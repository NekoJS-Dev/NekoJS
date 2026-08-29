package com.tkisor.nekojs.api.recipe.definition;

/**
 * Holds four layers of recipe type definitions, merged by {@link #current()}.
 *
 * <pre>
 *   auto-discovered (MinecraftRegistry scan)  ← lowest priority
 *   plugin overrides (registerRecipeSchemas)   │
 *   data-driven JSON (data pack definitions)   │
 *   script-registered (event.registerSchema)   ← highest priority
 * </pre>
 */
public final class RecipeTypeDefinitionStorage {
    private static volatile RecipeTypeDefinitionRegistry dataDriven = RecipeTypeDefinitionRegistry.EMPTY;
    private static volatile RecipeTypeDefinitionRegistry autoDiscovered = RecipeTypeDefinitionRegistry.EMPTY;
    private static volatile RecipeTypeDefinitionRegistry pluginOverrides = RecipeTypeDefinitionRegistry.EMPTY;
    private static volatile RecipeTypeDefinitionRegistry scriptSchemas = RecipeTypeDefinitionRegistry.EMPTY;

    private RecipeTypeDefinitionStorage() {}

    /** Merged registry in priority order: script &gt; data &gt; plugin &gt; auto. */
    public static RecipeTypeDefinitionRegistry current() {
        return autoDiscovered.merge(pluginOverrides).merge(dataDriven).merge(scriptSchemas);
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

    /** Set script-registered definitions (event.registerSchema). Replaced wholesale per recipe event run. */
    public static void replaceScript(RecipeTypeDefinitionRegistry registry) {
        scriptSchemas = registry == null ? RecipeTypeDefinitionRegistry.EMPTY : registry;
    }
}
