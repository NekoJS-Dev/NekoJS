package com.tkisor.nekojs.api.compiler;

import java.util.Set;

public record NekoScriptLanguage(
        String id,
        Set<String> extensions,
        IScriptCompiler compiler,
        NekoLanguagePlugin plugin
) {
    public NekoScriptLanguage {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Script language id must not be blank");
        }
        extensions = extensions == null ? Set.of() : Set.copyOf(extensions);
    }

    public NekoScriptLanguage(String id, Set<String> extensions, IScriptCompiler compiler) {
        this(id, extensions, compiler, null);
    }

    public NekoScriptLanguage(String id, Set<String> extensions, NekoLanguagePlugin plugin) {
        this(id, extensions, null, plugin);
    }
}
