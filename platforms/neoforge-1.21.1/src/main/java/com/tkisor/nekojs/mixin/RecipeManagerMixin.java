package com.tkisor.nekojs.mixin;

import com.google.common.collect.Multimap;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.mojang.serialization.JsonOps;
import com.tkisor.nekojs.NekoJS;
import com.tkisor.nekojs.NekoJSMod;
import com.tkisor.nekojs.api.recipe.definition.RecipeTypeDefinitionStorage;
import com.tkisor.nekojs.bindings.event.ServerEvents;
import com.tkisor.nekojs.core.error.NekoErrorUIHelper;
import com.tkisor.nekojs.api.plugin.NekoRuntimeAccess;
import com.tkisor.nekojs.api.recipe.IRecipeManagerExtension;
import com.tkisor.nekojs.api.ScriptType;
import com.tkisor.nekojs.wrapper.event.server.RecipeEventJS;
import graal.graalvm.polyglot.PolyglotException;
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

    @Unique
    private final Map<ResourceLocation, JsonElement> nekojs$rawJsons = new HashMap<>();

    // 永久缓存 apply() 收到的原始配方 JSON，让 nekojs$applyScripts() 可被 /nekojs reload server 反复调用（热重载）。
    // rawJsons 是每次 applyScripts 的工作集（会被条件过滤 mutate、末尾 clear），baseJsons 只在 apply 时整体重建。
    @Unique
    private final Map<ResourceLocation, JsonElement> nekojs$baseJsons = new HashMap<>();

    @Inject(method = "apply(Ljava/util/Map;Lnet/minecraft/server/packs/resources/ResourceManager;Lnet/minecraft/util/profiling/ProfilerFiller;)V", at = @At("HEAD"))
    private void nekojs$cacheRawJsons(Map<ResourceLocation, JsonElement> p_44037_, ResourceManager p_44038_, ProfilerFiller p_44039_, CallbackInfo ci) {
        this.nekojs$rawJsons.clear();
        this.nekojs$rawJsons.putAll(p_44037_);
        // 同步永久缓存：热重载时 nekojs$applyScripts() 从 baseJsons 重建工作集
        this.nekojs$baseJsons.clear();
        this.nekojs$baseJsons.putAll(p_44037_);
    }

    @Unique
    @Override
    public void nekojs$applyScripts() {
        // 从永久缓存重建工作集，使本方法可被 /nekojs reload server 反复调用。
        // rawJsons 在下方会被条件过滤 mutate、末尾 clear，故每次入口都从 baseJsons 重新拷贝。
        this.nekojs$rawJsons.clear();
        this.nekojs$rawJsons.putAll(this.nekojs$baseJsons);
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
        NekoJS.LOGGER.debug("Filtered out {} recipes that did not meet conditions", beforeCount - afterCount);

        RecipeEventJS eventJS = new RecipeEventJS(this.nekojs$rawJsons, this.registries, RecipeTypeDefinitionStorage.current());
        try {
            NekoRuntimeAccess.get().beforeRecipeLoading(eventJS);
            ServerEvents.RECIPES.post(eventJS);
            ServerEvents.AFTER_RECIPES.post(eventJS);
            NekoRuntimeAccess.get().afterRecipes(eventJS);
        } catch (PolyglotException e) {
            NekoJSMod.RUNTIME_ROOT.errorTracker().recordEventError(ScriptType.SERVER, e);
        } catch (Exception e) {
            ScriptType.SERVER.logger().error("Recipe script execution crashed", e);
        }

        List<RecipeHolder<?>> newHolders = new ArrayList<>();
        for (Map.Entry<ResourceLocation, JsonElement> entry : eventJS.getFinalJsons().entrySet()) {
            try {
                Recipe<?> recipe = Recipe.CODEC.parse(this.registries.createSerializationContext(JsonOps.INSTANCE), entry.getValue())
                        .getOrThrow(JsonParseException::new);
                newHolders.add(new RecipeHolder<>(entry.getKey(), recipe));
            } catch (Exception e) {
                if (entry.getValue().isJsonObject()) {
                    NekoJS.LOGGER.error("{}", eventJS.formatRecipeError("Invalid recipe after script processing", entry.getKey(), entry.getValue().getAsJsonObject(), e));
                } else {
                    NekoJS.LOGGER.error("Invalid recipe after script processing (id={}): {}", entry.getKey(), e.getMessage());
                }
            }
        }

        this.replaceRecipes(newHolders);
        this.nekojs$rawJsons.clear();

        ScriptType.SERVER.logger().debug("Script execution completed, total recipes: {}", this.byName.size());

        if (ServerLifecycleHooks.getCurrentServer() != null) {
            List<ServerPlayer> players = ServerLifecycleHooks.getCurrentServer().getPlayerList().getPlayers();
            players.forEach(player -> {
                if (player.hasPermissions(2)) {
                    if (!NekoJSMod.RUNTIME_ROOT.errorTracker().hasErrors()) {
                        player.sendSystemMessage(NekoErrorUIHelper.getSuccessComponent());
                    } else {
                        player.sendSystemMessage(NekoErrorUIHelper.getErrorComponent());
                    }
                }
            });
        }
    }
}