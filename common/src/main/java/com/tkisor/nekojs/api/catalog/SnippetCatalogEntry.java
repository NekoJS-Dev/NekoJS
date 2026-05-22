package com.tkisor.nekojs.api.catalog;

import com.tkisor.nekojs.script.ScriptTypePredicate;
import com.tkisor.nekojs.script.WithScriptType;

public record SnippetCatalogEntry(
        String name,
        ScriptTypePredicate scriptType,
        String prefix,
        String body,
        String description
) implements WithScriptType {
}
