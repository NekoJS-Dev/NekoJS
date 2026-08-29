package com.tkisor.nekojs.core.compiler;


public interface NekoAstLowering {
    NekoIRProgram lower(NekoSourceAst ast) throws Exception;
}
