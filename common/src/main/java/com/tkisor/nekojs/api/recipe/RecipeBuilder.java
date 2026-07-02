package com.tkisor.nekojs.api.recipe;

/**
 * Minimal builder facet used internally by the common recipe namespace proxies
 * to apply schema-driven fields.
 *
 * <p>Platform builders ({@code RecipeJsonBuilder}) implement this and additionally expose
 * the full scripting API (id/group/input/output/...) to JS via host-object access; the proxies
 * only need {@link #setPath(String, RecipeJsonValue)}.
 */
public interface RecipeBuilder {
    RecipeBuilder setPath(String path, RecipeJsonValue value);
}
