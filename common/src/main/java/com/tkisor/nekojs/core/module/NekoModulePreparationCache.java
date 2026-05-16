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
    }

    public static void invalidate(Path path) {
        if (path != null) {
            CACHE.remove(path.normalize().toAbsolutePath());
        }
    }

    private static void registerSourceMap(Path path, String sourceMap, int prependedLineCount) {
        try {
            String relativePath = NekoJSPaths.ROOT.relativize(path).toString().replace('\\', '/');
            SourceMapRegistry.register(relativePath, sourceMap, prependedLineCount);
        } catch (Exception ignored) {
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
