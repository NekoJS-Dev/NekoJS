package com.tkisor.nekojs.core.module;

import com.tkisor.nekojs.core.module.cache.NekoModulePipelineCache;

import java.io.IOException;
import java.nio.file.Path;

public final class NekoModulePreparationCache {
    private NekoModulePreparationCache() {}

    public static NekoPreparedModule prepare(Path path) throws IOException {
        return NekoModulePipelineCache.prepare(path);
    }

    public static void clear() {
        NekoModulePipelineCache.clear();
    }

    public static void invalidate(Path path) {
        NekoModulePipelineCache.invalidate(path);
    }
}
