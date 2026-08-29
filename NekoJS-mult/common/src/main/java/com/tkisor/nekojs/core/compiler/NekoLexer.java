package com.tkisor.nekojs.core.compiler;


public interface NekoLexer {
    NekoTokenStream tokenize(NekoSourceFile source) throws Exception;
}
