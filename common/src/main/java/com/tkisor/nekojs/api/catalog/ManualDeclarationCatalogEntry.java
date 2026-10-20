package com.tkisor.nekojs.api.catalog;

import com.tkisor.nekojs.api.ScriptTypePredicate;

import java.util.List;
import java.util.Map;
import java.util.Objects;

public record ManualDeclarationCatalogEntry(
        String id,
        ScriptTypePredicate scriptType,
        String declaration,
        String description,
        List<String> examples,
        int priority,
        Map<String, TypeReference> references
) {
    public ManualDeclarationCatalogEntry {
        examples = List.copyOf(examples == null ? List.of() : examples);
        references = Map.copyOf(references == null ? Map.of() : references);
        for (String name : references.keySet()) {
            if (!name.matches("[A-Za-z_$][A-Za-z0-9_$]*")) {
                throw new IllegalArgumentException("[NEKO-4051] Manual declaration reference rejected: invalid alias '"
                        + name + "' in " + id);
            }
        }
    }

    /** Preserves the constructor used before explicit declaration dependencies were added. */
    public ManualDeclarationCatalogEntry(String id, ScriptTypePredicate scriptType, String declaration,
                                         String description, List<String> examples, int priority) {
        this(id, scriptType, declaration, description, examples, priority, Map.of());
    }

    /** Associates global type aliases with real host types or their registered input conversions. */
    public ManualDeclarationCatalogEntry withReferences(Map<String, TypeReference> references) {
        return new ManualDeclarationCatalogEntry(id, scriptType, declaration, description, examples, priority, references);
    }

    public record TypeReference(Class<?> targetType, boolean input) {
        public TypeReference { Objects.requireNonNull(targetType, "targetType"); }

        public static TypeReference host(Class<?> type) { return new TypeReference(type, false); }
        public static TypeReference input(Class<?> type) { return new TypeReference(type, true); }
    }

    public static ManualDeclarationCatalogEntry of(String id, String declaration, String description, List<String> examples) {
        return new ManualDeclarationCatalogEntry(id, ScriptTypePredicate.any(), declaration, description, examples, 0);
    }

    public static ManualDeclarationCatalogEntry of(ScriptTypePredicate scriptType, String id, String declaration, String description, List<String> examples) {
        return new ManualDeclarationCatalogEntry(id, scriptType, declaration, description, examples, 0);
    }
}
