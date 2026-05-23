package com.tkisor.nekojs.core.module;

import com.tkisor.nekojs.core.error.SourceMapRegistry;
import com.tkisor.nekojs.core.fs.NekoJSPaths;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class NekoModulePreparationCache {
    private static final Map<Path, Entry> CACHE = new ConcurrentHashMap<>();

    private NekoModulePreparationCache() {}

    public static NekoPreparedModule prepare(Path path) throws IOException {
        Path key = path.normalize().toAbsolutePath();
        try {
            FileStamp stamp = FileStamp.read(key);
            Entry cached = CACHE.get(key);
            if (cached != null && cached.stamp().equals(stamp)) {
                return cached.prepared();
            }
            NekoPreparedModule prepared = NekoModulePipeline.prepare(key, Files.readString(key));
            registerSourceMap(key, prepared.sourceMap(), prepared.prependedLineCount());
            CACHE.put(key, new Entry(stamp, prepared));
            return prepared;
        } catch (IOException e) {
            throw e;
        } catch (Exception e) {
            throw new IOException("Failed to prepare NekoJS module: " + key + ": " + rootMessage(e), e);
        }
    }

    public static void clear() {
        CACHE.clear();
        SourceMapRegistry.clear();
    }

    public static void invalidate(Path path) {
        if (path != null) {
            Path key = path.normalize().toAbsolutePath();
            CACHE.remove(key);
            relativePath(key).ifPresent(SourceMapRegistry::clear);
        }
    }

    private static void registerSourceMap(Path path, String sourceMap, int prependedLineCount) {
        relativePath(path).ifPresent(relativePath -> SourceMapRegistry.register(relativePath, sourceMap, prependedLineCount));
    }

    private static java.util.Optional<String> relativePath(Path path) {
        try {
            return java.util.Optional.of(NekoJSPaths.ROOT.relativize(path).toString().replace('\\', '/'));
        } catch (Exception ignored) {
            return java.util.Optional.empty();
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

    private record Entry(FileStamp stamp, NekoPreparedModule prepared) {}

    private record FileStamp(long modifiedMillis, long size) {
        private static FileStamp read(Path path) throws IOException {
            return new FileStamp(Files.getLastModifiedTime(path).toMillis(), Files.size(path));
        }
    }
}
