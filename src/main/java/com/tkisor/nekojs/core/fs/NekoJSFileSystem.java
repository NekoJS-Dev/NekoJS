package com.tkisor.nekojs.core.fs;

import com.tkisor.nekojs.api.compiler.IScriptCompiler;
import com.tkisor.nekojs.api.compiler.ScriptCompilerRegistry;
import org.graalvm.polyglot.io.FileSystem;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.channels.NonWritableChannelException;
import java.nio.channels.SeekableByteChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.FileAttribute;
import java.util.Iterator;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;

public class NekoJSFileSystem implements FileSystem {
    private final java.nio.file.FileSystem delegate = FileSystems.getDefault();
    private Path currentWorkingDirectory;

    public NekoJSFileSystem(Path initialWorkingDirectory) {
        this.currentWorkingDirectory = initialWorkingDirectory;
    }

    private Path resolveVirtualScript(Path originalPath) {
        if (Files.exists(originalPath)) {
            return originalPath;
        }

        String fileName = originalPath.getFileName().toString();
        if (fileName.endsWith(".js")) {
            String baseName = fileName.substring(0, fileName.length() - 3);
            Path parent = originalPath.getParent();

            if (parent != null) {
                String[] virtualExtensions = {".ts", ".tsx", ".jsx"};
                for (String ext : virtualExtensions) {
                    Path virtualPath = parent.resolve(baseName + ext);
                    if (Files.exists(virtualPath)) {
                        return virtualPath;
                    }
                }
            }
        }
        return originalPath;
    }

    @Override
    public Path parsePath(URI uri) {
        return delegate.getPath(uri.getPath());
    }

    @Override
    public Path parsePath(String path) {
        Path parsedPath = delegate.getPath(path);
        if (parsedPath.isAbsolute()) {
            return parsedPath;
        }
        return currentWorkingDirectory.resolve(parsedPath);
    }

    @Override
    public void checkAccess(Path path, Set<? extends AccessMode> modes, LinkOption... linkOptions) throws IOException {
        Path verifiedPath = NekoJSPaths.verifyInsideGameDir(path);
        verifiedPath = resolveVirtualScript(verifiedPath);

        if (modes.contains(AccessMode.READ)) {
            if (!Files.exists(verifiedPath)) {
                throw new NoSuchFileException(verifiedPath.toString());
            }
        }
        if (modes.contains(AccessMode.WRITE)) {
            if (!Files.exists(verifiedPath)) {
                Path parent = verifiedPath.getParent();
                if (parent == null || !Files.isWritable(parent)) {
                    throw new AccessDeniedException(verifiedPath.toString());
                }
            } else if (!Files.isWritable(verifiedPath)) {
                throw new AccessDeniedException(verifiedPath.toString());
            }
        }
        if (modes.contains(AccessMode.EXECUTE)) {
            if (!Files.isExecutable(verifiedPath)) {
                throw new AccessDeniedException(verifiedPath.toString());
            }
        }
    }

    @Override
    public void createDirectory(Path dir, FileAttribute<?>... attrs) throws IOException {
        Path verifiedPath = NekoJSPaths.verifyInsideGameDir(dir);
        Files.createDirectory(verifiedPath, attrs);
    }

    @Override
    public void delete(Path path) throws IOException {
        Path verifiedPath = NekoJSPaths.verifyInsideGameDir(path);
        Files.delete(verifiedPath);
    }

    @Override
    public SeekableByteChannel newByteChannel(Path path, Set<? extends OpenOption> options, FileAttribute<?>... attrs) throws IOException {
        Path verifiedPath = NekoJSPaths.verifyInsideGameDir(path);
        verifiedPath = resolveVirtualScript(verifiedPath);

        if (!options.contains(StandardOpenOption.WRITE) && !options.contains(StandardOpenOption.APPEND)) {
            String fileName = verifiedPath.getFileName().toString();
            int dotIndex = fileName.lastIndexOf('.');

            if (dotIndex > 0) {
                String ext = fileName.substring(dotIndex);
                IScriptCompiler compiler = ScriptCompilerRegistry.getCompiler(ext);

                if (compiler != null) {
                    try {
                        String rawCode = Files.readString(verifiedPath);
                        String compiledJs = compiler.compile(verifiedPath, rawCode);
                        byte[] bytes = compiledJs.getBytes(StandardCharsets.UTF_8);
                        return new ReadOnlyMemoryByteChannel(bytes);

                    } catch (Exception e) {
                        throw new IOException("[NekoJSFileSystem] 转译脚本拦截失败: " + verifiedPath, e);
                    }
                }
            }
        }

        return Files.newByteChannel(verifiedPath, options, attrs);
    }

    @Override
    public Map<String, Object> readAttributes(Path path, String attributes, LinkOption... options) throws IOException {
        Path verifiedPath = NekoJSPaths.verifyInsideGameDir(path);
        verifiedPath = resolveVirtualScript(verifiedPath);
        return Files.readAttributes(verifiedPath, attributes, options);
    }

