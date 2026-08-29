package com.tkisor.nekojs.core.module.esm;

public record NekoEsmSpan(int start, int end) {
    public NekoEsmSpan {
        if (start < 0 || end < start) {
            throw new IllegalArgumentException("Invalid ESM span: " + start + ".." + end);
        }
    }
}
