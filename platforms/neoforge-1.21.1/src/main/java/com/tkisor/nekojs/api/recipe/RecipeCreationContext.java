package com.tkisor.nekojs.api.recipe;

public record RecipeCreationContext(String api, String type, String prefix) {
    public static RecipeCreationContext of(String api, String type, String prefix) {
        return new RecipeCreationContext(api, type, prefix);
    }

    public String describe(String id) {
        return "id=" + id + ", type=" + type + ", api=" + api + ", prefix=" + prefix;
    }
}
