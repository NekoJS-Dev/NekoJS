package com.tkisor.nekojs.mixin;

import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import com.tkisor.nekojs.NekoJS;
import com.tkisor.nekojs.bindings.event.ServerEvents;
import com.tkisor.nekojs.mixin_api.IRecipeManagerExtension;
import com.tkisor.nekojs.wrapper.event.server.RecipeEventJS;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeMap;
import net.neoforged.neoforge.common.conditions.ICondition;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.io.Reader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
        this.nekojs$rawJsons.entrySet().removeIf(entry -> !ICondition.conditionsMatched(JsonOps.INSTANCE, entry.getValue()));
        int afterCount = this.nekojs$rawJsons.size();
        NekoJS.LOGGER.info("[NekoJS] 经过底层的条件求值，剔除了 {} 个未满足前置的配方。", beforeCount - afterCount);

        RecipeEventJS eventJS = new RecipeEventJS(this.nekojs$rawJsons, this.registries);
        try {
            ServerEvents.RECIPES.post(eventJS);
        } catch (Exception e) {
            NekoJS.LOGGER.error("[NekoJS] 配方脚本执行崩溃: ", e);
        }

        List<RecipeHolder<?>> newHolders = new ArrayList<>();
        for (Map.Entry<Identifier, JsonElement> entry : eventJS.getFinalJsons().entrySet()) {
            try {
                Recipe<?> recipe = Recipe.CODEC.parse(this.registries.createSerializationContext(JsonOps.INSTANCE), entry.getValue()).getOrThrow(JsonParseException::new);
                newHolders.add(new RecipeHolder<>(ResourceKey.create(Registries.RECIPE, entry.getKey()), recipe));
            } catch (Exception e) {
                NekoJS.LOGGER.error("[NekoJS] 配方不合法 {}: {}", entry.getKey(), e.getMessage());
            }
        }

        this.recipes = RecipeMap.create(newHolders);
        this.nekojs$rawJsons.clear();

        NekoJS.LOGGER.info("[NekoJS] 脚本执行完毕，当前配方总数: {}", this.recipes.values().size());
    }
}