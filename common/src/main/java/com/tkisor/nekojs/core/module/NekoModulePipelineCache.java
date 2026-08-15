package com.tkisor.nekojs.core.module;

import com.tkisor.nekojs.api.ScriptType;
import com.tkisor.nekojs.core.error.SourceMapRegistry;
import com.tkisor.nekojs.core.fs.NekoJSPaths;
import com.tkisor.nekojs.core.module.NekoModulePipeline;
import com.tkisor.nekojs.core.module.NekoPreparedModule;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 模块准备缓存：持有 prepared module cache 和 source map cache。
 * prepare 逻辑委托给 {@link NekoModulePipeline} 实例。
 */
public final class NekoModulePipelineCache {
    private static final Map<Path, PreparedEntry> PREPARED_CACHE = new ConcurrentHashMap<>();

    private NekoModulePipelineCache() {}

    public static NekoPreparedModule prepare(Path path) throws IOException {
        Path key = key(path);
        try {
            SourceSnapshot source = readSource(key);
            // Atomic check-then-act: previously get→miss→compute→put could let two threads
            // loading the same path both run the full pipeline. computeIfAbsent guarantees
            // a single pipeline run per (key, stamp); the inner stamp check avoids recomputing
            // when the cached entry is still valid for this source stamp.
            PreparedEntry entry = PREPARED_CACHE.compute(key, (k, existing) -> {
                if (existing != null && existing.stamp().equals(source.stamp())) {
                    return existing;
                }
                try {
                    NekoPreparedModule prepared = prepareSource(k, source);
                    return new PreparedEntry(source.stamp(), prepared, scriptTypeOf(k));
                } catch (IOException | RuntimeException ex) {
                    throw new PipelineException(ex);
                } catch (Exception ex) {
                    // prepareSource declares 'throws Exception'; tunnel other checked
                    // exceptions out of the compute lambda the same way.
                    throw new PipelineException(ex);
                }
            });
            publishSourceMap(key, entry.prepared());
            return entry.prepared();
        } catch (PipelineException wrapper) {
            Throwable cause = wrapper.getCause();
            if (cause instanceof IOException io) {
                throw io;
            }
            if (cause instanceof RuntimeException re) {
                throw re;
            }
            throw new IOException("Failed to prepare NekoJS module: " + key + ": " + rootMessage(cause), cause);
        } catch (IOException e) {
            throw e;
        } catch (Exception e) {
            throw new IOException("Failed to prepare NekoJS module: " + key + ": " + rootMessage(e), e);
        }
    }

    /** Internal carrier to tunnel checked exceptions out of the ConcurrentHashMap compute lambda. */
    private static final class PipelineException extends RuntimeException {
        private static final long serialVersionUID = 1L;
        PipelineException(Throwable cause) { super(cause); }
    }

    public static void clear() {
        PREPARED_CACHE.clear();
        SourceMapRegistry.clear();
    }

    /**
     * 仅清空指定 {@link ScriptType} 的 prepared 模块缓存与对应 source map。
     * 进程级静态缓存原本无 ScriptType 维度：单机 CLIENT 触发 reload 会误清 SERVER 等
     * 其它类型已编译的模块（下次 import 重新编译）。各类型脚本根目录为 {@code nekojs/<name>_scripts}
     * 下的互不相交子树，故按 key 推导所属类型；不在任何类型脚本目录下的 key
     * （如 node_modules，跨类型共享）不受影响。无参 {@link #clear()} 保持全清语义，
     * 供 ModuleReloadCoordinator 等显式全清路径使用。
     */
    public static void clear(ScriptType type) {
        if (type == null) {
            clear();
            return;
        }
        PREPARED_CACHE.entrySet().removeIf(entry -> entry.getValue().type() == type);
        SourceMapRegistry.clearByScriptType(type);
    }

    public static void invalidate(Path path) {
        if (path == null) {
            return;
        }
        Path key = key(path);
        PREPARED_CACHE.remove(key);
        relativePath(key).ifPresent(SourceMapRegistry::clear);
    }

    private static SourceSnapshot readSource(Path path) throws IOException {
        String source = Files.readString(path);
        return new SourceSnapshot(FileStamp.read(path, source), source);
    }

