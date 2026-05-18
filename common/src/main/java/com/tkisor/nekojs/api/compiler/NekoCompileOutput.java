package com.tkisor.nekojs.api.compiler;

import java.util.Objects;

public record NekoCompileOutput(String code, NekoUnifiedIR ir) {
    public NekoCompileOutput {
        code = code == null ? "" : code;
        ir = Objects.requireNonNull(ir, "ir");
    }

    public NekoIRProgram program() {
        return ir.program();
    }

    public String sourceMap() {
        return program().sourceMap();
    }
}
