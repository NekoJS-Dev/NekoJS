package com.tkisor.nekojs.core.compiler;

import com.tkisor.nekojs.api.compiler.NekoParser;
import com.tkisor.nekojs.api.compiler.NekoSourceAst;
import com.tkisor.nekojs.api.compiler.NekoTokenStream;
import com.tkisor.nekojs.core.module.esm.NekoEsmParser;

public enum NekoTypeScriptParser implements NekoParser {
    INSTANCE;

    @Override
    public NekoSourceAst parse(NekoTokenStream tokens) {
        if (!(tokens instanceof NekoTypeScriptTokenStream tsTokens)) {
            throw new IllegalArgumentException("Expected TypeScript token stream");
        }
        return new NekoEsmSourceAst(tsTokens.source(), "typescript", tsTokens.erasedSource(), tsTokens.sourceMap(), new NekoEsmParser(tsTokens.source().path(), tsTokens.erasedSource()).parse());
    }
}
