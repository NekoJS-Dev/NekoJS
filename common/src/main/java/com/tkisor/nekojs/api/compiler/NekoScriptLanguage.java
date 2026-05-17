package com.tkisor.nekojs.api.compiler;

import java.util.Set;

public record NekoScriptLanguage(
        String id,
        Set<String> extensions,
        IScriptCompiler compiler
) {
    public NekoScriptLanguage {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Script language id must not be blank");
        }
        extensions = extensions == null ? Set.of() : Set.copyOf(extensions);
    }
}
