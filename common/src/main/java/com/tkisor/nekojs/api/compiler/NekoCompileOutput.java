package com.tkisor.nekojs.api.compiler;

import com.tkisor.nekojs.core.module.esm.NekoEsmModuleAst;

import java.util.Objects;

public record NekoCompileOutput(NekoUnifiedIR ir, NekoEsmModuleAst esmAst) {
    public NekoCompileOutput {
        ir = Objects.requireNonNull(ir, "ir");
    }

    public String code() {
        return ir.program().code();
    }

    public NekoIRProgram program() {
        return ir.program();
    }

    public String sourceMap() {
        return ir.program().sourceMap();
    }
}
