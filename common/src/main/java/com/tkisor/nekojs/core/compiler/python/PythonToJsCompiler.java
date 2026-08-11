package com.tkisor.nekojs.core.compiler.python;

import com.tkisor.nekojs.core.compiler.IScriptCompiler;
import com.tkisor.nekojs.core.compiler.python.ast.PythonNode;

import java.nio.file.Path;
import java.util.List;

/**
 * Transpiles a Python subset to JavaScript, registered for the {@code .py} extension by
 * {@link PythonTranspilerPlugin}. Pipeline: {@link PythonLexer} (with INDENT/DEDENT) →
 * {@link PythonParser} (recursive-descent AST) → {@link PythonEmitter} (JS source).
 *
 * <p>The emitted JS is then handed to the engine's {@code NekoEsmParser} by the pipeline
 * (see {@code NekoLegacyLanguagePlugin}); this compiler only produces a JS string.
 *
 * <p>Errors throw {@link IllegalArgumentException} with the file + position so the pipeline can
 * surface them to the user.
 */
public final class PythonToJsCompiler implements IScriptCompiler {

    @Override
    public boolean canCompile(String extension) {
        if (extension == null) return false;
        String e = extension.toLowerCase();
        return e.equals(".py") || e.equals("py");
    }

    @Override
    public String compile(Path file, String sourceCode) throws Exception {
        try {
            List<PythonToken> tokens = new PythonLexer(sourceCode).tokenize();
            PythonNode ast = new PythonParser(tokens).parseModule();
            return new PythonEmitter().emit((PythonNode.Module) ast);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("python transpile failed in " + file + ": " + e.getMessage(), e);
        }
    }
}
