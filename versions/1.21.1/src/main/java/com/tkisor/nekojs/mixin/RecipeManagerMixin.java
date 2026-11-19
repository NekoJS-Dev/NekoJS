// 1.21.1 implementation, paired with the same-named 26.x file under the version tree src/. Its
// content is that file as evaluated at this node. Keep this file in sync when the 26.x side changes.
// guard-exempt(20): recipe mixin injection points differ a lot between the two eras, so guarding
// each site is clearer than splitting the file again (ADR-0008).
package com.tkisor.nekojs.mixin;

import com.tkisor.nekojs.script.ScriptTypeEnv;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMultimap;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.mojang.serialization.JsonOps;
import com.tkisor.nekojs.NekoJS;
import com.tkisor.nekojs.api.data.NekoId;
import com.tkisor.nekojs.api.event.ScriptErrorReporter;
import com.tkisor.nekojs.api.recipe.definition.RecipeTypeDefinitionStorage;
import com.tkisor.nekojs.bindings.event.ServerEvents;
import com.tkisor.nekojs.core.error.NekoErrorUIHelper;
import com.tkisor.nekojs.core.stage.gate.InlineRecipeStageRegistry;
import com.tkisor.nekojs.api.plugin.NekoRuntimeAccess;
import com.tkisor.nekojs.api.recipe.RecipeLifecycleContext;
import com.tkisor.nekojs.core.plugin.NekoPluginRuntime;
import com.tkisor.nekojs.api.recipe.IRecipeManagerExtension;
import com.tkisor.nekojs.api.ScriptType;
import com.tkisor.nekojs.stage.CandidateRecipeReplay;
import com.tkisor.nekojs.stage.StageDomainOwner;
import com.tkisor.nekojs.wrapper.event.server.RecipeEventJS;
import graal.graalvm.polyglot.PolyglotException;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import com.tkisor.nekojs.core.stage.gate.StageRegistryAccess.RecipeDescriptor;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
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
import java.util.function.Consumer;
import com.google.common.collect.Multimap;
import net.minecraft.resources.RegistryOps;
import net.minecraft.world.item.crafting.*;
import net.neoforged.neoforge.common.conditions.WithConditions;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Mixin into {@link RecipeManager} that caches raw recipe JSON and applies script recipe modifications on reload. */
@Mixin(RecipeManager.class)
public abstract class RecipeManagerMixin implements IRecipeManagerExtension, CandidateRecipeReplay {

    @Final @Shadow private HolderLookup.Provider registries;
    @Shadow private Map<ResourceLocation, RecipeHolder<?>> byName;
    @Shadow private Multimap<RecipeType<?>, RecipeHolder<?>> byType;
    @Shadow private boolean hasErrors;

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
    public Prepared nekojs$prepareScriptReplay(Consumer<RecipeEventJS> dispatch) {
        InlineRecipeStageRegistry draft = new InlineRecipeStageRegistry();
        return InlineRecipeStageRegistry.withDraft(draft, () -> {
            Map<ResourceLocation, JsonElement> rawJsons = new HashMap<>(this.nekojs$baseJsons);
            boolean[] successful = {true};
            nekojs$filterRecipes(rawJsons, this.registries.createSerializationContext(JsonOps.INSTANCE), successful, true);
            RecipeEventJS eventJS = new RecipeEventJS(rawJsons, this.registries, RecipeTypeDefinitionStorage.current());
            nekojs$runStrictBeforeRecipeHooks(eventJS);
            dispatch.accept(eventJS);
            eventJS.prepareRecipesForDecode();

            Map<NekoId, JsonElement> acceptedJsons = new LinkedHashMap<>();
            List<RecipeHolder<?>> holders = nekojs$decodeRecipes(eventJS, successful, acceptedJsons);
            AcceptedTable acceptedTable = AcceptedTable.capture(acceptedJsons);
            Runnable publish = nekojs$prepareRecipePublication(holders);
            return new Prepared(draft.snapshot(), nekojs$describeRecipes(holders), acceptedTable, publish, server -> {
                boolean replaySucceeded = false;
                try {
                    nekojs$syncCommittedRecipes(server);
                    replaySucceeded = InlineRecipeStageRegistry.withDraft(draft,
                            () -> nekojs$runAfterRecipes(eventJS, true));
                } finally {
                    nekojs$recordScriptReplay(replaySucceeded);
                }
            });
        });
    }

