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
        return NekoModulePipeline.prepare(path, source.source());
    }

    private static void publishSourceMap(Path path, NekoPreparedModule prepared) {
        relativePath(path).ifPresent(relativePath -> SourceMapRegistry.register(relativePath, prepared.sourceMap(), prepared.prependedLineCount()));
    }

    private static Path key(Path path) {
        return path.normalize().toAbsolutePath();
    }

    private static Optional<String> relativePath(Path path) {
        try {
            return Optional.of(NekoJSPaths.ROOT.relativize(path).toString().replace('\\', '/'));
        } catch (Exception ignored) {
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
