package com.tkisor.nekojs.core.compiler;

import com.tkisor.nekojs.api.compiler.NekoLexer;
import com.tkisor.nekojs.api.compiler.NekoSourceFile;
import com.tkisor.nekojs.api.compiler.NekoTokenStream;

public enum NekoRawSourceLexer implements NekoLexer {
    INSTANCE;

    @Override
    public NekoTokenStream tokenize(NekoSourceFile source) {
        return new NekoRawTokenStream(source);
    }
}
