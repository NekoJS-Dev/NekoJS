package com.tkisor.nekojs.api.compiler;

public record NekoCompileOutput(String code, NekoUnifiedIR ir) {
    public NekoCompileOutput {
        code = code == null ? "" : code;
    }

    public NekoIRProgram program() {
        return ir == null ? null : ir.program();
    }

    public String sourceMap() {
        return program() == null ? null : program().sourceMap();
    }
}
