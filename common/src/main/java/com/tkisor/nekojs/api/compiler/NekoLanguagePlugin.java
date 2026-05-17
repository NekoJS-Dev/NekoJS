package com.tkisor.nekojs.api.compiler;

import java.util.Set;

public interface NekoLanguagePlugin {
    String id();

    Set<String> extensions();

    NekoLexer lexer();

    NekoParser parser();

    NekoAstLowering lowering();
}
