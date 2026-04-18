package com.tkisor.nekojs.mixin;

import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import com.tkisor.nekojs.NekoJS;
import com.tkisor.nekojs.bindings.event.ServerEvents;
import com.tkisor.nekojs.core.error.NekoErrorTracker;
import com.tkisor.nekojs.mixin_api.IRecipeManagerExtension;
import com.tkisor.nekojs.script.ScriptType;
import com.tkisor.nekojs.wrapper.event.server.RecipeEventJS;
import net.minecraft.commands.Commands;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.Unit;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeMap;
import net.neoforged.neoforge.common.conditions.ConditionalOps;
import net.neoforged.neoforge.common.conditions.ICondition;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.io.Reader;
import java.util.*;
import java.util.List;

@Mixin(RecipeManager.class)
public abstract class RecipeManagerMixin implements IRecipeManagerExtension {

    @Final @Shadow private HolderLookup.Provider registries;
    @Shadow private RecipeMap recipes;

    @Unique
    private final Map<Identifier, JsonElement> nekojs$rawJsons = new HashMap<>();

    @Inject(method = "prepare(Lnet/minecraft/server/packs/resources/ResourceManager;Lnet/minecraft/util/profiling/ProfilerFiller;)Lnet/minecraft/world/item/crafting/RecipeMap;", at = @At("HEAD"))
    private void nekojs$cacheRawJsons(ResourceManager manager, ProfilerFiller profiler, CallbackInfoReturnable<RecipeMap> cir) {
        nekojs$rawJsons.clear();
        FileToIdConverter converter = FileToIdConverter.registry(Registries.RECIPE);
        for (Map.Entry<Identifier, Resource> entry : converter.listMatchingResources(manager).entrySet()) {
            Identifier id = converter.fileToId(entry.getKey());
            try (Reader reader = entry.getValue().openAsReader()) {
                nekojs$rawJsons.put(id, JsonParser.parseReader(reader));
            } catch (Exception e) {
            }
        }
    }

    @Unique
    @Override
    public void nekojs$applyScripts() {
        int beforeCount = this.nekojs$rawJsons.size();

        // 相当于: ! ICondition.conditionsMatched(JsonOps.INSTANCE, entry.getValue())
        // 这么写只是为了避免重复创建 ConditionalOps
        var conditionalCodec = ConditionalOps.createConditionalCodec(Unit.CODEC);
        this.nekojs$rawJsons.entrySet().removeIf(entry -> ICondition.getWithConditionalCodec(conditionalCodec, JsonOps.INSTANCE, entry.getValue()).isEmpty());

        int afterCount = this.nekojs$rawJsons.size();
        NekoJS.LOGGER.debug("[NekoJS] Filtered out {} recipes that did not meet conditions", beforeCount - afterCount);

        RecipeEventJS eventJS = new RecipeEventJS(this.nekojs$rawJsons, this.registries);
        try {
            ServerEvents.RECIPES.post(eventJS);
        } catch (Exception e) {
            NekoJS.LOGGER.error("[NekoJS] Recipe script execution crashed:", e);
        }

        List<RecipeHolder<?>> newHolders = new ArrayList<>();
        for (Map.Entry<Identifier, JsonElement> entry : eventJS.getFinalJsons().entrySet()) {
            try {
                Recipe<?> recipe = Recipe.CODEC.parse(this.registries.createSerializationContext(JsonOps.INSTANCE), entry.getValue()).getOrThrow(JsonParseException::new);
                newHolders.add(new RecipeHolder<>(ResourceKey.create(Registries.RECIPE, entry.getKey()), recipe));
            } catch (Exception e) {
                NekoJS.LOGGER.debug("[NekoJS] Invalid recipe {}: {}", entry.getKey(), e.getMessage());
            }
        }

        this.recipes = RecipeMap.create(newHolders);
        this.nekojs$rawJsons.clear();

        ScriptType.SERVER.logger().debug("[NekoJS] Script execution completed, total recipes: {}", this.recipes.values().size());
        List<ServerPlayer> players = null;
        if (ServerLifecycleHooks.getCurrentServer() != null) {
            players = ServerLifecycleHooks.getCurrentServer().getPlayerList().getPlayers();
            players.forEach(player -> {
                if (Commands.LEVEL_GAMEMASTERS.check(player.permissions())) {
                    if (!NekoErrorTracker.hasErrors()) {
                        player.sendSystemMessage(NekoErrorTracker.getSuccessComponent());
                    } else {
                        player.sendSystemMessage(NekoErrorTracker.getErrorComponent());
                    }
                }
            });
        }
    }
}