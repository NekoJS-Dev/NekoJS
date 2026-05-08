package com.tkisor.nekojs.mixin;

import com.google.common.collect.Multimap;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.mojang.serialization.JsonOps;
import com.tkisor.nekojs.NekoJSCommon;
import com.tkisor.nekojs.bindings.event.ServerEvents;
import com.tkisor.nekojs.core.error.NekoErrorTracker;
import com.tkisor.nekojs.core.error.NekoErrorUIHelper;
import com.tkisor.nekojs.mixin_api.IRecipeManagerExtension;
import com.tkisor.nekojs.script.ScriptType;
import com.tkisor.nekojs.wrapper.event.server.RecipeEventJS;
import net.minecraft.core.HolderLookup;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.item.crafting.*;
import net.neoforged.neoforge.common.conditions.WithConditions;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.*;

@Mixin(RecipeManager.class)
public abstract class RecipeManagerMixin implements IRecipeManagerExtension {

    @Final @Shadow private HolderLookup.Provider registries;
    @Shadow private Map<ResourceLocation, RecipeHolder<?>> byName;
    @Shadow private Multimap<RecipeType<?>, RecipeHolder<?>> byType;

    @Shadow public abstract void replaceRecipes(Iterable<RecipeHolder<?>> p_44025_);

    // 【修改点 1】: 直接删除了 @Shadow protected abstract RegistryOps<JsonElement> makeConditionalOps();
    // 避免 IDEA 和 Mixin 编译报错。

    @Unique
    private final Map<ResourceLocation, JsonElement> nekojs$rawJsons = new HashMap<>();

    @Inject(method = "apply(Ljava/util/Map;Lnet/minecraft/server/packs/resources/ResourceManager;Lnet/minecraft/util/profiling/ProfilerFiller;)V", at = @At("HEAD"))
    private void nekojs$cacheRawJsons(Map<ResourceLocation, JsonElement> p_44037_, ResourceManager p_44038_, ProfilerFiller p_44039_, CallbackInfo ci) {
        this.nekojs$rawJsons.clear();
        this.nekojs$rawJsons.putAll(p_44037_);
    }

    @Unique
    @Override
    public void nekojs$applyScripts() {
        int beforeCount = this.nekojs$rawJsons.size();

        RegistryOps<JsonElement> registryOps = this.registries.createSerializationContext(JsonOps.INSTANCE);

        this.nekojs$rawJsons.entrySet().removeIf(entry -> {
            ResourceLocation id = entry.getKey();
            if (id.getPath().startsWith("_")) return true;

            try {
                Optional<WithConditions<Recipe<?>>> decoded = Recipe.CONDITIONAL_CODEC.parse(registryOps, entry.getValue())
                        .getOrThrow(JsonParseException::new);
                return decoded.isEmpty();
            } catch (Exception e) {
                return true;
            }
        });

        int afterCount = this.nekojs$rawJsons.size();
        NekoJSCommon.LOGGER.debug("[NekoJS] Filtered out {} recipes that did not meet conditions", beforeCount - afterCount);

        RecipeEventJS eventJS = new RecipeEventJS(this.nekojs$rawJsons, this.registries);
        try {
            ServerEvents.RECIPES.post(eventJS);
        } catch (Exception e) {
            NekoJSCommon.LOGGER.error("[NekoJS] Recipe script execution crashed:", e);
        }

        List<RecipeHolder<?>> newHolders = new ArrayList<>();
        for (Map.Entry<ResourceLocation, JsonElement> entry : eventJS.getFinalJsons().entrySet()) {
            try {
                Recipe<?> recipe = Recipe.CODEC.parse(this.registries.createSerializationContext(JsonOps.INSTANCE), entry.getValue())
                        .getOrThrow(JsonParseException::new);
                newHolders.add(new RecipeHolder<>(entry.getKey(), recipe));
            } catch (Exception e) {
                NekoJSCommon.LOGGER.debug("[NekoJS] Invalid recipe {}: {}", entry.getKey(), e.getMessage());
            }
        }

        this.replaceRecipes(newHolders);
        this.nekojs$rawJsons.clear();

        ScriptType.SERVER.logger().debug("[NekoJS] Script execution completed, total recipes: {}", this.byName.size());

        if (ServerLifecycleHooks.getCurrentServer() != null) {
            List<ServerPlayer> players = ServerLifecycleHooks.getCurrentServer().getPlayerList().getPlayers();
            players.forEach(player -> {
                if (player.hasPermissions(2)) {
                    if (!NekoErrorTracker.hasErrors()) {
                        player.sendSystemMessage(NekoErrorUIHelper.getSuccessComponent());
                    } else {
                        player.sendSystemMessage(NekoErrorUIHelper.getErrorComponent());
                    }
                }
            });
        }
    }
}