package com.tkisor.nekojs.core.compiler.python;

import com.tkisor.nekojs.core.compiler.IScriptCompiler;
import com.tkisor.nekojs.core.compiler.ScriptCompileResult;
import com.tkisor.nekojs.core.compiler.python.ast.PythonNode;

import java.nio.file.Path;
import java.util.List;

/**
 * Transpiles a Python subset to JavaScript, registered for the {@code .py} extension by
 * {@link PythonTranspilerPlugin}. Pipeline: {@link PythonLexer} (with INDENT/DEDENT) →
 * {@link PythonParser} (recursive-descent AST) → {@link PythonEmitter} (JS source).
 *
 * <p>{@link #compileDetailed} returns both the JS and a statement-granularity v3 source map
 * (via {@link PythonSourceMap}) so runtime stack traces can map JS lines back to Python source.
 * Errors throw {@link IllegalArgumentException} with the file + position.
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
        return compileDetailed(file, sourceCode).code();
    }

    @Override
    public ScriptCompileResult compileDetailed(Path file, String sourceCode) throws Exception {
        try {
            List<PythonToken> tokens = new PythonLexer(sourceCode).tokenize();
            PythonParser parser = new PythonParser(tokens);
            PythonNode ast = parser.parseModule();
            PythonEmitter emitter = new PythonEmitter(parser.srcLines());
            String js = emitter.emit((PythonNode.Module) ast);
            int totalLines = countLines(js);
            String fileName = file == null ? "python" : file.getFileName().toString();
            String sourceMap = PythonSourceMap.build(fileName, sourceCode, emitter.mappings(), totalLines);
            return new ScriptCompileResult(js, sourceMap);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("python transpile failed in " + file + ": " + e.getMessage(), e);
        }
    }

    private static int countLines(String s) {
        if (s == null || s.isEmpty()) return 0;
        int count = 0;
        for (int i = 0; i < s.length(); i++) if (s.charAt(i) == '\n') count++;
        return count;
    }
}
