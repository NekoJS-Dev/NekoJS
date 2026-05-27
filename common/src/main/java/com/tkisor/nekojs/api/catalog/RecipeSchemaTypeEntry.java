package com.tkisor.nekojs.api.catalog;

import com.tkisor.nekojs.api.recipe.definition.RecipeTypeDefinition;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Describes a single recipe type from the schema layer (auto-discovered / data-driven / plugin override).
 */
public record RecipeSchemaTypeEntry(
        String namespace,
        String type,
        String idPrefix,
        List<SchemaField> fields,
        List<List<String>> constructors
) {
    public record SchemaField(String name, String kind, boolean required, boolean array, String path) {}

    public static RecipeSchemaTypeEntry from(RecipeTypeDefinition def) {
        int colon = def.type().indexOf(':');
        String ns = colon < 0 ? "" : def.type().substring(0, colon);
        String name = colon < 0 ? def.type() : def.type().substring(colon + 1);

        List<SchemaField> fields = new ArrayList<>();
        for (var f : def.fields().values()) {
            fields.add(new SchemaField(
                    f.name(), f.kind().name(), f.required(), f.array(), f.path()));
        }
        List<List<String>> ctors = new ArrayList<>();
        for (var c : def.constructors()) {
            ctors.add(List.copyOf(c));
        }
        return new RecipeSchemaTypeEntry(ns, name, def.prefix(), List.copyOf(fields), List.copyOf(ctors));
    }
}
