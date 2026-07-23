package com.tkisor.nekojs.core.compiler;


public enum NekoRawSourceLexer implements NekoLexer {
    INSTANCE;

    @Override
    public NekoTokenStream tokenize(NekoSourceFile source) {
        return new NekoRawTokenStream(source);
    }
}
