package com.tkisor.nekojs.api.recipe.definition;

import com.google.gson.JsonElement;
import com.tkisor.nekojs.api.recipe.RecipeExecutionFactory;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

/** Canonical runtime and tooling metadata; unique IDs retain the native NekoJS policy. */
public record RecipeTypeDefinition(
        String namespace,
        String name,
        String type,
        String idPrefix,
        List<List<String>> constructors,
        Map<String, RecipeFieldDefinition> fields,
        List<String> unique,
        Map<String, String> fieldAliases,
        Map<String, Map<String, JsonElement>> functions,
        Map<List<String>, Map<String, JsonElement>> constructorOverrides,
        String unsupportedReason,
        Map<String, String> fieldFunctions,
        RecipeExecutionFactory executionFactory
) {
    public RecipeTypeDefinition(String namespace, String name, String type, String idPrefix,
                                List<List<String>> constructors, Map<String, RecipeFieldDefinition> fields,
                                List<String> unique) {
        this(namespace, name, type, idPrefix, constructors, fields, unique, Map.of(), Map.of(), Map.of(), null);
    }

    /** Retains the original advanced constructor's automatic field and alias setters. */
    public RecipeTypeDefinition(String namespace, String name, String type, String idPrefix,
                                List<List<String>> constructors, Map<String, RecipeFieldDefinition> fields,
                                List<String> unique, Map<String, String> fieldAliases,
                                Map<String, Map<String, JsonElement>> functions,
                                Map<List<String>, Map<String, JsonElement>> constructorOverrides,
                                String unsupportedReason) {
        this(namespace, name, type, idPrefix, constructors, fields, unique, fieldAliases, functions,
                constructorOverrides, unsupportedReason, null, null);
    }

    public RecipeTypeDefinition {
        constructors = constructors.stream().map(List::copyOf).toList();
        fields = Collections.unmodifiableMap(new LinkedHashMap<>(fields));
        unique = List.copyOf(unique);
        fieldAliases = Collections.unmodifiableMap(new LinkedHashMap<>(fieldAliases));
        for (var alias : fieldAliases.entrySet()) {
            if (fields.containsKey(alias.getKey()) || !fields.containsKey(alias.getValue())) {
                throw invalid(namespace, name, "Invalid field alias: " + alias.getKey());
            }
        }
        // A null map retains native automatic setters; an empty map deliberately exposes none.
        if (fieldFunctions != null) {
            var copiedFieldFunctions = new LinkedHashMap<String, String>();
            for (var function : fieldFunctions.entrySet()) {
                if (function.getKey() == null || function.getKey().isBlank()
                        || !fields.containsKey(function.getValue()) || functions.containsKey(function.getKey())) {
                    throw invalid(namespace, name, "Invalid field function: " + function.getKey());
                }
                copiedFieldFunctions.put(function.getKey(), function.getValue());
            }
            fieldFunctions = Collections.unmodifiableMap(copiedFieldFunctions);
        }
        var copiedFunctions = new LinkedHashMap<String, Map<String, JsonElement>>();
        for (var function : functions.entrySet()) {
            if (fields.containsKey(function.getKey()) || fieldAliases.containsKey(function.getKey())) {
                throw invalid(namespace, name, "Function conflicts with a field or alias: " + function.getKey());
            }
            validateConstants(namespace, name, fields, function.getValue());
            copiedFunctions.put(function.getKey(), copyConstants(function.getValue()));
        }
        functions = Collections.unmodifiableMap(copiedFunctions);
        var copiedOverrides = new LinkedHashMap<List<String>, Map<String, JsonElement>>();
        for (var override : constructorOverrides.entrySet()) {
            if (!constructors.contains(override.getKey())) {
                throw invalid(namespace, name, "Overrides reference an unknown constructor: " + override.getKey());
            }
            validateConstants(namespace, name, fields, override.getValue());
            copiedOverrides.put(List.copyOf(override.getKey()), copyConstants(override.getValue()));
        }
        constructorOverrides = Collections.unmodifiableMap(copiedOverrides);
        boolean prepared = fieldFunctions != null || executionFactory != null
                || !fieldAliases.isEmpty() || !functions.isEmpty() || !constructorOverrides.isEmpty()
                || fields.values().stream().anyMatch(field -> field.valueCodec() != null || !field.writeDefault());
        if (prepared) {
            var arities = new HashSet<Integer>();
            for (var constructor : constructors) {
                if (!arities.add(constructor.size()) || new HashSet<>(constructor).size() != constructor.size()
                        || !fields.keySet().containsAll(constructor)) {
                    throw invalid(namespace, name, "Ambiguous or invalid constructor: " + constructor);
                }
            }
        }
    }

    /** Retains a known profile as a terminal rejection instead of enabling raw JSON fallback. */
    public static RecipeTypeDefinition unsupported(String namespace, String name, String type, String reason) {
        if (reason == null || reason.isBlank()) {
            throw invalid(namespace, name, "An unsupported profile requires a reason");
        }
        return new RecipeTypeDefinition(namespace, name, type, null, List.of(), Map.of(), List.of(),
                Map.of(), Map.of(), Map.of(), reason);
    }

    private static void validateConstants(String namespace, String name, Map<String, RecipeFieldDefinition> fields,
                                          Map<String, JsonElement> values) {
        for (var value : values.entrySet()) {
            if (!fields.containsKey(value.getKey()) || value.getValue() == null) {
                throw invalid(namespace, name, "Invalid encoded constant for field: " + value.getKey());
            }
        }
    }

    private static Map<String, JsonElement> copyConstants(Map<String, JsonElement> values) {
        var copy = new LinkedHashMap<String, JsonElement>();
        values.forEach((key, value) -> copy.put(key, value.deepCopy()));
        return Collections.unmodifiableMap(copy);
    }

    private static IllegalArgumentException invalid(String namespace, String name, String detail) {
        return new IllegalArgumentException("[NEKO-5210] Invalid recipe definition " + namespace + ":" + name + ": " + detail);
    }

    /** Advanced profiles opt into detached JSON preparation and the encoded-field host hook. */
    public boolean requiresPreparedFields() {
        return fieldFunctions != null || executionFactory != null
                || !fieldAliases.isEmpty() || !functions.isEmpty() || !constructorOverrides.isEmpty()
                || fields.values().stream().anyMatch(field -> field.valueCodec() != null || !field.writeDefault());
    }

    public String key() {
        return namespace + ":" + name;
    }

    public String prefix() {
        return idPrefix == null || idPrefix.isBlank() ? namespace + "_" + name : idPrefix;
    }
}
