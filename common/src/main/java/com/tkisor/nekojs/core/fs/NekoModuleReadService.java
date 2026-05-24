package com.tkisor.nekojs.core.fs;

import com.tkisor.nekojs.api.compiler.ScriptCompilerRegistry;
import com.tkisor.nekojs.core.module.NekoModulePreparationCache;
import com.tkisor.nekojs.core.module.esm.NekoEsmVirtualModuleRegistry;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;

public final class NekoModuleReadService {
    private NekoModuleReadService() {}

    public static Path resolveReadableScript(Path originalPath) {
        if (NekoEsmVirtualModuleRegistry.isVirtualModule(originalPath)) {
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
        for (String extension : ScriptCompilerRegistry.current().supportedExtensionsInOrder()) {
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

    public static Optional<byte[]> readPreparedBytes(Path path) throws IOException {
        String virtualSource = NekoEsmVirtualModuleRegistry.source(path);
        if (virtualSource != null) {
            return Optional.of(virtualSource.getBytes(StandardCharsets.UTF_8));
        }
        if (path.getFileName() != null && NekoJSPaths.isSupportedScriptFile(path)) {
            return Optional.of(readTransformedModule(path));
        }
        return Optional.empty();
    }

    public static Optional<Map<String, Object>> virtualAttributes(Path path) {
        if (NekoEsmVirtualModuleRegistry.isVirtualDirectory(path)) {
            return Optional.of(Map.of(
                    "isRegularFile", false,
                    "isDirectory", true,
                    "isSymbolicLink", false,
                    "isOther", false,
                    "size", 0L
            ));
        }
        if (NekoEsmVirtualModuleRegistry.isVirtualModule(path)) {
            String source = NekoEsmVirtualModuleRegistry.source(path);
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

    public static byte[] readTransformedModule(Path path) throws IOException {
        return NekoModulePreparationCache.prepare(path).code().getBytes(StandardCharsets.UTF_8);
    }
}
