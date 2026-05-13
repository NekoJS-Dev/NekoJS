package com.tkisor.nekojs.core.module;

public record NekoModuleTransformResult(String code, String sourceMap, NekoModuleMode mode, int prependedLineCount) {
    public NekoModuleTransformResult(String code, String sourceMap, NekoModuleMode mode) {
        this(code, sourceMap, mode, 0);
    }

    public NekoModuleTransformResult {
        if (code == null) {
            code = "";
        }
        if (mode == null) {
            mode = NekoModuleMode.COMMONJS;
        }
        prependedLineCount = Math.max(0, prependedLineCount);
    }
}
