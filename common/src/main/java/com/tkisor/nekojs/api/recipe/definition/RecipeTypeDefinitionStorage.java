package com.tkisor.nekojs.api.recipe.definition;

public final class RecipeTypeDefinitionStorage {
    private static volatile RecipeTypeDefinitionRegistry current = RecipeTypeDefinitionRegistry.EMPTY;
    private static volatile RecipeTypeDefinitionRegistry autoDiscovered = RecipeTypeDefinitionRegistry.EMPTY;
    private static volatile RecipeTypeDefinitionRegistry pluginOverrides = RecipeTypeDefinitionRegistry.EMPTY;

    private RecipeTypeDefinitionStorage() {}

    /** Merged registry: auto-discovered → plugin overrides → data-driven JSON. */
    public static RecipeTypeDefinitionRegistry current() {
        return autoDiscovered.merge(pluginOverrides).merge(current);
    }

    public static void replace(RecipeTypeDefinitionRegistry registry) {
        current = registry == null ? RecipeTypeDefinitionRegistry.EMPTY : registry;
    }

    public static void setAutoDiscovered(RecipeTypeDefinitionRegistry registry) {
        autoDiscovered = registry == null ? RecipeTypeDefinitionRegistry.EMPTY : registry;
    }

    public static void setPluginOverrides(RecipeTypeDefinitionRegistry registry) {
        pluginOverrides = registry == null ? RecipeTypeDefinitionRegistry.EMPTY : registry;
    }
}
