package com.tkisor.nekojs.core.compiler;

import com.tkisor.nekojs.api.compiler.NekoCompileOutput;
import com.tkisor.nekojs.api.compiler.NekoJSBackend;
import com.tkisor.nekojs.api.compiler.NekoLanguagePlugin;
import com.tkisor.nekojs.api.compiler.NekoSourceAst;
import com.tkisor.nekojs.api.compiler.NekoSourceFile;
import com.tkisor.nekojs.api.compiler.NekoTokenStream;
import com.tkisor.nekojs.api.compiler.NekoUnifiedIR;

import java.nio.file.Path;

public final class NekoCompilationPipeline {
    private final NekoJSBackend backend;

    public NekoCompilationPipeline(NekoJSBackend backend) {
        this.backend = backend == null ? NekoIdentityJSBackend.INSTANCE : backend;
    }

    public NekoCompileOutput compile(Path file, String source, String extension, NekoLanguagePlugin language) throws Exception {
        NekoSourceFile sourceFile = new NekoSourceFile(file, source, extension);
        NekoTokenStream tokens = language.lexer().tokenize(sourceFile);
        NekoSourceAst ast = language.parser().parse(tokens);
        NekoUnifiedIR ir = language.lowering().lower(ast);
        return backend.emit(sourceFile, ir);
    }
}
