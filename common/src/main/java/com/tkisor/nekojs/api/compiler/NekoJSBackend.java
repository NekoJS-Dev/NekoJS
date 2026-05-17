package com.tkisor.nekojs.api.compiler;

public interface NekoJSBackend {
    NekoCompileOutput emit(NekoSourceFile source, NekoUnifiedIR ir) throws Exception;
}
