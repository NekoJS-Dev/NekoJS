package com.tkisor.nekojs.core.compiler;

import com.tkisor.nekojs.api.compiler.NekoAstLowering;
import com.tkisor.nekojs.api.compiler.NekoIRProgram;
import com.tkisor.nekojs.api.compiler.NekoSourceAst;

public enum NekoEsmToUnifiedIrLowering implements NekoAstLowering {
    INSTANCE;

    @Override
    public NekoIRProgram lower(NekoSourceAst ast) {
        if (!(ast instanceof NekoEsmSourceAst esmAst)) {
            throw new IllegalArgumentException("Expected ESM source AST for unified IR lowering");
        }
        return NekoUnifiedIrBuilder.fromEsm(esmAst.languageId(), esmAst.executableCode(), esmAst.sourceMap(), esmAst.source().extension(), esmAst.esmAst());
    }
}
