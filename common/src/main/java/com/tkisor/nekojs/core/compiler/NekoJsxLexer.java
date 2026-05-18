package com.tkisor.nekojs.core.compiler;

import com.tkisor.nekojs.api.compiler.NekoLexer;
import com.tkisor.nekojs.api.compiler.NekoSourceFile;
import com.tkisor.nekojs.api.compiler.NekoTokenStream;
import com.tkisor.nekojs.api.compiler.ScriptCompileResult;

public enum NekoJsxLexer implements NekoLexer {
    INSTANCE;

    @Override
    public NekoTokenStream tokenize(NekoSourceFile source) {
        boolean tsx = ".tsx".equals(source.extension());
        ScriptCompileResult compiled = tsx ? NekoJsxCompiler.compileTsx(source.path(), source.source()) : NekoJsxCompiler.compileJsx(source.path(), source.source());
        return new NekoJsxTokenStream(source, tsx ? "tsx" : "jsx", compiled.code(), compiled.sourceMap());
    }
}
