package com.tkisor.nekojs.core.api.json;

import com.tkisor.nekojs.api.data.JsonValue;
import com.tkisor.nekojs.api.error.ApiErrorCodes;
import com.tkisor.nekojs.api.error.ApiInvocationException;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.Channels;
import java.nio.channels.SeekableByteChannel;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
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

public final class JsonFileStore {
    public static final int MAX_FILE_BYTES = JsonValue.MAX_INPUT_CHARS * 3;

    private final Path dataRoot;
    private final AtomicMover atomicMover;
    // Serializes managed calls; JDK has no portable openat-style traversal for hostile native processes.
    private final ReentrantLock operationLock = new ReentrantLock();

    public JsonFileStore(Path dataRoot) {
        this(dataRoot, (source, target) -> Files.move(
                source, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING));
    }

    JsonFileStore(Path dataRoot, AtomicMover atomicMover) {
        this.dataRoot = Objects.requireNonNull(dataRoot, "dataRoot").toAbsolutePath().normalize();
        this.atomicMover = Objects.requireNonNull(atomicMover, "atomicMover");
    }

    public JsonValue read(String rawPath) {
        operationLock.lock();
        try {
            return readLocked(rawPath);
        } finally {
            operationLock.unlock();
        }
    }

    private JsonValue readLocked(String rawPath) {
        ResolvedPath path = resolve(rawPath, "read");
        Path root = existingRoot(path);
        if (root == null) return null;
        Path target = path.resolve(root);
        verifyExistingComponents(root, path.relative(), path);
        if (!Files.exists(target, LinkOption.NOFOLLOW_LINKS)) return null;
        if (!Files.isRegularFile(target, LinkOption.NOFOLLOW_LINKS)) {
            throw ioError(path, "JSON read target is not a regular file", null);
        }
        try {
            String source = decodeUtf8(readBounded(target, path));
            if (source.startsWith("\uFEFF")) {
                throw invalidJson(path, "JSON files must not include a byte-order mark", null);
            }
            return JsonValueParser.parse(source);
        } catch (JsonValueException error) {
            throw codecError(path, error);
        } catch (CharacterCodingException error) {
            throw invalidJson(path, "JSON file is not valid UTF-8", error);
        } catch (IOException error) {
            throw ioError(path, "JSON file could not be read", error);
        }
    }

    public void write(String rawPath, JsonValue value) {
        operationLock.lock();
        try {
            writeLocked(rawPath, value);
        } finally {
            operationLock.unlock();
        }
    }

