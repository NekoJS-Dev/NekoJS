package com.tkisor.nekojs.stage;

import com.google.gson.JsonElement;
import com.tkisor.nekojs.api.data.NekoId;
import com.tkisor.nekojs.core.stage.gate.InlineRecipeStageRegistry;
import com.tkisor.nekojs.wrapper.event.server.RecipeEventJS;
import net.minecraft.server.MinecraftServer;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.tkisor.nekojs.core.stage.gate.StageRegistryAccess.RecipeDescriptor;
import java.util.function.Consumer;

public interface CandidateRecipeReplay {
    Prepared nekojs$prepareScriptReplay(Consumer<RecipeEventJS> dispatch);

    List<RecipeDescriptor> nekojs$recipeDescriptors();

    void nekojs$restoreAcceptedTable(AcceptedTable table);

    final class AcceptedTable {
        private final Map<NekoId, JsonElement> recipes;

        private AcceptedTable(Map<NekoId, JsonElement> recipes) {
            this.recipes = detached(recipes);
        }

        public static AcceptedTable capture(Map<NekoId, JsonElement> recipes) {
            return new AcceptedTable(recipes);
        }

        public Map<NekoId, JsonElement> copyRecipes() {
            return detached(recipes);
        }

        private static Map<NekoId, JsonElement> detached(Map<NekoId, JsonElement> source) {
            Map<NekoId, JsonElement> copy = new LinkedHashMap<>();
            source.forEach((id, json) -> copy.put(id, json.deepCopy()));
            return Collections.unmodifiableMap(copy);
        }
    }

    record Prepared(Map<NekoId, InlineRecipeStageRegistry.InlineEntry> stages, List<RecipeDescriptor> descriptors,
                    AcceptedTable table, Runnable publish, Consumer<MinecraftServer> afterCommit) {
        public Prepared {
            stages = Map.copyOf(stages);
            descriptors = List.copyOf(descriptors);
        }
    }
}
