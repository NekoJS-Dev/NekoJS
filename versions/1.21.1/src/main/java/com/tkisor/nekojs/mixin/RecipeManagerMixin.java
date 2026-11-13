// 1.21.1 implementation, paired with the same-named 26.x file under the version tree src/. Its
// content is that file as evaluated at this node. Keep this file in sync when the 26.x side changes.
// guard-exempt(20): recipe mixin injection points differ a lot between the two eras, so guarding
// each site is clearer than splitting the file again (ADR-0008).
package com.tkisor.nekojs.mixin;

import com.tkisor.nekojs.script.ScriptTypeEnv;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.mojang.serialization.JsonOps;
import com.tkisor.nekojs.NekoJS;
import com.tkisor.nekojs.api.event.ScriptErrorReporter;
import com.tkisor.nekojs.api.recipe.definition.RecipeTypeDefinitionStorage;
import com.tkisor.nekojs.bindings.event.ServerEvents;
import com.tkisor.nekojs.core.error.NekoErrorUIHelper;
import com.tkisor.nekojs.api.plugin.NekoRuntimeAccess;
import com.tkisor.nekojs.api.recipe.IRecipeManagerExtension;
import com.tkisor.nekojs.api.ScriptType;
import com.tkisor.nekojs.wrapper.event.server.RecipeEventJS;
import graal.graalvm.polyglot.PolyglotException;
import net.minecraft.core.HolderLookup;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.profiling.ProfilerFiller;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import java.util.*;
import com.google.common.collect.Multimap;
import net.minecraft.resources.RegistryOps;
import net.minecraft.world.item.crafting.*;
import net.neoforged.neoforge.common.conditions.WithConditions;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(RecipeManager.class)
public abstract class RecipeManagerMixin implements IRecipeManagerExtension {

    @Final @Shadow private HolderLookup.Provider registries;
    @Shadow private Map<ResourceLocation, RecipeHolder<?>> byName;
    @Shadow private Multimap<RecipeType<?>, RecipeHolder<?>> byType;

    @Shadow public abstract void replaceRecipes(Iterable<RecipeHolder<?>> p_44025_);

    @Unique
    private final Map<ResourceLocation, JsonElement> nekojs$rawJsons = new HashMap<>();

    // Permanently caches the raw recipe JSON that prepare() reads, so nekojs$applyScripts() can run
    // again on every /nekojs reload server (hot reload).
    // rawJsons is the working set of one applyScripts call: the condition filter mutates it and it is
    // cleared at the end. baseJsons is only rebuilt as a whole in prepare().
    @Unique
    private final Map<ResourceLocation, JsonElement> nekojs$baseJsons = new HashMap<>();
    @Unique
    private final Object nekojs$replayLock = new Object();
    @Unique
    private long nekojs$completedReplays;
    @Unique
    private long nekojs$failedReplays;

    @Unique
    @Override
    public ReplayObservation nekojs$observeScriptReplays() {
        synchronized (this.nekojs$replayLock) {
            long completed = this.nekojs$completedReplays;
            long failed = this.nekojs$failedReplays;
            return () -> {
                synchronized (this.nekojs$replayLock) {
                    return this.nekojs$completedReplays > completed && this.nekojs$failedReplays == failed;
                }
            };
        }
    }

    @Unique
    @Override
    public boolean nekojs$scriptReplaysSucceededSinceCreation() {
        synchronized (this.nekojs$replayLock) {
            return this.nekojs$completedReplays > 0 && this.nekojs$failedReplays == 0;
        }
    }

    @Unique
    private void nekojs$recordScriptReplay(boolean successful) {
        synchronized (this.nekojs$replayLock) {
            this.nekojs$completedReplays++;
            if (!successful) this.nekojs$failedReplays++;
        }
    }


    @Inject(method = "apply(Ljava/util/Map;Lnet/minecraft/server/packs/resources/ResourceManager;Lnet/minecraft/util/profiling/ProfilerFiller;)V", at = @At("HEAD"))
    private void nekojs$cacheRawJsons(Map<ResourceLocation, JsonElement> p_44037_, ResourceManager p_44038_, ProfilerFiller p_44039_, CallbackInfo ci) {
        this.nekojs$rawJsons.clear();
        this.nekojs$rawJsons.putAll(p_44037_);
        // Keeps the permanent cache in step: on hot reload, nekojs$applyScripts() rebuilds the working set from baseJsons.
        this.nekojs$baseJsons.clear();
        this.nekojs$baseJsons.putAll(p_44037_);
    }

    @Unique
    @Override
    public void nekojs$applyScripts() {
        nekojs$applyScriptsChecked();
    }

