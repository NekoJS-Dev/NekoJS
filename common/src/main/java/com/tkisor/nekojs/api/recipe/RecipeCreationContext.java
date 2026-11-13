package com.tkisor.nekojs.api.recipe;

/**
 * Context for creating a recipe.
 *
 * <p>{@code scriptId} is the id of the script file that creates the recipe; it is {@code null}
 * when something else creates the recipe, for example a plugin. The platform captures it from
 * {@code ScriptContextRegistry} while the script callback runs. On failure it is reported
 * together with {@code api/type/prefix} so the script location can be found.
 */
public record RecipeCreationContext(String api, String type, String prefix, String scriptId) {
    public static RecipeCreationContext of(String api, String type, String prefix) {
        return new RecipeCreationContext(api, type, prefix, null);
    }

    public static RecipeCreationContext of(String api, String type, String prefix, String scriptId) {
        return new RecipeCreationContext(api, type, prefix, scriptId);
    }

    public RecipeCreationContext withScriptId(String scriptId) {
        return new RecipeCreationContext(api, type, prefix, scriptId);
    }

    public String describe(String id) {
        String base = "id=" + id + ", type=" + type + ", api=" + api + ", prefix=" + prefix;
        return scriptId == null ? base : base + ", script=" + scriptId;
    }
}
