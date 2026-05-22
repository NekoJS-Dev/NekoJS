package com.tkisor.nekojs.api.recipe.definition;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class RecipeTypeDefinitionJsonLoader {
    private RecipeTypeDefinitionJsonLoader() {}

    public static RecipeTypeDefinition parse(String namespace, String name, JsonObject json) {
        String type = string(json, "type", namespace + ":" + name);
        String idPrefix = string(json, "id_prefix", namespace + "_" + name);
        Map<String, RecipeFieldDefinition> fields = fields(json.getAsJsonObject("fields"));
        List<List<String>> constructors = constructors(json.getAsJsonArray("constructors"), fields);
        return new RecipeTypeDefinition(namespace, name, type, idPrefix, constructors, fields);
    }

    private static Map<String, RecipeFieldDefinition> fields(JsonObject object) {
        if (object == null || object.size() == 0) {
            throw new IllegalArgumentException("Recipe type definition requires fields");
        }
        Map<String, RecipeFieldDefinition> fields = new LinkedHashMap<>();
        for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
            if (!entry.getValue().isJsonObject()) {
                throw new IllegalArgumentException("Recipe field definition must be an object: " + entry.getKey());
            }
            JsonObject field = entry.getValue().getAsJsonObject();
            String name = entry.getKey();
            String path = string(field, "path", name);
            RecipeFieldKind kind = RecipeFieldKind.parse(string(field, "kind", "json"));
            boolean array = bool(field, "array", false);
            boolean optional = bool(field, "optional", false);
            JsonElement defaultValue = field.has("default") ? field.get("default") : null;
            fields.put(name, new RecipeFieldDefinition(name, path, kind, array, optional, defaultValue));
        }
        return Collections.unmodifiableMap(fields);
    }

    private static List<List<String>> constructors(JsonArray array, Map<String, RecipeFieldDefinition> fields) {
        if (array == null || array.isEmpty()) {
            return List.of(List.copyOf(fields.keySet()));
        }
        List<List<String>> constructors = new ArrayList<>();
        for (JsonElement element : array) {
            if (!element.isJsonArray()) {
                throw new IllegalArgumentException("Recipe constructor must be an array of field names");
            }
            List<String> constructor = new ArrayList<>();
            for (JsonElement fieldName : element.getAsJsonArray()) {
                String name = fieldName.getAsString();
                if (!fields.containsKey(name)) {
                    throw new IllegalArgumentException("Recipe constructor references unknown field: " + name);
                }
                constructor.add(name);
            }
            constructors.add(List.copyOf(constructor));
        }
        return List.copyOf(constructors);
    }

    private static String string(JsonObject object, String key, String fallback) {
        return object != null && object.has(key) && object.get(key).isJsonPrimitive() ? object.get(key).getAsString() : fallback;
    }

    private static boolean bool(JsonObject object, String key, boolean fallback) {
        return object != null && object.has(key) && object.get(key).isJsonPrimitive() ? object.get(key).getAsBoolean() : fallback;
    }
}
