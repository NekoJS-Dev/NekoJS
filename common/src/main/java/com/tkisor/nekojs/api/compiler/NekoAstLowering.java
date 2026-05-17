package com.tkisor.nekojs.api.compiler;

public interface NekoAstLowering {
    NekoUnifiedIR lower(NekoSourceAst ast) throws Exception;
}