    @Unique
    @Override
    public boolean nekojs$applyScriptsChecked() {
        boolean[] successful = {true};
        boolean replaySucceeded = false;
        try {
            // Rebuilds the working set from the permanent cache so this method can run again on every
            // /nekojs reload server.
            // rawJsons is mutated by the condition filter below and cleared at the end, so each entry
            // starts from a fresh copy of baseJsons.
            this.nekojs$rawJsons.clear();
            this.nekojs$rawJsons.putAll(this.nekojs$baseJsons);
            int beforeCount = this.nekojs$rawJsons.size();

            // Equivalent to: ! ICondition.conditionsMatched(JsonOps.INSTANCE, entry.getValue())
            // Written this way only to avoid creating ConditionalOps over and over.
            RegistryOps<JsonElement> registryOps = this.registries.createSerializationContext(JsonOps.INSTANCE);

            this.nekojs$rawJsons.entrySet().removeIf(entry -> {
                ResourceLocation id = entry.getKey();
                if (id.getPath().startsWith("_")) return true;

                try {
                    Optional<WithConditions<Recipe<?>>> decoded = Recipe.CONDITIONAL_CODEC.parse(registryOps, entry.getValue())
                            .getOrThrow(JsonParseException::new);
                    return decoded.isEmpty();
                } catch (Exception e) {
                    successful[0] = false;
                    return true;
                }
            });

            int afterCount = this.nekojs$rawJsons.size();
            NekoJS.LOGGER.debug("Filtered out {} recipes that did not meet conditions", beforeCount - afterCount);

            RecipeEventJS eventJS = new RecipeEventJS(this.nekojs$rawJsons, this.registries, RecipeTypeDefinitionStorage.current());
            try {
                // Ingredient actions (damage/keep/replaceIngredient) are pure script state: the whole table is
                // cleared before the scripts run again and rebuilt by the RecipeJsonBuilder action calls made
                // during this run, so actions from deleted scripts vanish with them.
                NekoRuntimeAccess.get().beforeRecipeLoading(eventJS);
                if (!ServerEvents.RECIPES.postChecked(eventJS)) successful[0] = false;
                eventJS.prepareRecipesForDecode();
            } catch (PolyglotException e) {
                successful[0] = false;
                // Errors are reported through the ScriptErrorReporter facade, the static reporting side of the
                // root-owned ErrorTracker installed by the shared assembly function. A mixin static context
                // cannot have the root injected, so it must not read the static root directly.
                ScriptErrorReporter.recordEventError(ScriptType.SERVER, e);
            } catch (Exception e) {
                successful[0] = false;
                com.tkisor.nekojs.script.ScriptTypeEnv.logger(ScriptType.SERVER).error("Recipe script execution crashed", e);
            }

            List<RecipeHolder<?>> newHolders = new ArrayList<>();
            // Keep registry lookup reuse within this decode pass and after the modification hooks.
            RegistryOps<JsonElement> recipeOps = null;
            for (Map.Entry<ResourceLocation, JsonElement> entry : eventJS.getFinalJsons().entrySet()) {
                try {
                    if (recipeOps == null) {
                        recipeOps = this.registries.createSerializationContext(JsonOps.INSTANCE);
                    }
                    Recipe<?> recipe = Recipe.CODEC.parse(recipeOps, entry.getValue())
                            .getOrThrow(JsonParseException::new);
                    newHolders.add(new RecipeHolder<>(entry.getKey(), recipe));
                } catch (Exception e) {
                    successful[0] = false;
                    if (entry.getValue().isJsonObject()) {
                        NekoJS.LOGGER.error("{}", eventJS.formatRecipeError("Invalid recipe after script processing", entry.getKey(), entry.getValue().getAsJsonObject(), e));
                    } else {
                        NekoJS.LOGGER.error("Invalid recipe after script processing (id={}): {}", entry.getKey(), e.getMessage());
                    }
                }
            }

            // Replaces the recipe table in one go after the whole batch decodes (a single reference swap, no
            // half update). afterRecipes (the script event and the plugin hook) fires only after that
            // commit; running it before codec decoding would let consumers observe the old, uncommitted
            // table. Changes made in the afterRecipes stage are not part of this commit.
            this.replaceRecipes(newHolders);
            try {
                if (!ServerEvents.AFTER_RECIPES.postChecked(eventJS)) successful[0] = false;
                NekoRuntimeAccess.get().afterRecipes(eventJS);
            } catch (PolyglotException e) {
                successful[0] = false;
                ScriptErrorReporter.recordEventError(ScriptType.SERVER, e);
            } catch (Exception e) {
                successful[0] = false;
                com.tkisor.nekojs.script.ScriptTypeEnv.logger(ScriptType.SERVER).error("afterRecipes lifecycle phase crashed", e);
            }

            com.tkisor.nekojs.script.ScriptTypeEnv.logger(ScriptType.SERVER).debug("Script execution completed, total recipes: {}", this.byName.size());
            if (ServerLifecycleHooks.getCurrentServer() != null) {
                List<ServerPlayer> players = ServerLifecycleHooks.getCurrentServer().getPlayerList().getPlayers();
                players.forEach(player -> {
                    if (player.hasPermissions(2)) {
                        if (!ScriptErrorReporter.hasErrors()) {
                            player.sendSystemMessage(NekoErrorUIHelper.getSuccessComponent());
                        } else {
                            player.sendSystemMessage(NekoErrorUIHelper.getErrorComponent(ScriptErrorReporter.errorCount()));
                        }
                    }
                });
            }
            replaySucceeded = successful[0];
            return replaySucceeded;
        } finally {
            this.nekojs$rawJsons.clear();
            nekojs$recordScriptReplay(replaySucceeded);
        }
    }
}
