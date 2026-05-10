package com.tkisor.nekojs.api.catalog;

import com.tkisor.nekojs.script.ScriptType;

import java.util.List;

public record ManualDeclarationCatalogEntry(
        String id,
        ScriptType scriptType,
        String declaration,
        String description,
        List<String> examples,
        int priority
) {
    public ManualDeclarationCatalogEntry {
        examples = List.copyOf(examples == null ? List.of() : examples);
    }

    public static ManualDeclarationCatalogEntry of(String id, String declaration, String description, List<String> examples) {
        return new ManualDeclarationCatalogEntry(id, ScriptType.COMMON, declaration, description, examples, 0);
    }

    public static ManualDeclarationCatalogEntry of(ScriptType scriptType, String id, String declaration, String description, List<String> examples) {
        return new ManualDeclarationCatalogEntry(id, scriptType, declaration, description, examples, 0);
    }
}