    @Unique
    @Override
    public void nekojs$restoreAcceptedTable(AcceptedTable table) {
        Map<NekoId, JsonElement> acceptedJsons = table.copyRecipes();
        RegistryOps<JsonElement> recipeOps = this.registries.createSerializationContext(JsonOps.INSTANCE);
        List<RecipeHolder<?>> holders = new ArrayList<>(acceptedJsons.size());
        for (var entry : acceptedJsons.entrySet()) {
            NekoId id = entry.getKey();
            try {
                ResourceLocation nativeId = ResourceLocation.fromNamespaceAndPath(id.namespace(), id.path());
                holders.add(nekojs$decodeRecipe(recipeOps, nativeId, entry.getValue()));
            } catch (Exception e) {
                throw new JsonParseException("[NEKO-1008] Failed to restore accepted recipe (id=" + id + ")", e);
            }
        }
        nekojs$prepareRecipePublication(holders).run();
    }

    @Unique
    private Runnable nekojs$prepareRecipePublication(List<RecipeHolder<?>> holders) {
        var byType = ImmutableMultimap.<RecipeType<?>, RecipeHolder<?>>builder();
        var byName = ImmutableMap.<ResourceLocation, RecipeHolder<?>>builder();
        for (RecipeHolder<?> holder : holders) {
            byType.put(holder.value().getType(), holder);
            byName.put(holder.id(), holder);
        }
        var preparedByType = byType.build();
        var preparedByName = byName.build();
        return () -> {
            this.byType = preparedByType;
            this.byName = preparedByName;
            this.hasErrors = false;
        };
    }

    @Unique
    @Override
    public List<RecipeDescriptor> nekojs$recipeDescriptors() {
        return nekojs$describeRecipes(this.byName.values());
    }

