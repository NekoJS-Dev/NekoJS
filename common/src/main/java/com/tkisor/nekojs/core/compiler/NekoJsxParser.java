package com.tkisor.nekojs.core.compiler;

import com.tkisor.nekojs.api.compiler.NekoParser;
import com.tkisor.nekojs.api.compiler.NekoSourceAst;
import com.tkisor.nekojs.api.compiler.NekoTokenStream;
import com.tkisor.nekojs.core.module.esm.NekoEsmParser;

public enum NekoJsxParser implements NekoParser {
    INSTANCE;

    @Override
    public NekoSourceAst parse(NekoTokenStream tokens) {
        if (!(tokens instanceof NekoJsxTokenStream jsxTokens)) {
            throw new IllegalArgumentException("Expected JSX token stream");
        }
        return new NekoEsmSourceAst(jsxTokens.source(), jsxTokens.languageId(), jsxTokens.transformedSource(), jsxTokens.sourceMap(), new NekoEsmParser(jsxTokens.source().path(), jsxTokens.transformedSource()).parse());
    }
}
