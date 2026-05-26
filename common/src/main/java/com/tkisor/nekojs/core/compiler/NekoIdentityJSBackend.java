package com.tkisor.nekojs.core.compiler;

import com.tkisor.nekojs.api.compiler.NekoCompileOutput;
import com.tkisor.nekojs.api.compiler.NekoSourceFile;
import com.tkisor.nekojs.api.compiler.NekoUnifiedIR;
import com.tkisor.nekojs.core.module.esm.NekoEsmModuleAst;

public enum NekoIdentityJSBackend {
    INSTANCE;

    public NekoCompileOutput emit(NekoSourceFile source, NekoUnifiedIR ir, NekoEsmModuleAst esmAst) {
        return new NekoCompileOutput(ir, esmAst);
    }
}
