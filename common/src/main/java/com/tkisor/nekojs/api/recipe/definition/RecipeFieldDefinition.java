package com.tkisor.nekojs.api.recipe.definition;

import com.google.gson.JsonElement;

public record RecipeFieldDefinition(
        String name,
        String path,
        RecipeFieldKind kind,
        boolean array,
        boolean optional,
        JsonElement defaultValue,
        RecipeFieldRole role
) {
    public boolean required() {
        return !optional && defaultValue == null;
    }
}
