package com.tkisor.nekojs.core.module;

public record NekoModuleTransformResult(String code, String sourceMap, NekoModuleMode mode) {
    public NekoModuleTransformResult {
        if (code == null) {
            code = "";
        }
        if (mode == null) {
            mode = NekoModuleMode.COMMONJS;
        }
    }
}
