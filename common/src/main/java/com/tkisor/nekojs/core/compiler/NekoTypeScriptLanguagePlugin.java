package com.tkisor.nekojs.core.compiler;


import java.util.Set;

public enum NekoTypeScriptLanguagePlugin implements NekoLanguagePlugin {
    INSTANCE;

    @Override
    public String id() {
        return "typescript";
    }

    @Override
    public Set<String> extensions() {
        return Set.of(".ts");
    }

    @Override
    public NekoLexer lexer() {
        return NekoTypeScriptLexer.INSTANCE;
    }

    @Override
    public NekoParser parser() {
        return NekoTypeScriptParser.INSTANCE;
    }

    @Override
    public NekoAstLowering lowering() {
        return NekoEsmToUnifiedIrLowering.INSTANCE;
    }
}
