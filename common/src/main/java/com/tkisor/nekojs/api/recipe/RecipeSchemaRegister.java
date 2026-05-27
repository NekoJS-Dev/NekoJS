package com.tkisor.nekojs.api.recipe;

import com.tkisor.nekojs.api.recipe.definition.RecipeTypeDefinition;

/**
 * Plugin hook for overriding or defining recipe type schemas.
 * Schemas registered here take priority over auto-discovered ones.
 */
@FunctionalInterface
public interface RecipeSchemaRegister {
    /** Override or define a schema for a specific recipe type. */
    void register(String namespace, String type, RecipeTypeDefinition schema);
}