    private static NekoPreparedModule prepareSource(Path path, SourceSnapshot source) throws Exception {
        NekoModulePipeline pipeline = NekoModulePipeline.legacyInstance();
        if (pipeline != null) {
            return pipeline.prepare(path, source.source());
        }
        return NekoModulePipeline.legacyPrepare(path, source.source());
    }

    private static void publishSourceMap(Path path, NekoPreparedModule prepared) {
        relativePath(path).ifPresent(relativePath -> SourceMapRegistry.register(relativePath, prepared.sourceMap(), prepared.prependedLineCount()));
    }

    private static Path key(Path path) {
        return path.normalize().toAbsolutePath();
    }

    private static Optional<String> relativePath(Path path) {
        try {
            return Optional.of(NekoJSPaths.get().root().relativize(path).toString().replace('\\', '/'));
        } catch (Exception ignored) { // relative path computation fails → cache miss
            return Optional.empty();
        }
    }

    private static String rootMessage(Throwable throwable) {
        Throwable root = throwable;
        while (root.getCause() != null) {
            root = root.getCause();
        }
        String message = root.getMessage();
        return message == null || message.isBlank() ? root.toString() : message;
    }

    /**
     * 从缓存 key（模块文件绝对路径）推导所属 {@link ScriptType}：脚本根目录遵循
     * {@code <name>_scripts} 命名约定（与 {@link SourceMapRegistry#clearByScriptType} 的
     * root-relative 前缀约定一致），key 相对 nekojs root 的第一个路径段即类型目录名。
     * 不在任何类型目录下（如 node_modules）的 key 是跨类型共享缓存，返回 null。
     */
    private static ScriptType scriptTypeOf(Path key) {
        if (key == null) {
            return null;
        }
        try {
            Path root = NekoJSPaths.get().root().normalize().toAbsolutePath();
            if (!key.startsWith(root)) {
                return null;
            }
            Path relative = root.relativize(key);
            if (relative.getNameCount() < 1) {
                return null;
            }
            String first = relative.getName(0).toString();
            for (ScriptType type : ScriptType.all()) {
                Path typePath = type.path;
                String dirName = typePath == null ? type.name + "_scripts" : typePath.getFileName().toString();
                // Windows 文件系统大小写不敏感：手建的 Server_scripts 目录同样落在 SERVER
                // 类型子树内（Path.startsWith 在 Windows 上本就忽略大小写），这里必须同等
                // 忽略大小写匹配，否则该目录下的模块会被误标为跨类型共享缓存、逃脱按类型清理
                if (first.equalsIgnoreCase(dirName)) {
                    return type;
                }
            }
        } catch (Exception ignored) { // 路径解析失败 → 视为共享缓存
        }
        return null;
    }

    private record SourceSnapshot(FileStamp stamp, String source) {}

    private record PreparedEntry(FileStamp stamp, NekoPreparedModule prepared, ScriptType type) {}

    /**
     * 模块文件的内容指纹。
     *
     * <p>历史缺陷：仅 (modifiedMillis, size) 无法区分“同一时间戳刻度内对等长文件的覆盖写入”，
     * 粗粒度时间戳文件系统（如部分 Windows / FAT / 容器挂载）会因此误判未变化，继续返回旧编译模块。
     * 修复：增加 {@code contentHash}。这里选择 SHA-256 hex 而不是 64-bit hash（如 xxhash/两个 long）：
     * 源码在 prepare 前已经完整读入（{@code Files.readString}），SHA-256 的额外开销相对模块编译管线
     * 可以忽略；而 64-bit 在长期运行、大量模块的场景下仍有生日碰撞的微小概率，一旦碰撞会静默返回
     * 错误代码，SHA-256 可以把这种风险降到工程上可忽略。安全比这微小的 CPU 开销更重要。
     */
    private record FileStamp(long modifiedMillis, long size, String contentHash) {
        private static FileStamp read(Path path, String source) throws IOException {
            return new FileStamp(Files.getLastModifiedTime(path).toMillis(), Files.size(path), contentHash(source));
        }

        private static String contentHash(String source) {
            try {
                MessageDigest digest = MessageDigest.getInstance("SHA-256");
                return HexFormat.of().formatHex(digest.digest(source.getBytes(StandardCharsets.UTF_8)));
            } catch (NoSuchAlgorithmException e) {
                // JDK 规范要求 SHA-256 算法必须存在；这里作为环境缺陷快速失败。
                throw new IllegalStateException("SHA-256 digest is not available on this JVM", e);
            }
        }
    }
}
