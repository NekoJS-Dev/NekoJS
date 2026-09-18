package com.tkisor.nekojs.core.module;

import java.nio.file.Path;
import java.util.Objects;

/** Explicit filesystem roots used by module resolution; contains no platform owner. */
public record NekoModuleResolutionPaths(Path gameDir, Path root, Path nodeModules) {
    public NekoModuleResolutionPaths {
        gameDir = Objects.requireNonNull(gameDir, "gameDir");
        root = Objects.requireNonNull(root, "root");
        nodeModules = Objects.requireNonNull(nodeModules, "nodeModules");
    }
}
