package com.tkisor.nekojs.resource;

import com.google.gson.JsonParser;
import com.tkisor.nekojs.NekoJS;
import com.tkisor.nekojs.api.recipe.definition.RecipeTypeDefinitionJsonLoader;
import com.tkisor.nekojs.api.recipe.definition.RecipeTypeDefinitionRegistry;
import java.io.Reader;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.server.packs.resources.ResourceManager;

/** Reads data-driven recipe type definitions from the selected server packs. */
public final class RecipeTypeDefinitionResources {
    private static final FileToIdConverter FILES = FileToIdConverter.json("nekojs/recipe_types");

    private RecipeTypeDefinitionResources() {}

    public static RecipeTypeDefinitionRegistry load(ResourceManager manager) {
        var definitions = RecipeTypeDefinitionRegistry.builder();
        for (var entry : FILES.listMatchingResources(manager).entrySet()) {
            var resourceId = entry.getKey();
            try (Reader reader = entry.getValue().openAsReader()) {
                var id = FILES.fileToId(resourceId);
                var json = JsonParser.parseReader(reader).getAsJsonObject();
                definitions.add(RecipeTypeDefinitionJsonLoader.parse(id.getNamespace(), id.getPath(), json));
            } catch (Exception failure) {
                NekoJS.LOGGER.error("[NEKO-5004] Skipped unreadable or invalid recipe type definition {}", resourceId, failure);
            }
        }
        return definitions.build();
    }

    /** Lets a loader's recipe manager publish its prepared definitions at resource binding. */
    public interface Prepared {
        void nekojs$publishDataPackSchemas();
    }
}
