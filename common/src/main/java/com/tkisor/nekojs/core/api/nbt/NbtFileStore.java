package com.tkisor.nekojs.core.api.nbt;

import com.tkisor.nekojs.api.data.NbtValue;
import com.tkisor.nekojs.api.error.ApiErrorCodes;
import com.tkisor.nekojs.api.error.ApiInvocationException;
import com.tkisor.nekojs.api.nbt.NbtBinaryCodec;
import com.tkisor.nekojs.api.nbt.NbtBinaryException;
import com.tkisor.nekojs.api.nbt.NbtBinaryLimits;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.locks.ReentrantLock;

public final class NbtFileStore {
    private final Path dataRoot;
    private final NbtBinaryCodec codec;
    private final NbtBinaryLimits limits;
    private final AtomicMover atomicMover;
    private final ReentrantLock operationLock = new ReentrantLock();

    public NbtFileStore(Path dataRoot, NbtBinaryCodec codec) {
        this(dataRoot, codec, NbtBinaryLimits.DEFAULT, (source, target) -> Files.move(
                source, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING));
    }

    NbtFileStore(Path dataRoot, NbtBinaryCodec codec, NbtBinaryLimits limits, AtomicMover atomicMover) {
        this.dataRoot = Objects.requireNonNull(dataRoot, "dataRoot").toAbsolutePath().normalize();
        this.codec = Objects.requireNonNull(codec, "codec");
        this.limits = Objects.requireNonNull(limits, "limits");
        this.atomicMover = Objects.requireNonNull(atomicMover, "atomicMover");
    }

    public NbtValue.CompoundValue read(String rawPath) {
        operationLock.lock();
        try {
            ResolvedPath path = resolve(rawPath, "read");
            Path root = existingRoot(path);
            if (root == null) return null;
            Path target = path.resolve(root);
            verifyExistingComponents(root, path.relative(), path);
            if (!Files.exists(target, LinkOption.NOFOLLOW_LINKS)) return null;
            if (!Files.isRegularFile(target, LinkOption.NOFOLLOW_LINKS)) {
                throw ioError(path, "NBT read target is not a regular file", null);
            }
            try {
                NbtValue.CompoundValue value = codec.decodeCompressed(readBounded(target, path), limits);
                if (value == null) {
                    throw error(ApiErrorCodes.INVALID_NBT, path, "NBT codec returned no value", null, null);
                }
                return value;
            } catch (NbtBinaryException error) {
                throw codecError(path, error);
            } catch (IOException error) {
                throw ioError(path, "NBT file could not be read", error);
            }
        } finally {
            operationLock.unlock();
        }
    }

    public void write(String rawPath, NbtValue.CompoundValue value) {
        operationLock.lock();
        try {
            writeLocked(rawPath, value);
        } finally {
            operationLock.unlock();
        }
    }

