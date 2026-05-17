package com.tkisor.nekojs.api.compiler;

public interface NekoLexer {
    NekoTokenStream tokenize(NekoSourceFile source) throws Exception;
}
