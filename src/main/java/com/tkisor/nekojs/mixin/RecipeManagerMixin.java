//? if >=26 {
package com.tkisor.nekojs.mixin;

import com.tkisor.nekojs.script.ScriptTypeEnv;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import com.tkisor.nekojs.NekoJS;
//? if fabric {
/*import com.tkisor.nekojs.fabric.event.FabricServerEventBindings;
*///?}
import com.tkisor.nekojs.api.data.NekoId;
import com.tkisor.nekojs.api.event.ScriptErrorReporter;
import com.tkisor.nekojs.api.recipe.definition.RecipeTypeDefinitionStorage;
import com.tkisor.nekojs.bindings.event.ServerEvents;
import com.tkisor.nekojs.core.error.NekoErrorUIHelper;
import com.tkisor.nekojs.core.plugin.NekoPluginRuntime;
import com.tkisor.nekojs.core.stage.gate.InlineRecipeStageRegistry;
import com.tkisor.nekojs.api.plugin.NekoRuntimeAccess;
import com.tkisor.nekojs.api.recipe.IRecipeManagerExtension;
import com.tkisor.nekojs.api.recipe.IngredientActionRegistry;
import com.tkisor.nekojs.api.ScriptType;
import com.tkisor.nekojs.stage.CandidateRecipeReplay;
import com.tkisor.nekojs.stage.StageDomainOwner;
import com.tkisor.nekojs.wrapper.event.server.RecipeEventJS;
import graal.graalvm.polyglot.PolyglotException;
import net.minecraft.commands.Commands;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import com.tkisor.nekojs.core.stage.gate.StageRegistryAccess.RecipeDescriptor;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.RegistryOps;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
//? if neoforge {
import net.minecraft.util.Unit;
//?}
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeMap;
//? if neoforge {
import net.neoforged.neoforge.common.conditions.ConditionalOps;
import net.neoforged.neoforge.common.conditions.ICondition;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
//?}
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
import java.util.function.Consumer;

/** Mixin hooking {@code RecipeManager} reload to apply NekoJS recipe additions, removals, and edits. */
@Mixin(RecipeManager.class)
public abstract class RecipeManagerMixin implements IRecipeManagerExtension, CandidateRecipeReplay {

    @Final @Shadow private HolderLookup.Provider registries;
    @Shadow private RecipeMap recipes;

    @Unique
    private final Map<Identifier, JsonElement> nekojs$rawJsons = new HashMap<>();

    // Permanently caches the raw recipe JSON that prepare() read, so nekojs$applyScripts() can be called
    // again and again by /nekojs reload server (hot reload). rawJsons is the working set of one
    // applyScripts call: condition filtering mutates it and the end of the call clears it. baseJsons
    // is only rebuilt as a whole during prepare.
    @Unique
    private final Map<Identifier, JsonElement> nekojs$baseJsons = new HashMap<>();
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

    @Unique
    private boolean nekojs$sourceJsonsValid = true;


    @Inject(method = "prepare(Lnet/minecraft/server/packs/resources/ResourceManager;Lnet/minecraft/util/profiling/ProfilerFiller;)Lnet/minecraft/world/item/crafting/RecipeMap;", at = @At("HEAD"))
    private void nekojs$cacheRawJsons(ResourceManager manager, ProfilerFiller profiler, CallbackInfoReturnable<RecipeMap> cir) {
        this.nekojs$sourceJsonsValid = true;
        nekojs$rawJsons.clear();
        FileToIdConverter converter = FileToIdConverter.registry(Registries.RECIPE);
        for (Map.Entry<Identifier, Resource> entry : converter.listMatchingResources(manager).entrySet()) {
            Identifier id = converter.fileToId(entry.getKey());
            try (Reader reader = entry.getValue().openAsReader()) {
                nekojs$rawJsons.put(id, JsonParser.parseReader(reader));
            } catch (Exception e) {
                this.nekojs$sourceJsonsValid = false;
                // Bad recipe JSON is not dropped silently: log a warning with the id so modpack authors can find
                // the broken entry.
                NekoJS.LOGGER.warn("Failed to parse recipe JSON '{}'; it will be skipped", id, e);
            }
        }
        // Sync the permanent cache; on a hot reload nekojs$applyScripts() rebuilds its working set from
        // baseJsons.
        nekojs$baseJsons.clear();
        nekojs$baseJsons.putAll(nekojs$rawJsons);
    }

