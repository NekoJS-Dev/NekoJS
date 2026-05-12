package com.tkisor.nekojs.api.catalog;

import com.tkisor.nekojs.script.ScriptType;
import com.tkisor.nekojs.script.WithScriptType;

public record SnippetCatalogEntry(
        String name,
        ScriptType scriptType,
        String prefix,
        String body,
        String description
) implements WithScriptType {
}
