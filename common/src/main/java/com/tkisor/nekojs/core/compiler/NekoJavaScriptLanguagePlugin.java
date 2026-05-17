package com.tkisor.nekojs.core.compiler;

import com.tkisor.nekojs.api.compiler.NekoAstLowering;
import com.tkisor.nekojs.api.compiler.NekoLanguagePlugin;
import com.tkisor.nekojs.api.compiler.NekoLexer;
import com.tkisor.nekojs.api.compiler.NekoParser;

import java.util.Set;

public enum NekoJavaScriptLanguagePlugin implements NekoLanguagePlugin {
    INSTANCE;

    @Override
    public String id() {
        return "javascript";
    }

    @Override
    public Set<String> extensions() {
        return Set.of(".js", ".mjs", ".cjs");
    }

    @Override
    public NekoLexer lexer() {
        return NekoRawSourceLexer.INSTANCE;
    }

    @Override
    public NekoParser parser() {
        return NekoJavaScriptParser.INSTANCE;
    }

    @Override
    public NekoAstLowering lowering() {
        return NekoEsmToUnifiedIrLowering.INSTANCE;
    }
}