    private void writeLocked(String rawPath, NbtValue.CompoundValue value) {
        ResolvedPath path = resolve(rawPath, "write");
        byte[] content;
        try {
            content = codec.encodeCompressed(Objects.requireNonNull(value, "value"), limits);
        } catch (NbtBinaryException error) {
            throw codecError(path, error);
        }
        if (content == null) {
            throw error(ApiErrorCodes.INVALID_NBT, path, "NBT codec returned no data", null, null);
        }
        if (content.length > limits.maxCompressedBytes()) throw fileTooLarge(path);

        Path root = createRoot(path);
        Path target = path.resolve(root);
        Path temp = null;
        try {
            Path parent = createParentDirectories(root, path.relative(), path);
            verifyExistingComponents(root, path.relative(), path);
            if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)
                    && !Files.isRegularFile(target, LinkOption.NOFOLLOW_LINKS)) {
                throw ioError(path, "NBT write target is not a regular file", null);
            }
            temp = Files.createTempFile(parent, ".nekojs-nbt-", ".tmp");
            try (FileChannel channel = FileChannel.open(temp, StandardOpenOption.WRITE)) {
                ByteBuffer buffer = ByteBuffer.wrap(content);
                while (buffer.hasRemaining()) channel.write(buffer);
                channel.force(true);
            }
        } catch (ApiInvocationException error) {
            throw error;
        } catch (IOException error) {
            throw ioError(path, "NBT file could not be written", error);
        }

        try {
            verifyExistingComponents(root, path.relative(), path);
            atomicMover.move(temp, target);
        } catch (AtomicMoveNotSupportedException error) {
            throw atomicWriteError(path, error);
        } catch (IOException error) {
            throw atomicWriteError(path, error);
        } finally {
            if (temp != null) {
                try {
                    Files.deleteIfExists(temp);
                } catch (IOException ignored) {
                    // Cleanup failure does not weaken the atomic replacement guarantee.
                }
            }
        }
    }

    private ResolvedPath resolve(String rawPath, String operation) {
        if (rawPath == null || rawPath.isEmpty() || rawPath.indexOf('\\') >= 0 || rawPath.matches("^[A-Za-z]:.*")) {
            throw pathForbidden(rawPath, operation);
        }
        for (String segment : rawPath.split("/", -1)) {
            if (segment.isEmpty() || ".".equals(segment) || "..".equals(segment)) {
                throw pathForbidden(rawPath, operation);
            }
        }
        try {
            Path relative = Path.of(rawPath);
            if (relative.isAbsolute()) throw pathForbidden(rawPath, operation);
            return new ResolvedPath(rawPath, operation, relative);
        } catch (InvalidPathException error) {
            throw pathForbidden(rawPath, operation);
        }
    }

    private Path existingRoot(ResolvedPath path) {
        if (!Files.exists(dataRoot, LinkOption.NOFOLLOW_LINKS)) return null;
        return validateRoot(path);
    }

    private Path createRoot(ResolvedPath path) {
        try {
            Path workspace = dataRoot.getParent();
            if (workspace != null && Files.exists(workspace, LinkOption.NOFOLLOW_LINKS)
                    && isLinkOrReparsePoint(workspace)) {
                throw pathForbidden(path.value(), path.operation());
            }
            Files.createDirectories(dataRoot);
            return validateRoot(path);
        } catch (ApiInvocationException error) {
            throw error;
        } catch (IOException error) {
            throw ioError(path, "NBT storage directory could not be created", error);
        }
    }

    private Path validateRoot(ResolvedPath path) {
        try {
            Path workspace = dataRoot.getParent();
            if (workspace != null && Files.exists(workspace, LinkOption.NOFOLLOW_LINKS)
                    && isLinkOrReparsePoint(workspace)) {
                throw pathForbidden(path.value(), path.operation());
            }
            if (isLinkOrReparsePoint(dataRoot)) throw pathForbidden(path.value(), path.operation());
            if (!Files.isDirectory(dataRoot, LinkOption.NOFOLLOW_LINKS)) {
                throw ioError(path, "NBT storage root is not a directory", null);
            }
            return dataRoot.toRealPath();
        } catch (ApiInvocationException error) {
            throw error;
        } catch (IOException error) {
            throw ioError(path, "NBT storage root could not be verified", error);
        }
    }

    private Path createParentDirectories(Path root, Path relative, ResolvedPath path) throws IOException {
        Path current = root;
        for (int index = 0; index < relative.getNameCount() - 1; index++) {
            current = current.resolve(relative.getName(index));
            if (Files.exists(current, LinkOption.NOFOLLOW_LINKS)) {
                if (isLinkOrReparsePoint(current)) throw pathForbidden(path.value(), path.operation());
                if (!Files.isDirectory(current, LinkOption.NOFOLLOW_LINKS)) {
                    throw ioError(path, "NBT storage parent is not a directory", null);
                }
            } else {
                Files.createDirectory(current);
            }
        }
        return current;
    }

    private void verifyExistingComponents(Path root, Path relative, ResolvedPath path) {
        Path current = root;
        for (Path segment : relative) {
            current = current.resolve(segment);
            try {
                if (Files.exists(current, LinkOption.NOFOLLOW_LINKS) && isLinkOrReparsePoint(current)) {
                    throw pathForbidden(path.value(), path.operation());
                }
            } catch (IOException error) {
                throw ioError(path, "NBT storage path could not be verified", error);
            }
        }
    }

    private byte[] readBounded(Path target, ResolvedPath path) throws IOException {
        try (SeekableByteChannel channel = Files.newByteChannel(
                target, Set.of(StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS));
             InputStream input = Channels.newInputStream(channel);
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = input.read(buffer)) >= 0) {
                if (output.size() + read > limits.maxCompressedBytes()) throw fileTooLarge(path);
                output.write(buffer, 0, read);
            }
            return output.toByteArray();
        }
    }

    private static boolean isLinkOrReparsePoint(Path path) throws IOException {
        if (Files.isSymbolicLink(path)) return true;
        BasicFileAttributes attributes = Files.readAttributes(
                path, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        return attributes.isOther();
    }

    private ApiInvocationException codecError(ResolvedPath path, NbtBinaryException error) {
        return switch (error.reason()) {
            case INVALID -> error(ApiErrorCodes.INVALID_NBT, path, "NBT file is not valid", error, null);
            case LIMIT -> error(ApiErrorCodes.NBT_LIMIT_EXCEEDED, path,
                    "NBT file exceeds portable limits", error, null);
            case FILE_SIZE -> fileTooLarge(path, error);
            case UNSUPPORTED -> unsupported(path, error);
        };
    }

    private static ApiInvocationException ioError(ResolvedPath path, String message, Throwable cause) {
        return error(ApiErrorCodes.NBT_IO_ERROR, path, message, cause, null);
    }

    private static ApiInvocationException atomicWriteError(ResolvedPath path, Throwable cause) {
        return error(ApiErrorCodes.NBT_ATOMIC_WRITE_FAILED, path,
                "NBT file could not be atomically replaced", cause, null);
    }

    private ApiInvocationException fileTooLarge(ResolvedPath path) {
        return fileTooLarge(path, null);
    }

    private ApiInvocationException fileTooLarge(ResolvedPath path, Throwable cause) {
        return error(ApiErrorCodes.NBT_FILE_TOO_LARGE, path, "NBT file exceeds the supported compressed size", cause,
                Integer.toString(limits.maxCompressedBytes()));
    }

    private static ApiInvocationException unsupported(ResolvedPath path, Throwable cause) {
        Map<String, String> details = details(path, null);
        details.put("requiredCapability", "nbt-binary-io");
        return new ApiInvocationException(ApiErrorCodes.UNSUPPORTED_CAPABILITY,
                "NBT binary I/O is unavailable on this platform", details, cause);
    }

    private static ApiInvocationException pathForbidden(String rawPath, String operation) {
        return new ApiInvocationException(ApiErrorCodes.NBT_PATH_FORBIDDEN, "NBT path is not allowed",
                Map.of("path", rawPath == null ? "" : rawPath, "operation", operation));
    }

    private static ApiInvocationException error(
            String code, ResolvedPath path, String message, Throwable cause, String limit) {
        return new ApiInvocationException(code, message, details(path, limit), cause);
    }

    private static Map<String, String> details(ResolvedPath path, String limit) {
        Map<String, String> details = new LinkedHashMap<>();
        details.put("path", path.value());
        details.put("operation", path.operation());
        if (limit != null) details.put("limit", limit);
        return details;
    }

    @FunctionalInterface
    interface AtomicMover {
        void move(Path source, Path target) throws IOException;
    }

    private record ResolvedPath(String value, String operation, Path relative) {
        Path resolve(Path root) {
            return root.resolve(relative);
        }
    }
}
