package com.tkisor.nekojs.api.compiler;

import java.util.ArrayList;
import java.util.List;

public final class ScriptCompilerRegistry {
    private static final List<IScriptCompiler> COMPILERS = new ArrayList<>();

    public static void register(IScriptCompiler compiler) {
        COMPILERS.add(compiler);
    }

    public static IScriptCompiler getCompiler(String extension) {
        for (IScriptCompiler compiler : COMPILERS) {
            if (compiler.canCompile(extension)) {
                return compiler;
            }
        }
        return null;
    }
}