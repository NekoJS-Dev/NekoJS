package com.tkisor.nekojs.core.compiler;

import com.tkisor.nekojs.api.compiler.NekoCompileOutput;
import com.tkisor.nekojs.api.compiler.NekoIRProgram;
import com.tkisor.nekojs.api.compiler.NekoLanguagePlugin;
import com.tkisor.nekojs.api.compiler.NekoSourceAst;
import com.tkisor.nekojs.api.compiler.NekoSourceFile;
import com.tkisor.nekojs.api.compiler.NekoTokenStream;
import com.tkisor.nekojs.core.module.esm.NekoEsmModuleAst;

import java.nio.file.Path;

public final class NekoCompilationPipeline {

    public NekoCompileOutput compile(Path file, String source, String extension, NekoLanguagePlugin language) throws Exception {
        NekoSourceFile sourceFile = new NekoSourceFile(file, source, extension);
        NekoTokenStream tokens = language.lexer().tokenize(sourceFile);
        NekoSourceAst ast = language.parser().parse(tokens);
        NekoIRProgram program = language.lowering().lower(ast);
        NekoEsmModuleAst esmAst = ast instanceof NekoEsmSourceAst esm ? esm.esmAst() : null;
        return NekoIdentityJSBackend.INSTANCE.emit(program, esmAst);
    }
}
