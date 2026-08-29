package com.tkisor.nekojs.core.compiler;

import com.tkisor.nekojs.core.module.esm.NekoEsmParser;

public enum NekoJavaScriptParser implements NekoParser {
    INSTANCE;

    @Override
    public NekoSourceAst parse(NekoTokenStream tokens) {
        return new NekoEsmSourceAst(tokens.source(), "javascript", tokens.source().source(), null, new NekoEsmParser(tokens.source().path(), tokens.source().source()).parse());
    }
}
