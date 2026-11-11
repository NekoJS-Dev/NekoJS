package dev.latvian.mods.kubejs.recipe.schema;

import dev.latvian.mods.kubejs.recipe.KubeRecipe;
import net.minecraft.resources.ResourceLocation;
import java.util.Objects;
import java.util.function.Supplier;

public class RecipeFactoryRegistry {
    private final RecipeSchemaStorage storage;
    public RecipeFactoryRegistry(RecipeSchemaStorage storage) { this.storage = Objects.requireNonNull(storage); }
    public void register(KubeRecipeFactory factory) { storage.registerFactory(factory); }
    public void register(ResourceLocation id, Class<?> typeClass, Supplier<? extends KubeRecipe> factory) {
        register(new KubeRecipeFactory(id, typeClass, factory));
    }
}
