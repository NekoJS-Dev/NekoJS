package com.tkisor.nekojs.api.recipe.definition;

import com.google.gson.JsonElement;

/**
 * A canonical field. Defaults are already encoded JSON; writeDefault controls only omission
 * of an implicit default, never an explicitly supplied value. A valueCodec owns the whole field.
 */
public record RecipeFieldDefinition(
        String name,
        String path,
        RecipeFieldKind kind,
        boolean array,
        boolean optional,
        JsonElement defaultValue,
        RecipeFieldRole role,
        RecipeFieldValueCodec valueCodec,
        boolean writeDefault
) {
    public RecipeFieldDefinition(String name, String path, RecipeFieldKind kind, boolean array,
                                 boolean optional, JsonElement defaultValue, RecipeFieldRole role) {
        this(name, path, kind, array, optional, defaultValue, role, null, true);
    }

    public boolean required() {
        return !optional && defaultValue == null;
    }
}
