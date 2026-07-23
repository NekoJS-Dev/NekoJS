package com.tkisor.nekojs.core.compiler;


public enum NekoTypeScriptLexer implements NekoLexer {
    INSTANCE;

    @Override
    public NekoTokenStream tokenize(NekoSourceFile source) {
        NekoTypeScriptCompiler.TypeScriptTransformResult result = NekoTypeScriptCompiler.eraseDetailed(source.path(), source.source());
        return new NekoTypeScriptTokenStream(source, result.code(), result.sourceMap());
    }
}
