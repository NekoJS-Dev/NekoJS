package com.tkisor.nekojs.api.compiler;

import java.util.Objects;

public record NekoUnifiedIR(NekoIRProgram program) {
    public NekoUnifiedIR {
        program = Objects.requireNonNull(program, "program");
    }
}
