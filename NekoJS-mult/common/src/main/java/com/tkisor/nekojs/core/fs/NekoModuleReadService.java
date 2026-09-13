package com.tkisor.nekojs.core.fs;

import com.tkisor.nekojs.core.ScriptFilePolicy;
import com.tkisor.nekojs.core.module.NekoModulePreparation;
import com.tkisor.nekojs.core.module.esm.NekoEsmVirtualModuleRegistry;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;

/**
 * 沙箱文件系统的模块读取服务：把脚本路径解析成「Graal 侧实际应该读到的字节」。
 *
 * <p>工单 11：prepared module cache 的所有权移到 runtime owner 后，本服务不再经任何
 * process-wide static 缓存取 prepared code——prepared 字节来自注入的 {@link NekoModulePreparation}。
 */
public final class NekoModuleReadService {

    private final NekoModulePreparation preparation;
    private final ScriptFilePolicy filePolicy;

    public NekoModuleReadService(NekoModulePreparation preparation, ScriptFilePolicy filePolicy) {
        this.preparation = preparation;
        this.filePolicy = filePolicy;
    }

    public Path resolveReadableScript(Path originalPath) {
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
        for (String extension : filePolicy.supportedExtensionsInOrder()) {
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

    public Optional<byte[]> readPreparedBytes(Path path) throws IOException {
        String virtualSource = NekoEsmVirtualModuleRegistry.source(path);
        if (virtualSource != null) {
            return Optional.of(virtualSource.getBytes(StandardCharsets.UTF_8));
        }
        if (path.getFileName() != null && filePolicy.isSupportedScriptFile(path)) {
            return Optional.of(readTransformedModule(path));
        }
        return Optional.empty();
    }

    public Optional<Map<String, Object>> virtualAttributes(Path path) {
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

    public byte[] readTransformedModule(Path path) throws IOException {
        return preparation.prepare(path).code().getBytes(StandardCharsets.UTF_8);
    }
}
