package com.tkisor.nekojs.core.module;

import java.nio.file.Path;

public record NekoResolvedModule(
        Path path,
        String id,
        String dirname,
        String specifier,
        NekoModuleKind kind
) {
    public static NekoResolvedModule special(String specifier, NekoModuleKind kind) {
        return new NekoResolvedModule(null, null, null, specifier, kind);
    }

    public boolean special() {
        return path == null;
    }

    public boolean json() {
        return kind == NekoModuleKind.JSON;
    }
}
