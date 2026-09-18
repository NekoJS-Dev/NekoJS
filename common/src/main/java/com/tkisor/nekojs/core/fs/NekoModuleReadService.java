package com.tkisor.nekojs.core.fs;

import com.tkisor.nekojs.core.ScriptFilePolicy;
import com.tkisor.nekojs.core.module.NekoModulePipelineCache;
import com.tkisor.nekojs.core.module.NekoVirtualModuleView;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;

public final class NekoModuleReadService {
    private NekoModuleReadService() {}

    public static Path resolveReadableScript(Path originalPath, NekoVirtualModuleView virtualModules) {
        if (virtualModules.isVirtualModule(originalPath)) {
            return originalPath;
        }
        if (Files.exists(originalPath)) {
            return originalPath;
        }

        if (originalPath.getFileName() == null) {
            return originalPath;
        }
        String fileName = originalPath.getFileName().toString();
        if (!fileName.endsWith(".js")) {
            return originalPath;
        }
        Path parent = originalPath.getParent();
        if (parent == null) {
            return originalPath;
        }

        String baseName = fileName.substring(0, fileName.length() - 3);
        for (String extension : ScriptFilePolicy.legacyRuntime().supportedExtensionsInOrder()) {
            if (".js".equals(extension)) {
                continue;
            }
            Path virtualPath = parent.resolve(baseName + extension);
            if (Files.exists(virtualPath)) {
                return virtualPath;
            }
        }
        return originalPath;
    }

    /**
     * 读已准备字节（W3 显式注入：调用方传入其 runtime-owned 缓存实例；无隐藏静态缓存）。
     */
    public static Optional<byte[]> readPreparedBytes(Path path, NekoModulePipelineCache preparationCache,
                                                     NekoVirtualModuleView virtualModules) throws IOException {
        String virtualSource = virtualModules.source(path);
        if (virtualSource != null) {
            return Optional.of(virtualSource.getBytes(StandardCharsets.UTF_8));
        }
        if (path.getFileName() != null && ScriptFilePolicy.legacyRuntime().isSupportedScriptFile(path)) {
            return Optional.of(readTransformedModule(path, preparationCache));
        }
        return Optional.empty();
    }

    public static Optional<Map<String, Object>> virtualAttributes(Path path,
                                                                  NekoVirtualModuleView virtualModules) {
        if (virtualModules.isVirtualDirectory(path)) {
            return Optional.of(Map.of(
                    "isRegularFile", false,
                    "isDirectory", true,
                    "isSymbolicLink", false,
                    "isOther", false,
                    "size", 0L
            ));
        }
        if (virtualModules.isVirtualModule(path)) {
            String source = virtualModules.source(path);
            return Optional.of(Map.of(
                    "isRegularFile", true,
                    "isDirectory", false,
                    "isSymbolicLink", false,
                    "isOther", false,
                    "size", source == null ? 0L : (long) source.getBytes(StandardCharsets.UTF_8).length
            ));
        }
        return Optional.empty();
    }

    public static byte[] readTransformedModule(Path path, NekoModulePipelineCache preparationCache) throws IOException {
        return preparationCache.prepare(path).code().getBytes(StandardCharsets.UTF_8);
    }
}
