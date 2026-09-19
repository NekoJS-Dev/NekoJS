package com.tkisor.nekojs.core.compiler;


import java.util.Set;

/** JSX language identity for `.jsx`; `.tsx` has its own identity in {@link NekoTsxLanguagePlugin}. */
public enum NekoJsxLanguagePlugin implements NekoLanguagePlugin {
    INSTANCE;

    @Override
    public String id() {
        return "jsx";
    }

    @Override
    public Set<String> extensions() {
        return Set.of(".jsx");
    }

    @Override
    public NekoLexer lexer() {
        return NekoJsxLexer.INSTANCE;
    }

    @Override
    public NekoParser parser() {
        return NekoJsxParser.INSTANCE;
    }

    @Override
    public NekoAstLowering lowering() {
        return NekoEsmToUnifiedIrLowering.INSTANCE;
    }
}
