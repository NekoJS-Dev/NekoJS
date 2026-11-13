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
import com.tkisor.nekojs.api.event.ScriptErrorReporter;
import com.tkisor.nekojs.api.recipe.definition.RecipeTypeDefinitionStorage;
import com.tkisor.nekojs.bindings.event.ServerEvents;
import com.tkisor.nekojs.core.error.NekoErrorUIHelper;
import com.tkisor.nekojs.api.plugin.NekoRuntimeAccess;
import com.tkisor.nekojs.api.recipe.IRecipeManagerExtension;
import com.tkisor.nekojs.api.ScriptType;
import com.tkisor.nekojs.wrapper.event.server.RecipeEventJS;
import graal.graalvm.polyglot.PolyglotException;
import net.minecraft.commands.Commands;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.RegistryOps;
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

@Mixin(RecipeManager.class)
public abstract class RecipeManagerMixin implements IRecipeManagerExtension {

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
    public boolean nekojs$applyScriptsChecked() {
        boolean[] successful = {this.nekojs$sourceJsonsValid};
        boolean replaySucceeded = false;
        try {
            // Rebuild the working set from the permanent cache so the method can run again on every
            // /nekojs reload server. Condition filtering mutates rawJsons and the end of the call clears it,
            // so the copy from baseJsons is remade on each entry.
            this.nekojs$rawJsons.clear();
            this.nekojs$rawJsons.putAll(this.nekojs$baseJsons);
            int beforeCount = this.nekojs$rawJsons.size();
//? if neoforge {
// Same as: ! ICondition.conditionsMatched(JsonOps.INSTANCE, entry.getValue())
// Written this way only to avoid creating the ConditionalOps repeatedly.
            var conditionalCodec = ConditionalOps.createConditionalCodec(Unit.CODEC);
            this.nekojs$rawJsons.entrySet().removeIf(entry -> ICondition.getWithConditionalCodec(conditionalCodec, JsonOps.INSTANCE, entry.getValue()).isEmpty());

            int afterCount = this.nekojs$rawJsons.size();
            NekoJS.LOGGER.debug("Filtered out {} recipes that did not meet conditions", beforeCount - afterCount);
//?}

            RecipeEventJS eventJS = new RecipeEventJS(this.nekojs$rawJsons, this.registries, RecipeTypeDefinitionStorage.current());
            try {
                // Ingredient actions (damage/keep/replaceIngredient) are pure script state: the whole table is
                // cleared before the scripts rerun and rebuilt by the RecipeJsonBuilder calls made while the
                // scripts run, so actions of deleted scripts disappear with them.
                com.tkisor.nekojs.api.recipe.IngredientActionRegistry.clear();
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

            List<RecipeHolder<?>> newHolders = new ArrayList<>();
            // Keep registry lookup reuse within this decode pass and after the modification hooks.
            RegistryOps<JsonElement> recipeOps = null;
            for (Map.Entry<Identifier, JsonElement> entry : eventJS.getFinalJsons().entrySet()) {
                try {
                    if (recipeOps == null) {
                        recipeOps = this.registries.createSerializationContext(JsonOps.INSTANCE);
                    }
                    Recipe<?> recipe = Recipe.CODEC.parse(recipeOps, entry.getValue()).getOrThrow(JsonParseException::new);
                    newHolders.add(new RecipeHolder<>(ResourceKey.create(Registries.RECIPE, entry.getKey()), recipe));
                } catch (Exception e) {
                    successful[0] = false;
                    if (entry.getValue().isJsonObject()) {
                        NekoJS.LOGGER.error("{}", eventJS.formatRecipeError("Invalid recipe after script processing", entry.getKey(), entry.getValue().getAsJsonObject(), e));
                    } else {
                        NekoJS.LOGGER.error("Invalid recipe after script processing (id={}): {}", entry.getKey(), e.getMessage());
                    }
                }
            }

            // After the whole batch has parsed, the RecipeMap is replaced in one go: a single reference commit,
            // never a half update. afterRecipes (script event and plugin hooks) fires only after that commit,
            // so consumers see the committed recipe table, and changes made during afterRecipes do not enter
            // this commit.
            this.recipes = RecipeMap.create(newHolders);
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
        } finally {
            this.nekojs$rawJsons.clear();
            nekojs$recordScriptReplay(replaySucceeded);
        }
    }
}
//?}
