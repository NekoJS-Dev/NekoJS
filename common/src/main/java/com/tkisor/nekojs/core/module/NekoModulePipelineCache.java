package com.tkisor.nekojs.core.module;

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
                    return new PreparedEntry(source.stamp(), prepared);
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

    private record SourceSnapshot(FileStamp stamp, String source) {}

    private record PreparedEntry(FileStamp stamp, NekoPreparedModule prepared) {}

    private record FileStamp(long modifiedMillis, long size) {
        private static FileStamp read(Path path) throws IOException {
            return new FileStamp(Files.getLastModifiedTime(path).toMillis(), Files.size(path));
        }
    }
}
