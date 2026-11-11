package com.tkisor.nekujs.recipe.schema;

import com.tkisor.nekojs.api.recipe.definition.RecipeTypeDefinition;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Every input ID has a native definition, including terminal unsupported profiles. */
public record Compilation(Map<String, RecipeTypeDefinition> definitions, List<SchemaRejection> rejections) {
    public Compilation {
        definitions = Collections.unmodifiableMap(new LinkedHashMap<>(definitions));
        rejections = List.copyOf(rejections);
    }
}
