package com.tkisor.nekojs.core.compiler;

import com.tkisor.nekojs.api.compiler.NekoParser;
import com.tkisor.nekojs.api.compiler.NekoSourceAst;
import com.tkisor.nekojs.api.compiler.NekoTokenStream;
import com.tkisor.nekojs.core.module.esm.NekoEsmParser;

public enum NekoJavaScriptParser implements NekoParser {
    INSTANCE;

    @Override
    public NekoSourceAst parse(NekoTokenStream tokens) {
        return new NekoEsmSourceAst(tokens.source(), "javascript", tokens.source().source(), null, new NekoEsmParser(tokens.source().path(), tokens.source().source()).parse());
    }
}