    @Unique
    private void nekojs$syncCommittedRecipes(MinecraftServer server) {
        if (server == null) return;
        RecipeManager manager = (RecipeManager) (Object) this;
        if (server.getPlayerList() == null || server.getPlayerList().getPlayers().isEmpty()) return;
        var packet = new net.minecraft.network.protocol.game.ClientboundUpdateRecipesPacket(manager.getRecipes());
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            player.connection.send(packet);
            player.getRecipeBook().sendInitialRecipeBook(player);
        }
    }

    @Unique
    private List<RecipeDescriptor> nekojs$describeRecipes(Collection<RecipeHolder<?>> holders) {
        return holders.stream().map(holder -> {
            Recipe<?> recipe = holder.value();
            var id = holder.id();
            var type = BuiltInRegistries.RECIPE_TYPE.getKey(recipe.getType());
            var result = RecipeEventJS.getRecipeOutputId(recipe, this.registries);
            return new RecipeDescriptor(NekoId.resource(id.toString()),
                    type == null ? null : NekoId.resource(type.toString()),
                    result == null ? null : NekoId.resource(result));
        }).toList();
    }

    @Unique
    @Override
    public boolean nekojs$applyScriptsChecked() {
        InlineRecipeStageRegistry draft = new InlineRecipeStageRegistry();
        return InlineRecipeStageRegistry.withDraft(draft, () -> nekojs$applyDraftChecked(draft));
    }

    @Unique
    private boolean nekojs$applyDraftChecked(InlineRecipeStageRegistry draft) {
        Map<NekoId, InlineRecipeStageRegistry.InlineEntry> previousInlineStages =
                InlineRecipeStageRegistry.SHARED.snapshot();
        boolean recipesCommitted = false;
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

            nekojs$filterRecipes(this.nekojs$rawJsons, registryOps, successful, false);

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

            // Keep registry lookup reuse within this decode pass and after the modification hooks.
            List<RecipeHolder<?>> newHolders = nekojs$decodeRecipes(eventJS, successful, null);

            // Replaces the recipe table in one go after the whole batch decodes (a single reference swap, no
            // half update). afterRecipes (the script event and the plugin hook) fires only after that
            // commit; running it before codec decoding would let consumers observe the old, uncommitted
            // table. Changes made in the afterRecipes stage are not part of this commit.
            if (!successful[0]) return false;
            this.replaceRecipes(newHolders);
            InlineRecipeStageRegistry.SHARED.replaceSnapshot(draft.snapshot(), false);
            recipesCommitted = true;
            if (!nekojs$runAfterRecipes(eventJS, false)) successful[0] = false;
            nekojs$refreshInlineRecipeStages();

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
        } catch (Throwable t) {
            // Roll back inline recipe stages to the pre-replay snapshot when an error aborts before the new
            // recipe map is committed, keeping active recipe gates aligned with the active RecipeManager.
            if (!recipesCommitted) {
                nekojs$restoreInlineRecipeStages(previousInlineStages);
            }
            nekojs$refreshInlineRecipeStages();
            throw t;
        } finally {
            this.nekojs$rawJsons.clear();
            nekojs$recordScriptReplay(replaySucceeded);
        }
    }

    @Unique
    private void nekojs$filterRecipes(Map<ResourceLocation, JsonElement> rawJsons,
                                      RegistryOps<JsonElement> registryOps, boolean[] successful, boolean strict) {
        rawJsons.entrySet().removeIf(entry -> {
            ResourceLocation id = entry.getKey();
            if (id.getPath().startsWith("_")) return true;

            try {
                Optional<WithConditions<Recipe<?>>> decoded = Recipe.CONDITIONAL_CODEC.parse(registryOps, entry.getValue())
                        .getOrThrow(JsonParseException::new);
                return decoded.isEmpty();
            } catch (Exception e) {
                if (strict) throw new JsonParseException("Failed to decode cached recipe " + id, e);
                successful[0] = false;
                return true;
            }
        });
    }

    @Unique
    private RecipeHolder<?> nekojs$decodeRecipe(RegistryOps<JsonElement> recipeOps, ResourceLocation id, JsonElement json) {
        Recipe<?> recipe = Recipe.CODEC.parse(recipeOps, json).getOrThrow(JsonParseException::new);
        return new RecipeHolder<>(id, recipe);
    }

    @Unique
    private List<RecipeHolder<?>> nekojs$decodeRecipes(RecipeEventJS eventJS, boolean[] successful,
                                                      Map<NekoId, JsonElement> acceptedJsons) {
        boolean strict = acceptedJsons != null;
        Set<NekoId> committedRecipeIds = strict ? acceptedJsons.keySet() : new HashSet<>();
        InlineRecipeStageRegistry stages = InlineRecipeStageRegistry.current();
        List<RecipeHolder<?>> newHolders = new ArrayList<>();
        RegistryOps<JsonElement> recipeOps = null;
        for (Map.Entry<ResourceLocation, JsonElement> entry : eventJS.getFinalJsons().entrySet()) {
            try {
                if (recipeOps == null) {
                    recipeOps = this.registries.createSerializationContext(JsonOps.INSTANCE);
                }
                JsonElement json = entry.getValue();
                newHolders.add(nekojs$decodeRecipe(recipeOps, entry.getKey(), strict ? json.deepCopy() : json));
                NekoId id = NekoId.resource(entry.getKey().getNamespace(), entry.getKey().getPath());
                if (strict) acceptedJsons.put(id, json);
                else committedRecipeIds.add(id);
            } catch (Exception e) {
                String message = entry.getValue() != null && entry.getValue().isJsonObject()
                        ? eventJS.formatRecipeError("Invalid recipe after script processing", entry.getKey(), entry.getValue().getAsJsonObject(), e)
                        : "Invalid recipe after script processing (id=" + entry.getKey() + "): " + e.getMessage();
                if (strict) throw new JsonParseException(message, e);
                successful[0] = false;
                stages.remove(
                        NekoId.resource(entry.getKey().getNamespace(), entry.getKey().getPath()));
                NekoJS.LOGGER.error("{}", message);
            }
        }
        nekojs$pruneUncommittedInlineStages(stages, committedRecipeIds);
        return newHolders;
    }

    @Unique
    private void nekojs$runStrictBeforeRecipeHooks(RecipeEventJS eventJS) {
        var runtime = NekoRuntimeAccess.get();
        if (runtime instanceof NekoPluginRuntime pluginRuntime) {
            for (Consumer<RecipeLifecycleContext> hook : pluginRuntime.beforeRecipeLoadingHooks()) {
                hook.accept(eventJS);
            }
        } else {
            runtime.beforeRecipeLoading(eventJS);
        }
    }

    @Unique
    private boolean nekojs$runAfterRecipeHooks(RecipeEventJS eventJS, boolean strict) {
        var runtime = NekoRuntimeAccess.get();
        if (!strict || !(runtime instanceof NekoPluginRuntime pluginRuntime)) {
            runtime.afterRecipes(eventJS);
            return true;
        }
        boolean successful = true;
        for (Consumer<RecipeLifecycleContext> hook : pluginRuntime.afterRecipesHooks()) {
            try {
                hook.accept(eventJS);
            } catch (Exception e) {
                successful = false;
                com.tkisor.nekojs.script.ScriptTypeEnv.logger(ScriptType.SERVER).error("Recipe lifecycle hook failed", e);
            }
        }
        return successful;
    }

    @Unique
    private boolean nekojs$runAfterRecipes(RecipeEventJS eventJS, boolean strict) {
        boolean successful = true;
        try {
            successful = ServerEvents.AFTER_RECIPES.postChecked(eventJS);
            successful &= nekojs$runAfterRecipeHooks(eventJS, strict);
        } catch (PolyglotException e) {
            successful = false;
            ScriptErrorReporter.recordEventError(ScriptType.SERVER, e);
        } catch (Exception e) {
            successful = false;
            com.tkisor.nekojs.script.ScriptTypeEnv.logger(ScriptType.SERVER).error("afterRecipes lifecycle phase crashed", e);
        }
        return successful;
    }

    @Unique
    private MinecraftServer nekojs$currentServer() {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        return server != null && server.getPlayerList() != null ? server : null;
    }

    @Unique
    private void nekojs$pruneUncommittedInlineStages(InlineRecipeStageRegistry stages, Set<NekoId> committedRecipeIds) {
        Map<NekoId, InlineRecipeStageRegistry.InlineEntry> current = stages.snapshot();
        if (current.isEmpty()) {
            return;
        }
        stages.runBatch(() -> {
            for (NekoId recipeId : current.keySet()) {
                if (!committedRecipeIds.contains(recipeId)) {
                    stages.remove(recipeId);
                }
            }
        });
    }

    @Unique
    private void nekojs$restoreInlineRecipeStages(
            Map<NekoId, InlineRecipeStageRegistry.InlineEntry> previousEntries) {
        InlineRecipeStageRegistry.SHARED.runBatch(() -> {
            InlineRecipeStageRegistry.SHARED.clear();
            for (InlineRecipeStageRegistry.InlineEntry entry : previousEntries.values()) {
                InlineRecipeStageRegistry.SHARED.put(entry.recipeId(), entry.stage(), entry.scope());
            }
        });
    }

    @Unique
    private void nekojs$refreshInlineRecipeStages() {
        try {
            StageDomainOwner stageOwner = StageDomainOwner.current();
            if (stageOwner != null) {
                stageOwner.refreshInlineRecipesAndSync(nekojs$currentServer(), nekojs$recipeDescriptors());
            }
        } catch (Exception e) {
            NekoJS.LOGGER.error("Failed to refresh inline recipe stage snapshot after recipe reload", e);
        }
    }
}
