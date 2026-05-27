package com.tkisor.nekojs.api.compiler;

public interface NekoAstLowering {
    NekoIRProgram lower(NekoSourceAst ast) throws Exception;
}
