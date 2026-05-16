package com.tkisor.nekojs.core.fs;

import com.tkisor.nekojs.core.module.NekoModulePreparationCache;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

public final class NekoModuleReadService {
    private NekoModuleReadService() {}

    public static byte[] readTransformedModule(Path path) throws IOException {
        return NekoModulePreparationCache.prepare(path).code().getBytes(StandardCharsets.UTF_8);
    }
}
