package com.tkisor.nekojs.api.recipe.definition;

/**
 * Holds three layers of recipe type definitions, merged by {@link #current()}.
 *
 * <pre>
 *   auto-discovered (MinecraftRegistry scan)  ← lowest priority
 *   plugin overrides (registerRecipeSchemas)   ← medium priority
 *   data-driven JSON (data pack definitions)    ← highest priority
 * </pre>
 */
public final class RecipeTypeDefinitionStorage {
    private static volatile RecipeTypeDefinitionRegistry dataDriven = RecipeTypeDefinitionRegistry.EMPTY;
    private static volatile RecipeTypeDefinitionRegistry autoDiscovered = RecipeTypeDefinitionRegistry.EMPTY;
    private static volatile RecipeTypeDefinitionRegistry pluginOverrides = RecipeTypeDefinitionRegistry.EMPTY;

    private RecipeTypeDefinitionStorage() {}

    /** Merged registry in priority order. */
    public static RecipeTypeDefinitionRegistry current() {
        return autoDiscovered.merge(pluginOverrides).merge(dataDriven);
    }

    /** Set data-driven definitions (from data pack JSON files). Called on server reload. */
    public static void replace(RecipeTypeDefinitionRegistry registry) {
        dataDriven = registry == null ? RecipeTypeDefinitionRegistry.EMPTY : registry;
    }

    public static void setAutoDiscovered(RecipeTypeDefinitionRegistry registry) {
        autoDiscovered = registry == null ? RecipeTypeDefinitionRegistry.EMPTY : registry;
    }

    public static void setPluginOverrides(RecipeTypeDefinitionRegistry registry) {
        pluginOverrides = registry == null ? RecipeTypeDefinitionRegistry.EMPTY : registry;
    }
}
