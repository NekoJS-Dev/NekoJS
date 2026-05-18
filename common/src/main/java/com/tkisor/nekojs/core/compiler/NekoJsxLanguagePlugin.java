package com.tkisor.nekojs.core.compiler;

import com.tkisor.nekojs.api.compiler.NekoAstLowering;
import com.tkisor.nekojs.api.compiler.NekoLanguagePlugin;
import com.tkisor.nekojs.api.compiler.NekoLexer;
import com.tkisor.nekojs.api.compiler.NekoParser;

import java.util.Set;

public enum NekoJsxLanguagePlugin implements NekoLanguagePlugin {
    INSTANCE;

    @Override
    public String id() {
        return "jsx";
    }

    @Override
    public Set<String> extensions() {
        return Set.of(".jsx", ".tsx");
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
