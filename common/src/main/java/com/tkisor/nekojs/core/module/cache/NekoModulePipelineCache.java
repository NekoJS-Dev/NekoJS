package com.tkisor.nekojs.core.module.cache;

import com.tkisor.nekojs.core.error.SourceMapRegistry;
import com.tkisor.nekojs.core.fs.NekoJSPaths;
import com.tkisor.nekojs.core.module.NekoModulePipeline;
import com.tkisor.nekojs.core.module.NekoPreparedModule;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 模块准备缓存：持有 prepared module cache 为 static map（过渡），但 prepare 委托
 * bootstrap 绑定的 {@link NekoModulePipeline} 实例（{@link NekoModulePipeline#legacyInstance()}），
 * 不再直接调用 static {@code NekoModulePipeline.prepare}。
 *
 * <p>路径 relativize 仍走 {@code NekoJSPaths.legacy()}（过渡），后续 Phase 3F / 6 随 loader host
 * 注入实例 paths 后改为实例字段。
 */
public final class NekoModulePipelineCache {
    private static final Map<Path, PreparedEntry> PREPARED_CACHE = new ConcurrentHashMap<>();

    private NekoModulePipelineCache() {}

    public static NekoPreparedModule prepare(Path path) throws IOException {
        Path key = key(path);
        try {
            SourceSnapshot source = readSource(key);
            PreparedEntry cached = PREPARED_CACHE.get(key);
            if (cached != null && cached.stamp().equals(source.stamp())) {
                publishSourceMap(key, cached.prepared());
                return cached.prepared();
            }
            NekoPreparedModule prepared = prepareSource(key, source);
            publishSourceMap(key, prepared);
            PREPARED_CACHE.put(key, new PreparedEntry(source.stamp(), prepared));
            return prepared;
        } catch (IOException e) {
            throw e;
        } catch (Exception e) {
            throw new IOException("Failed to prepare NekoJS module: " + key + ": " + rootMessage(e), e);
        }
    }

    public static void clear() {
        PREPARED_CACHE.clear();
        SourceMapRegistry.clear();
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
        return new SourceSnapshot(FileStamp.read(path), Files.readString(path));
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
            return Optional.of(NekoJSPaths.legacy().root().relativize(path).toString().replace('\\', '/'));
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

    private record SourceSnapshot(FileStamp stamp, String source) {}

    private record PreparedEntry(FileStamp stamp, NekoPreparedModule prepared) {}

    private record FileStamp(long modifiedMillis, long size) {
        private static FileStamp read(Path path) throws IOException {
            return new FileStamp(Files.getLastModifiedTime(path).toMillis(), Files.size(path));
        }
    }
}
