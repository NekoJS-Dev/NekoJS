package com.tkisor.nekojs.api.catalog;

import com.tkisor.nekojs.script.ScriptTypePredicate;

import java.util.List;

public record ManualDeclarationCatalogEntry(
        String id,
        ScriptTypePredicate scriptType,
        String declaration,
        String description,
        List<String> examples,
        int priority
) {
    public ManualDeclarationCatalogEntry {
        examples = List.copyOf(examples == null ? List.of() : examples);
    }

    public static ManualDeclarationCatalogEntry of(String id, String declaration, String description, List<String> examples) {
        return new ManualDeclarationCatalogEntry(id, ScriptTypePredicate.any(), declaration, description, examples, 0);
    }

    public static ManualDeclarationCatalogEntry of(ScriptTypePredicate scriptType, String id, String declaration, String description, List<String> examples) {
        return new ManualDeclarationCatalogEntry(id, scriptType, declaration, description, examples, 0);
    }
}
