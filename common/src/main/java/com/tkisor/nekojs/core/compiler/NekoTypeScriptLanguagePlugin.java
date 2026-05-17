package com.tkisor.nekojs.core.compiler;

import com.tkisor.nekojs.api.compiler.NekoAstLowering;
import com.tkisor.nekojs.api.compiler.NekoLanguagePlugin;
import com.tkisor.nekojs.api.compiler.NekoLexer;
import com.tkisor.nekojs.api.compiler.NekoParser;

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
