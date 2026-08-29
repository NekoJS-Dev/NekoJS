package com.tkisor.nekojs.core.compiler;

import com.tkisor.nekojs.core.module.esm.NekoEsmModuleAst;

import java.nio.file.Path;

public final class NekoCompilationPipeline {

    public NekoCompileOutput compile(Path file, String source, String extension, NekoLanguagePlugin language) throws Exception {
        return compile(file, source, extension, language, false);
    }

    public NekoCompileOutput compile(Path file, String source, String extension, NekoLanguagePlugin language,
                                     boolean jsxAutomaticRuntime) throws Exception {
        NekoSourceFile sourceFile = new NekoSourceFile(file, source, extension);
        NekoLexer lexer = language.lexer();
        NekoTokenStream tokens = lexer instanceof NekoJsxLexer jsxLexer
            ? jsxLexer.tokenize(sourceFile, jsxAutomaticRuntime)
            : lexer.tokenize(sourceFile);
        NekoSourceAst ast = language.parser().parse(tokens);
        NekoIRProgram program = language.lowering().lower(ast);
        NekoEsmModuleAst esmAst = ast instanceof NekoEsmSourceAst esm ? esm.esmAst() : null;
        return new NekoCompileOutput(program, esmAst);
    }
}