    @Override
    public Path toRealPath(Path path, LinkOption... linkOptions) throws IOException {
        Path verifiedPath = NekoJSPaths.verifyInsideGameDir(path);
        verifiedPath = resolveVirtualScript(verifiedPath);
        return verifiedPath.toRealPath(linkOptions);
    }

    private static class ReadOnlyMemoryByteChannel implements SeekableByteChannel {
        private final ByteBuffer buffer;

        public ReadOnlyMemoryByteChannel(byte[] bytes) {
            this.buffer = ByteBuffer.wrap(bytes);
        }

        @Override
        public int read(ByteBuffer dst) throws IOException {
            if (!buffer.hasRemaining()) return -1;
            int len = Math.min(dst.remaining(), buffer.remaining());
            int oldLimit = buffer.limit();
            buffer.limit(buffer.position() + len);
            dst.put(buffer);
            buffer.limit(oldLimit);
            return len;
        }

        @Override
        public int write(ByteBuffer src) throws IOException {
            throw new NonWritableChannelException();
        }

        @Override
        public long position() throws IOException {
            return buffer.position();
        }

        @Override
        public SeekableByteChannel position(long newPosition) throws IOException {
            if (newPosition < 0 || newPosition > buffer.capacity()) {
                throw new IllegalArgumentException("Invalid position");
            }
            buffer.position((int) newPosition);
            return this;
        }

        @Override
        public long size() throws IOException {
            return buffer.capacity();
        }

        @Override
        public SeekableByteChannel truncate(long size) throws IOException {
            throw new NonWritableChannelException();
        }

        @Override
        public boolean isOpen() {
            return true;
        }

        @Override
        public void close() throws IOException {
        }
    }

    @Override
    public DirectoryStream<Path> newDirectoryStream(Path dir, DirectoryStream.Filter<? super Path> filter) throws IOException {
        Path verifiedPath = NekoJSPaths.verifyInsideGameDir(dir);
        DirectoryStream<Path> delegateStream = Files.newDirectoryStream(verifiedPath);
        return new FilteredDirectoryStream(delegateStream, filter);
    }

    private Path resolvePath(Path path) {
        if (path.isAbsolute()) {
            return path;
        }
        return currentWorkingDirectory.resolve(path);
    }

    @Override
    public Path toAbsolutePath(Path path) {
        return resolvePath(path).toAbsolutePath();
    }

    @Override
    public void createLink(Path link, Path existing) throws IOException {
        Path verifiedLink = NekoJSPaths.verifyInsideGameDir(link);
        Path verifiedExisting = NekoJSPaths.verifyInsideGameDir(existing);
        Files.createLink(verifiedLink, verifiedExisting);
    }

    @Override
    public void setAttribute(Path path, String attribute, Object value, LinkOption... options) throws IOException {
        Path verifiedPath = NekoJSPaths.verifyInsideGameDir(path);
        Files.setAttribute(verifiedPath, attribute, value, options);
    }

    @Override
    public void createSymbolicLink(Path link, Path target, FileAttribute<?>... attrs) throws IOException {
        Path verifiedLink = NekoJSPaths.verifyInsideGameDir(link);
        Files.createSymbolicLink(verifiedLink, target, attrs);
    }

    @Override
    public Path readSymbolicLink(Path link) throws IOException {
        Path verifiedLink = NekoJSPaths.verifyInsideGameDir(link);
        return Files.readSymbolicLink(verifiedLink);
    }

    @Override
    public void setCurrentWorkingDirectory(Path currentWorkingDirectory) {
        this.currentWorkingDirectory = currentWorkingDirectory;
    }

    @Override
    public Path getTempDirectory() {
        Path tempDir = NekoJSPaths.ROOT.resolve("temp");
        try {
            Files.createDirectories(tempDir);
        } catch (IOException e) {
            throw new RuntimeException("Failed to create temp directory", e);
        }
        return tempDir;
    }

    private static class FilteredDirectoryStream implements DirectoryStream<Path> {
        private final DirectoryStream<Path> delegate;
        private final Filter<? super Path> filter;

        public FilteredDirectoryStream(DirectoryStream<Path> delegate, Filter<? super Path> filter) {
            this.delegate = delegate;
            this.filter = filter;
        }

        @Override
        public @NotNull Iterator<Path> iterator() {
            Iterator<Path> delegateIterator = delegate.iterator();
            return new Iterator<>() {
                private Path nextPath;

                @Override
                public boolean hasNext() {
                    if (nextPath != null) {
                        return true;
                    }

                    while (delegateIterator.hasNext()) {
                        Path path = delegateIterator.next();
                        try {
                            NekoJSPaths.verifyInsideGameDir(path);
                            if (filter == null || filter.accept(path)) {
                                nextPath = path;
                                return true;
                            }
                        } catch (IOException e) {
                        }
                    }
                    return false;
                }

                @Override
                public Path next() {
                    if (nextPath == null && !hasNext()) {
                        throw new NoSuchElementException();
                    }
                    Path result = nextPath;
                    nextPath = null;
                    return result;
                }
            };
        }

        @Override
        public void close() throws IOException {
            delegate.close();
        }
    }
}