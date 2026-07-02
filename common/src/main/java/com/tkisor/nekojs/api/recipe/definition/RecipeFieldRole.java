package com.tkisor.nekojs.api.recipe.definition;

/**
 * Role of a recipe field: whether it represents a recipe input, output, or neither.
 *
 * <p>Used by schema-aware {@code replaceInput}/{@code replaceOutput} to locate the right JSON
 * paths per recipe type (instead of relying solely on hardcoded key names), and surfaced by the
 * scanner so plugins and data-driven schemas can declare it explicitly. Orthogonal to
 * {@link RecipeFieldKind}: an {@code ITEM_STACK} field can be either an output (smelting result)
 * or, for some mods, a single-item input.
 */
public enum RecipeFieldRole {
    INPUT,
    OUTPUT,
    OTHER
}
