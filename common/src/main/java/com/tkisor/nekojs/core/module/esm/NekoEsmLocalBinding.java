package com.tkisor.nekojs.core.module.esm;

public record NekoEsmLocalBinding(
        String name,
        String kind,
        NekoEsmSpan span
) {}
