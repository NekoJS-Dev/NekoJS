package com.tkisor.nekojs.core.ui;

import graal.graalvm.polyglot.io.FileSystem;

import java.io.IOException;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.NonWritableChannelException;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.AccessDeniedException;
import java.nio.file.AccessMode;
import java.nio.file.DirectoryStream;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.FileTime;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Set;

/** Resolves native ESM imports from two fixed in-memory resources, with no host filesystem access. */
final class BuiltinUiModules implements FileSystem {
    private static final Path ROOT = Path.of(".nekojs-builtin-ui").toAbsolutePath().normalize();
    private static final Path RUNTIME = ROOT.resolve("jsx-runtime.mjs");
    private static final Path DASHBOARD = ROOT.resolve("error-dashboard.mjs");
    private final Map<Path, byte[]> sources;

    BuiltinUiModules(String runtime, String dashboard) {
        // The canonical runtime registers Node exports. Keep that registration local to its ESM adapter.
        String module = "let registered;\n(function(globalThis){\n" + runtime
                + "\n})({__nekoNodeDefine:(ids,exports)=>{registered=exports}});\n"
                + "export const {UI,jsx,jsxs,Fragment}=registered;\n";
        sources = Map.of(RUNTIME, module.getBytes(StandardCharsets.UTF_8),
                DASHBOARD, dashboard.getBytes(StandardCharsets.UTF_8));
    }

    URI dashboardUri() { return DASHBOARD.toUri(); }
    String dashboardSource() { return new String(sources.get(DASHBOARD), StandardCharsets.UTF_8); }

    @Override public Path parsePath(URI uri) { return Path.of(uri); }
    @Override public Path parsePath(String path) {
        if ("nekojs/jsx-runtime".equals(path)) return RUNTIME;
        if (path.startsWith("file:")) return parsePath(URI.create(path));
        return toAbsolutePath(Path.of(path));
    }
    @Override public Path toAbsolutePath(Path path) { return (path.isAbsolute() ? path : ROOT.resolve(path)).normalize(); }
    @Override public Path toRealPath(Path path, LinkOption... options) throws IOException {
        Path absolute = toAbsolutePath(path);
        if (!sources.containsKey(absolute) && !ROOT.equals(absolute)) throw new NoSuchFileException(absolute.toString());
        return absolute;
    }
    @Override public void checkAccess(Path path, Set<? extends AccessMode> modes, LinkOption... options) throws IOException {
        toRealPath(path);
        if (modes.contains(AccessMode.WRITE) || modes.contains(AccessMode.EXECUTE)) throw denied(path);
    }
    @Override public void createDirectory(Path path, FileAttribute<?>... attributes) throws IOException { throw denied(path); }
    @Override public void delete(Path path) throws IOException { throw denied(path); }
    @Override public DirectoryStream<Path> newDirectoryStream(Path path, DirectoryStream.Filter<? super Path> filter) throws IOException {
        throw denied(path);
    }
    @Override public Map<String, Object> readAttributes(Path path, String attributes, LinkOption... options) throws IOException {
        Path absolute = toRealPath(path);
        boolean directory = ROOT.equals(absolute);
        return Map.of("isRegularFile", !directory, "isDirectory", directory, "isSymbolicLink", false,
                "isOther", false, "size", directory ? 0L : (long) sources.get(absolute).length,
                "creationTime", FileTime.fromMillis(0), "lastModifiedTime", FileTime.fromMillis(0),
                "lastAccessTime", FileTime.fromMillis(0), "fileKey", absolute.toString());
    }
    @Override public SeekableByteChannel newByteChannel(Path path, Set<? extends OpenOption> options,
                                                       FileAttribute<?>... attributes) throws IOException {
        if (options.stream().anyMatch(option -> option != StandardOpenOption.READ)) throw denied(path);
        byte[] bytes = sources.get(toRealPath(path));
        if (bytes == null) throw denied(path);
        return new MemoryChannel(bytes);
    }
    private static AccessDeniedException denied(Path path) { return new AccessDeniedException(path.toString()); }

    private static final class MemoryChannel implements SeekableByteChannel {
        private final ByteBuffer bytes;
        private boolean open = true;
        private MemoryChannel(byte[] source) { bytes = ByteBuffer.wrap(source).asReadOnlyBuffer(); }
        private void requireOpen() throws ClosedChannelException { if (!open) throw new ClosedChannelException(); }
        @Override public int read(ByteBuffer destination) throws IOException {
            requireOpen();
            if (!destination.hasRemaining()) return 0;
            if (!bytes.hasRemaining()) return -1;
            int count = Math.min(bytes.remaining(), destination.remaining());
            ByteBuffer portion = bytes.slice(); portion.limit(count);
            destination.put(portion); bytes.position(bytes.position() + count);
            return count;
        }
        @Override public int write(ByteBuffer source) throws IOException { requireOpen(); throw new NonWritableChannelException(); }
        @Override public long position() throws IOException { requireOpen(); return bytes.position(); }
        @Override public SeekableByteChannel position(long position) throws IOException {
            requireOpen();
            if (position < 0 || position > bytes.limit()) throw new IllegalArgumentException("[NEKO-7015] Invalid built-in module read position");
            bytes.position((int) position); return this;
        }
        @Override public long size() throws IOException { requireOpen(); return bytes.limit(); }
        @Override public SeekableByteChannel truncate(long size) throws IOException { requireOpen(); throw new NonWritableChannelException(); }
        @Override public boolean isOpen() { return open; }
        @Override public void close() { open = false; }
    }
}
