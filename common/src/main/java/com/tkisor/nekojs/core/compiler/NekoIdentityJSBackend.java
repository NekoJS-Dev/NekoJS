package com.tkisor.nekojs.core.compiler;

import com.tkisor.nekojs.api.compiler.NekoCompileOutput;
import com.tkisor.nekojs.api.compiler.NekoJSBackend;
import com.tkisor.nekojs.api.compiler.NekoSourceFile;
import com.tkisor.nekojs.api.compiler.NekoUnifiedIR;

public enum NekoIdentityJSBackend implements NekoJSBackend {
    INSTANCE;

    @Override
    public NekoCompileOutput emit(NekoSourceFile source, NekoUnifiedIR ir) {
        return new NekoCompileOutput(ir.program().code(), ir);
    }
}
