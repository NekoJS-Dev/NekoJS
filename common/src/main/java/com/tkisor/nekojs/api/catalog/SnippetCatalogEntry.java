package com.tkisor.nekojs.api.catalog;

import com.tkisor.nekojs.api.ScriptTypePredicate;
import com.tkisor.nekojs.api.WithScriptType;

public record SnippetCatalogEntry(
        String name,
        ScriptTypePredicate scriptType,
        String prefix,
        String body,
        String description
) implements WithScriptType {
}
