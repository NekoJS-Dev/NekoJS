package com.tkisor.nekojs.core.compiler;

import java.nio.file.Path;

/**
 * Extension interface for NekoJS script compilers.
 */
public interface IScriptCompiler {
    /// Matches a file extension (for example ".ts", ".tsx").
    boolean canCompile(String extension);

    /// Takes the source file and its source code, and returns the compiled JS string.
    String compile(Path file, String sourceCode) throws Exception;

    default ScriptCompileResult compileDetailed(Path file, String sourceCode) throws Exception {
        return ScriptCompileResult.codeOnly(compile(file, sourceCode));
    }
}