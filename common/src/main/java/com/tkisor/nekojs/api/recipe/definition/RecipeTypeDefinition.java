package com.tkisor.nekojs.api.recipe.definition;

import java.util.List;
import java.util.Map;

public record RecipeTypeDefinition(
        String namespace,
        String name,
        String type,
        String idPrefix,
        List<List<String>> constructors,
        Map<String, RecipeFieldDefinition> fields,
        List<String> unique
) {
    public String key() {
        return namespace + ":" + name;
    }

    public String prefix() {
        return idPrefix == null || idPrefix.isBlank() ? namespace + "_" + name : idPrefix;
    }
}
