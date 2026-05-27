package com.tkisor.nekojs.api.recipe.definition;

public final class RecipeTypeDefinitionStorage {
    private static volatile RecipeTypeDefinitionRegistry current = RecipeTypeDefinitionRegistry.EMPTY;

    private RecipeTypeDefinitionStorage() {}

    public static RecipeTypeDefinitionRegistry current() {
        return current;
    }

    public static void replace(RecipeTypeDefinitionRegistry registry) {
        current = registry == null ? RecipeTypeDefinitionRegistry.EMPTY : registry;
    }
}
