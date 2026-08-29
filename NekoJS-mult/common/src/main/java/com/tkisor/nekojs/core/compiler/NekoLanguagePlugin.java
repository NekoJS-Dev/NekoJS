package com.tkisor.nekojs.core.compiler;

import java.util.Set;

public interface NekoLanguagePlugin {
    String id();

    Set<String> extensions();

    NekoLexer lexer();

    NekoParser parser();

    NekoAstLowering lowering();
}
