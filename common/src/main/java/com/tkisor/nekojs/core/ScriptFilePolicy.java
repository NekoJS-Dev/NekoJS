package com.tkisor.nekojs.core;

import com.tkisor.nekojs.core.compiler.ScriptCompilerRegistry;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.LinkedHashSet;
import java.util.Locale;

public final class ScriptFilePolicy {
    private final ScriptCompilerRegistry compilers;

    private final Set<String> explicitExtensions;

    public ScriptFilePolicy(ScriptCompilerRegistry compilers) {
        this(compilers, Set.of());
    }

    public ScriptFilePolicy(ScriptCompilerRegistry compilers, Set<String> explicitExtensions) {
        this.compilers = compilers;
        this.explicitExtensions = java.util.Collections.unmodifiableSet(new LinkedHashSet<>(explicitExtensions));
    }

    public boolean isSupportedScriptFile(Path path) {
        if (path != null && path.getFileName() != null) {
            String name = path.getFileName().toString();
            int dot = name.lastIndexOf('.');
            if (dot >= 0 && explicitExtensions.contains(name.substring(dot).toLowerCase(Locale.ROOT))) return true;
        }
        return compilers.isSupportedScriptFile(path);
    }

    public List<String> supportedExtensionsInOrder() {
        var extensions = new LinkedHashSet<>(compilers.supportedExtensionsInOrder());
        extensions.addAll(explicitExtensions);
        return List.copyOf(extensions);
    }

    public List<String> candidateExtensionsWithJson() {
        List<String> extensions = new ArrayList<>(supportedExtensionsInOrder());
        extensions.add(".json");
        return extensions;
    }

    public static ScriptFilePolicy legacyRuntime() {
        return new ScriptFilePolicy(ScriptCompilerRegistry.current());
    }
}
