package com.tkisor.nekojs.core.module.esm;

import com.tkisor.nekojs.core.module.NekoResolvedModule;

public record NekoEsmResolvedDependency(
        NekoEsmStatement statement,
        String specifier,
        NekoResolvedModule resolved
) {}
