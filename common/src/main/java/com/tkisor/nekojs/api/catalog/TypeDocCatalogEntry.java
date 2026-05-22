package com.tkisor.nekojs.api.catalog;

import com.tkisor.nekojs.script.ScriptTypePredicate;

import java.util.List;

public record TypeDocCatalogEntry(
        String kind,
        String target,
        ScriptTypePredicate scriptType,
        String typeOverride,
        String description,
        List<String> examples,
        int priority
) {
    public TypeDocCatalogEntry {
        examples = List.copyOf(examples == null ? List.of() : examples);
    }

    public static TypeDocCatalogEntry binding(String name, String typeOverride, String description, List<String> examples) {
        return new TypeDocCatalogEntry("binding", name, ScriptTypePredicate.any(), typeOverride, description, examples, 0);
    }

    public static TypeDocCatalogEntry binding(ScriptTypePredicate scriptType, String name, String typeOverride, String description, List<String> examples) {
        return new TypeDocCatalogEntry("binding", name, scriptType, typeOverride, description, examples, 0);
    }
}
