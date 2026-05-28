package com.tkisor.nekojs.api.recipe.definition;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

public final class RecipeTypeDefinitionRegistry {
    public static final RecipeTypeDefinitionRegistry EMPTY = new RecipeTypeDefinitionRegistry(Map.of());

    private final Map<String, Map<String, RecipeTypeDefinition>> definitions;

    public RecipeTypeDefinitionRegistry(Map<String, Map<String, RecipeTypeDefinition>> definitions) {
        Map<String, Map<String, RecipeTypeDefinition>> copy = new LinkedHashMap<>();
        definitions.forEach((namespace, types) -> copy.put(namespace, Collections.unmodifiableMap(new LinkedHashMap<>(types))));
        this.definitions = Collections.unmodifiableMap(copy);
    }

    public static Builder builder() {
        return new Builder();
    }

    public RecipeTypeDefinitionRegistry merge(RecipeTypeDefinitionRegistry other) {
        Map<String, Map<String, RecipeTypeDefinition>> merged = new LinkedHashMap<>(definitions);
        for (var entry : other.definitions.entrySet()) {
            merged.computeIfAbsent(entry.getKey(), ignored -> new LinkedHashMap<>()).putAll(entry.getValue());
        }
        return new RecipeTypeDefinitionRegistry(merged);
    }

    public Set<String> namespaces() {
        return definitions.keySet();
    }

    public Set<String> types(String namespace) {
        Map<String, RecipeTypeDefinition> types = definitions.get(namespace);
        return types == null ? Set.of() : types.keySet();
    }

    public boolean hasNamespace(String namespace) {
        return definitions.containsKey(namespace);
    }

    public boolean hasType(String namespace, String type) {
        Map<String, RecipeTypeDefinition> types = definitions.get(namespace);
        return types != null && types.containsKey(type);
    }

    public RecipeTypeDefinition get(String namespace, String type) {
        Map<String, RecipeTypeDefinition> types = definitions.get(namespace);
        return types == null ? null : types.get(type);
    }

    public Map<String, Map<String, RecipeTypeDefinition>> asMap() {
        return definitions;
    }

    public static final class Builder {
        private final Map<String, Map<String, RecipeTypeDefinition>> definitions = new LinkedHashMap<>();

        public Builder add(RecipeTypeDefinition definition) {
            definitions.computeIfAbsent(definition.namespace(), ignored -> new LinkedHashMap<>()).put(definition.name(), definition);
            return this;
        }

        public RecipeTypeDefinitionRegistry build() {
            return new RecipeTypeDefinitionRegistry(definitions);
        }
    }
}