    private void writeLocked(String rawPath, JsonValue value) {
        ResolvedPath path = resolve(rawPath, "write");
        byte[] content = serialize(path, value);
        Path root = createRoot(path);
        Path target = path.resolve(root);
        Path temp = null;
        try {
            Path parent = createParentDirectories(root, path.relative(), path);
            verifyExistingComponents(root, path.relative(), path);
            if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)
                    && !Files.isRegularFile(target, LinkOption.NOFOLLOW_LINKS)) {
                throw ioError(path, "JSON write target is not a regular file", null);
            }
            temp = Files.createTempFile(parent, ".nekojs-json-", ".tmp");
            try (FileChannel channel = FileChannel.open(temp, StandardOpenOption.WRITE)) {
                ByteBuffer buffer = ByteBuffer.wrap(content);
                while (buffer.hasRemaining()) channel.write(buffer);
                channel.force(true);
            }
        } catch (ApiInvocationException error) {
            throw error;
        } catch (IOException error) {
            throw ioError(path, "JSON file could not be written", error);
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
                    // A failed cleanup cannot weaken the atomic replacement guarantee.
                }
            }
        }
    }

    private ResolvedPath resolve(String rawPath, String operation) {
        if (rawPath == null || rawPath.isEmpty() || rawPath.indexOf('\\') >= 0 || rawPath.matches("^[A-Za-z]:.*")) {
            throw pathForbidden(rawPath, operation);
        }
        String[] segments = rawPath.split("/", -1);
        for (String segment : segments) {
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
            throw ioError(path, "JSON storage directory could not be created", error);
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
                throw ioError(path, "JSON storage root is not a directory", null);
            }
            return dataRoot.toRealPath();
        } catch (ApiInvocationException error) {
            throw error;
        } catch (IOException error) {
            throw ioError(path, "JSON storage root could not be verified", error);
        }
    }

    private Path createParentDirectories(Path root, Path relative, ResolvedPath path) throws IOException {
        Path current = root;
        for (int index = 0; index < relative.getNameCount() - 1; index++) {
            current = current.resolve(relative.getName(index));
            if (Files.exists(current, LinkOption.NOFOLLOW_LINKS)) {
                if (isLinkOrReparsePoint(current)) throw pathForbidden(path.value(), path.operation());
                if (!Files.isDirectory(current, LinkOption.NOFOLLOW_LINKS)) {
                    throw ioError(path, "JSON storage parent is not a directory", null);
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
                throw ioError(path, "JSON storage path could not be verified", error);
            }
        }
    }

    private byte[] serialize(ResolvedPath path, JsonValue value) {
        try {
            byte[] bytes = JsonValueSerializer.pretty(value).getBytes(StandardCharsets.UTF_8);
            if (bytes.length > MAX_FILE_BYTES) throw fileTooLarge(path);
            return bytes;
        } catch (JsonValueException error) {
            throw codecError(path, error);
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
                if (output.size() + read > MAX_FILE_BYTES) throw fileTooLarge(path);
                output.write(buffer, 0, read);
            }
            return output.toByteArray();
        }
    }

    private static String decodeUtf8(byte[] bytes) throws CharacterCodingException {
        return StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes))
                .toString();
    }

    private static boolean isLinkOrReparsePoint(Path path) throws IOException {
        if (Files.isSymbolicLink(path)) return true;
        BasicFileAttributes attributes = Files.readAttributes(
                path, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        return attributes.isOther();
    }

    private static ApiInvocationException codecError(ResolvedPath path, JsonValueException error) {
        String code = error.reason() == JsonValueException.Reason.LIMIT_EXCEEDED
                ? ApiErrorCodes.JSON_LIMIT_EXCEEDED
                : ApiErrorCodes.INVALID_JSON;
        return error(code, path, "JSON file is not valid", error, null);
    }

    private static ApiInvocationException invalidJson(ResolvedPath path, String message, Throwable cause) {
        return error(ApiErrorCodes.INVALID_JSON, path, message, cause, null);
    }

    private static ApiInvocationException ioError(ResolvedPath path, String message, Throwable cause) {
        return error(ApiErrorCodes.JSON_IO_ERROR, path, message, cause, null);
    }

    private static ApiInvocationException atomicWriteError(ResolvedPath path, Throwable cause) {
        return error(ApiErrorCodes.JSON_ATOMIC_WRITE_FAILED, path, "JSON file could not be atomically replaced", cause, null);
    }

    private static ApiInvocationException fileTooLarge(ResolvedPath path) {
        return error(ApiErrorCodes.JSON_FILE_TOO_LARGE, path, "JSON file exceeds the supported size", null,
                Integer.toString(MAX_FILE_BYTES));
    }

    private static ApiInvocationException pathForbidden(String rawPath, String operation) {
        String path = rawPath == null ? "" : rawPath;
        return new ApiInvocationException(ApiErrorCodes.JSON_PATH_FORBIDDEN, "JSON path is not allowed",
                Map.of("path", path, "operation", operation));
    }

    private static ApiInvocationException error(
            String code, ResolvedPath path, String message, Throwable cause, String limit) {
        Map<String, String> details = new LinkedHashMap<>();
        details.put("path", path.value());
        details.put("operation", path.operation());
        if (limit != null) details.put("limit", limit);
        return new ApiInvocationException(code, message, details, cause);
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