    @Unique
    @Override
    public void nekojs$applyScripts() {
        nekojs$applyScriptsChecked();
    }

    @Unique
    @Override
    public List<RecipeDescriptor> nekojs$recipeDescriptors() {
        return nekojs$describeRecipes(this.recipes.values());
    }

    @Unique
    private void nekojs$syncCommittedRecipes(MinecraftServer server) {
        if (server == null) return;
        RecipeManager manager = (RecipeManager) (Object) this;
        manager.finalizeRecipeLoading(server.getWorldData().enabledFeatures());
        if (server.getPlayerList() == null || server.getPlayerList().getPlayers().isEmpty()) return;
        var packet = new net.minecraft.network.protocol.game.ClientboundUpdateRecipesPacket(
                manager.getSynchronizedItemProperties(), manager.getSynchronizedStonecutterRecipes());
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            player.connection.send(packet);
            player.getRecipeBook().sendInitialRecipeBook(player);
        }
    }

    @Unique
    private List<RecipeDescriptor> nekojs$describeRecipes(Collection<RecipeHolder<?>> holders) {
        return holders.stream().map(holder -> {
            Recipe<?> recipe = holder.value();
            var id = holder.id().identifier();
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
        Map<Identifier, List<IngredientActionRegistry.Action>> actions = new HashMap<>();
        return InlineRecipeStageRegistry.withDraft(draft, () -> IngredientActionRegistry.withDraft(actions,
                () -> nekojs$applyDraftChecked(draft, actions)));
    }

    @Unique
    private boolean nekojs$applyDraftChecked(InlineRecipeStageRegistry draft,
                                             Map<Identifier, List<IngredientActionRegistry.Action>> actions) {
        Map<NekoId, InlineRecipeStageRegistry.InlineEntry> previousInlineStages =
                InlineRecipeStageRegistry.SHARED.snapshot();
        boolean recipesCommitted = false;
        boolean[] successful = {this.nekojs$sourceJsonsValid};
        boolean replaySucceeded = false;
        try {
            // Rebuild the working set from the permanent cache so the method can run again on every
            // /nekojs reload server. Condition filtering mutates rawJsons and the end of the call clears it,
            // so the copy from baseJsons is remade on each entry.
            this.nekojs$rawJsons.clear();
            this.nekojs$rawJsons.putAll(this.nekojs$baseJsons);
            nekojs$filterRecipeConditions(this.nekojs$rawJsons);

            RecipeEventJS eventJS = new RecipeEventJS(this.nekojs$rawJsons, this.registries, RecipeTypeDefinitionStorage.current());
            try {
                // Ingredient actions (damage/keep/replaceIngredient) are pure script state: the whole table is
                // cleared before the scripts rerun and rebuilt by the RecipeJsonBuilder calls made while the
                // scripts run, so actions of deleted scripts disappear with them.
                IngredientActionRegistry.clear();
                NekoRuntimeAccess.get().beforeRecipeLoading(eventJS);
                if (!ServerEvents.RECIPES.postChecked(eventJS)) successful[0] = false;
                eventJS.prepareRecipesForDecode();
            } catch (PolyglotException e) {
                successful[0] = false;
                // Errors are reported through the ScriptErrorReporter facade, the static reporting face of the
                // root-owned ErrorTracker that the shared assembly function installs; static mixin context cannot
                // inject the root.
                ScriptErrorReporter.recordEventError(ScriptType.SERVER, e);
            } catch (Exception e) {
                successful[0] = false;
                com.tkisor.nekojs.script.ScriptTypeEnv.logger(ScriptType.SERVER).error("Recipe script execution crashed", e);
            }

            // Keep registry lookup reuse within this decode pass and after the modification hooks.
            RecipeMap newRecipes = nekojs$decodeRecipes(eventJS, draft, null, successful);

            // After the whole batch has parsed, the RecipeMap is replaced in one go: a single reference commit,
            // never a half update. afterRecipes (script event and plugin hooks) fires only after that commit,
            // so consumers see the committed recipe table, and changes made during afterRecipes do not enter
            // this commit.
            if (!successful[0]) return false;
            this.recipes = newRecipes;
            IngredientActionRegistry.publishDraft(actions);
            InlineRecipeStageRegistry.SHARED.replaceSnapshot(draft.snapshot(), false);
            recipesCommitted = true;
            if (!nekojs$runAfterRecipes(eventJS, false)) successful[0] = false;
            nekojs$refreshInlineRecipeStages();

            com.tkisor.nekojs.script.ScriptTypeEnv.logger(ScriptType.SERVER).debug("Script execution completed, total recipes: {}", this.recipes.values().size());
//? if neoforge {
            List<ServerPlayer> players = null;
            if (ServerLifecycleHooks.getCurrentServer() != null) {
                players = ServerLifecycleHooks.getCurrentServer().getPlayerList().getPlayers();
//?} else {
/*        List<ServerPlayer> players = null;
            net.minecraft.server.MinecraftServer nekojs$server = FabricServerEventBindings.currentServer();
            if (nekojs$server != null) {
                players = nekojs$server.getPlayerList().getPlayers();
*///?}
                players.forEach(player -> {
                    if (Commands.LEVEL_GAMEMASTERS.check(player.permissions())) {
                        if (!ScriptErrorReporter.hasErrors()) {
                            player.sendSystemMessage(NekoErrorUIHelper.getSuccessComponent());
                        } else {
                            player.sendSystemMessage(NekoErrorUIHelper.getErrorComponent(
                                    ScriptErrorReporter.errorCount()));
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
    @Override
    public CandidateRecipeReplay.Prepared nekojs$prepareScriptReplay(Consumer<RecipeEventJS> dispatch) {
        if (!this.nekojs$sourceJsonsValid) {
            throw new IllegalStateException("[NEKO-1008] Recipe replay requires valid datapack recipe JSON.");
        }
        InlineRecipeStageRegistry inlineStages = new InlineRecipeStageRegistry();
        Map<Identifier, List<IngredientActionRegistry.Action>> ingredientActions = new HashMap<>();
        return InlineRecipeStageRegistry.withDraft(inlineStages, () ->
                IngredientActionRegistry.withDraft(ingredientActions, () -> {
                    Map<Identifier, JsonElement> rawJsons = new HashMap<>(this.nekojs$baseJsons);
                    nekojs$filterRecipeConditions(rawJsons);
                    RecipeEventJS eventJS = new RecipeEventJS(rawJsons, this.registries, RecipeTypeDefinitionStorage.current());
                    var pluginRuntime = NekoRuntimeAccess.get();
                    if (pluginRuntime instanceof NekoPluginRuntime runtime) {
                        for (var hook : runtime.beforeRecipeLoadingHooks()) hook.accept(eventJS);
                    } else {
                        pluginRuntime.beforeRecipeLoading(eventJS);
                    }
                    dispatch.accept(eventJS);
                    eventJS.prepareRecipesForDecode();
                    Map<NekoId, JsonElement> acceptedJsons = new LinkedHashMap<>();
                    RecipeMap preparedRecipes = nekojs$decodeRecipes(eventJS, inlineStages, acceptedJsons, new boolean[]{true});
                    var acceptedTable = CandidateRecipeReplay.AcceptedTable.capture(acceptedJsons);
                    return new CandidateRecipeReplay.Prepared(inlineStages.snapshot(), nekojs$describeRecipes(preparedRecipes.values()), acceptedTable, () -> {
                        IngredientActionRegistry.publishDraft(ingredientActions);
                        this.recipes = preparedRecipes;
                    }, server -> {
                        boolean successful = false;
                        try {
                            nekojs$syncCommittedRecipes(server);
                            successful = InlineRecipeStageRegistry.withDraft(inlineStages, () ->
                                    IngredientActionRegistry.withDraft(new HashMap<>(), () -> nekojs$runAfterRecipes(eventJS, true)));
                        } finally {
                            nekojs$recordScriptReplay(successful);
                        }
                    });
                }));
    }

    @Unique
    @Override
    public void nekojs$restoreAcceptedTable(CandidateRecipeReplay.AcceptedTable table) {
        Map<NekoId, JsonElement> acceptedJsons = table.copyRecipes();
        RegistryOps<JsonElement> recipeOps = this.registries.createSerializationContext(JsonOps.INSTANCE);
        List<RecipeHolder<?>> restoredHolders = new ArrayList<>(acceptedJsons.size());
        for (var entry : acceptedJsons.entrySet()) {
            NekoId id = entry.getKey();
            try {
                Identifier nativeId = Identifier.fromNamespaceAndPath(id.namespace(), id.path());
                restoredHolders.add(nekojs$decodeRecipe(recipeOps, nativeId, entry.getValue()));
            } catch (Exception e) {
                throw new JsonParseException("[NEKO-1008] Failed to restore accepted recipe (id=" + id + ")", e);
            }
        }
        this.recipes = RecipeMap.create(restoredHolders);
    }

    @Unique
    private void nekojs$filterRecipeConditions(Map<Identifier, JsonElement> jsons) {
//? if neoforge {
        int beforeCount = jsons.size();
// Same as: ! ICondition.conditionsMatched(JsonOps.INSTANCE, entry.getValue())
// Written this way only to avoid creating the ConditionalOps repeatedly.
        var conditionalCodec = ConditionalOps.createConditionalCodec(Unit.CODEC);
        jsons.entrySet().removeIf(entry -> ICondition.getWithConditionalCodec(conditionalCodec, JsonOps.INSTANCE, entry.getValue()).isEmpty());

        int afterCount = jsons.size();
        NekoJS.LOGGER.debug("Filtered out {} recipes that did not meet conditions", beforeCount - afterCount);
//?}
    }

    @Unique
    private RecipeHolder<?> nekojs$decodeRecipe(RegistryOps<JsonElement> recipeOps, Identifier id, JsonElement json) {
        Recipe<?> recipe = Recipe.CODEC.parse(recipeOps, json).getOrThrow(JsonParseException::new);
        return new RecipeHolder<>(ResourceKey.create(Registries.RECIPE, id), recipe);
    }

    @Unique
    private RecipeMap nekojs$decodeRecipes(RecipeEventJS eventJS, InlineRecipeStageRegistry inlineStages,
                                          Map<NekoId, JsonElement> acceptedJsons, boolean[] successful) {
        boolean strict = acceptedJsons != null;
        Set<NekoId> committedRecipeIds = strict ? acceptedJsons.keySet() : new HashSet<>();
        List<RecipeHolder<?>> newHolders = new ArrayList<>();
        RegistryOps<JsonElement> recipeOps = null;
        for (Map.Entry<Identifier, JsonElement> entry : eventJS.getFinalJsons().entrySet()) {
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
                successful[0] = false;
                if (strict) {
                    String message = entry.getValue().isJsonObject()
                            ? eventJS.formatRecipeError("[NEKO-1008] Invalid recipe after script processing", entry.getKey(), entry.getValue().getAsJsonObject(), e)
                            : "[NEKO-1008] Invalid recipe after script processing (id=" + entry.getKey() + ")";
                    throw new JsonParseException(message, e);
                }
                inlineStages.remove(
                        NekoId.resource(entry.getKey().getNamespace(), entry.getKey().getPath()));
                if (entry.getValue().isJsonObject()) {
                    NekoJS.LOGGER.error("{}", eventJS.formatRecipeError("Invalid recipe after script processing", entry.getKey(), entry.getValue().getAsJsonObject(), e));
                } else {
                    NekoJS.LOGGER.error("Invalid recipe after script processing (id={}): {}", entry.getKey(), e.getMessage());
                }
            }
        }
        nekojs$pruneUncommittedInlineStages(inlineStages, committedRecipeIds);
        return RecipeMap.create(newHolders);
    }

    @Unique
    private boolean nekojs$runAfterRecipes(RecipeEventJS eventJS, boolean candidate) {
        boolean successful = true;
        try {
            if (!ServerEvents.AFTER_RECIPES.postChecked(eventJS)) successful = false;
            var pluginRuntime = NekoRuntimeAccess.get();
            if (candidate && pluginRuntime instanceof NekoPluginRuntime runtime) {
                for (var hook : runtime.afterRecipesHooks()) {
                    try {
                        hook.accept(eventJS);
                    } catch (Exception e) {
                        successful = false;
                        ScriptTypeEnv.logger(ScriptType.SERVER).error("[NEKO-1022] afterRecipes lifecycle hook failed", e);
                    }
                }
            } else {
                pluginRuntime.afterRecipes(eventJS);
            }
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
//? if neoforge {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
//?} else {
/*        MinecraftServer server = FabricServerEventBindings.currentServer();
*///?}
        return server != null && server.getPlayerList() != null ? server : null;
    }

    @Unique
    private void nekojs$pruneUncommittedInlineStages(InlineRecipeStageRegistry inlineStages, Set<NekoId> committedRecipeIds) {
        Map<NekoId, InlineRecipeStageRegistry.InlineEntry> current = inlineStages.snapshot();
        if (current.isEmpty()) {
            return;
        }
        inlineStages.runBatch(() -> {
            for (NekoId recipeId : current.keySet()) {
                if (!committedRecipeIds.contains(recipeId)) {
                    inlineStages.remove(recipeId);
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
//?}
