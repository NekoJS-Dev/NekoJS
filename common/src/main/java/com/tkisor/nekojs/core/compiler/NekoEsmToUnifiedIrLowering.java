package com.tkisor.nekojs.core.compiler;


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
