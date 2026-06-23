package com.tkisor.nekojs.core;

import com.tkisor.nekojs.api.compiler.ScriptCompilerRegistry;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class ScriptFilePolicy {
    private final ScriptCompilerRegistry compilers;

    public ScriptFilePolicy(ScriptCompilerRegistry compilers) {
        this.compilers = compilers;
    }

    public boolean isSupportedScriptFile(Path path) {
        return compilers.isSupportedScriptFile(path);
    }

    public List<String> supportedExtensionsInOrder() {
        return compilers.supportedExtensionsInOrder();
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
