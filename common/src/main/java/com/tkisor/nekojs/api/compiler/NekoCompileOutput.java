package com.tkisor.nekojs.api.compiler;

import com.tkisor.nekojs.core.module.esm.NekoEsmModuleAst;

import java.util.Objects;

public record NekoCompileOutput(NekoIRProgram program, NekoEsmModuleAst esmAst) {
    public NekoCompileOutput {
        program = Objects.requireNonNull(program, "program");
    }

    public String code() {
        return program.code();
    }
}
