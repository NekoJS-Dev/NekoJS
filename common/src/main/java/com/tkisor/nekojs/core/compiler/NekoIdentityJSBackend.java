package com.tkisor.nekojs.core.compiler;

import com.tkisor.nekojs.api.compiler.NekoCompileOutput;
import com.tkisor.nekojs.api.compiler.NekoIRProgram;
import com.tkisor.nekojs.core.module.esm.NekoEsmModuleAst;

public enum NekoIdentityJSBackend {
    INSTANCE;

    public NekoCompileOutput emit(NekoIRProgram program, NekoEsmModuleAst esmAst) {
        return new NekoCompileOutput(program, esmAst);
    }
}
