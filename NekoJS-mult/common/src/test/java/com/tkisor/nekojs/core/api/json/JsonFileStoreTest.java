package com.tkisor.nekojs.core.api.json;

import com.tkisor.nekojs.api.data.JsonValue;
import com.tkisor.nekojs.api.error.ApiErrorCodes;
import com.tkisor.nekojs.api.error.ApiInvocationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class JsonFileStoreTest {
    @TempDir
    Path tempDir;

    @Test
    void writesPrettyUtf8ReadsValuesAndReturnsNullForMissingFiles() throws Exception {
        JsonFileStore store = store();
        LinkedHashMap<String, JsonValue> values = new LinkedHashMap<>();
        values.put("enabled", JsonValue.bool(true));
        values.put("count", JsonValue.number("2"));

        store.write("settings/ui.json", JsonValue.object(values));

        Path file = dataRoot().resolve("settings/ui.json");
        assertEquals("""
                {
                  "enabled": true,
                  "count": 2
                }""", Files.readString(file, StandardCharsets.UTF_8));
        assertEquals("{\"enabled\":true,\"count\":2}",
                JsonValueSerializer.compact(store.read("settings/ui.json")));
        assertNull(store.read("settings/missing.json"));

        store.write("null.json", JsonValue.nullValue());
        assertEquals("null", Files.readString(dataRoot().resolve("null.json"), StandardCharsets.UTF_8));
        assertInstanceOf(JsonValue.NullValue.class, store.read("null.json"));
    }

    @Test
    void rejectsNonPortableAndTraversingPaths() {
        JsonFileStore store = store();
        for (String path : List.of("", "../escape.json", "a/../b.json", "./value.json", "a\\value.json", "/value.json", "C:/value.json")) {
            ApiInvocationException error = assertThrows(ApiInvocationException.class, () -> store.read(path));
            assertEquals(ApiErrorCodes.JSON_PATH_FORBIDDEN, error.code(), path);
            assertEquals(path, error.details().get("path"));
            assertEquals("read", error.details().get("operation"));
        }
    }

    @Test
    void rejectsMalformedUtf8MalformedJsonDirectoriesAndOversizedFiles() throws Exception {
        JsonFileStore store = store();
        Files.createDirectories(dataRoot());

        Files.writeString(dataRoot().resolve("malformed.json"), "{", StandardCharsets.UTF_8);
        assertCode(ApiErrorCodes.INVALID_JSON, () -> store.read("malformed.json"));

        Files.write(dataRoot().resolve("invalid-utf8.json"), new byte[] {(byte) 0xC3, 0x28});
        assertCode(ApiErrorCodes.INVALID_JSON, () -> store.read("invalid-utf8.json"));

        Files.createDirectory(dataRoot().resolve("directory.json"));
        assertCode(ApiErrorCodes.JSON_IO_ERROR, () -> store.read("directory.json"));
        assertCode(ApiErrorCodes.JSON_IO_ERROR, () -> store.write("directory.json", JsonValue.nullValue()));

        Files.write(dataRoot().resolve("large.json"), new byte[JsonFileStore.MAX_FILE_BYTES + 1]);
        ApiInvocationException oversized = assertThrows(ApiInvocationException.class, () -> store.read("large.json"));
        assertEquals(ApiErrorCodes.JSON_FILE_TOO_LARGE, oversized.code());
        assertEquals(Integer.toString(JsonFileStore.MAX_FILE_BYTES), oversized.details().get("limit"));
    }

    @Test
    void rejectsSymlinkParentsAndLeaves() throws Exception {
        JsonFileStore store = store();
        Path outside = tempDir.resolve("outside");
        Files.createDirectories(outside);
        Files.createDirectories(dataRoot());
        try {
            Files.createSymbolicLink(dataRoot().resolve("link"), outside);
            Files.createSymbolicLink(dataRoot().resolve("leaf.json"), outside.resolve("leaf.json"));
        } catch (UnsupportedOperationException | IOException error) {
            assumeTrue(false, "symbolic links are not available for this test process");
            return;
        }

        assertCode(ApiErrorCodes.JSON_PATH_FORBIDDEN, () -> store.write("link/escape.json", JsonValue.nullValue()));
        assertCode(ApiErrorCodes.JSON_PATH_FORBIDDEN, () -> store.read("leaf.json"));
    }

    @Test
    void rejectsWindowsJunctionParents() throws Exception {
        assumeTrue(System.getProperty("os.name").toLowerCase(java.util.Locale.ROOT).contains("win"),
                "junctions are a Windows filesystem feature");
        JsonFileStore store = store();
        Path outside = tempDir.resolve("outside");
        Files.createDirectories(outside);
        Files.createDirectories(dataRoot());
        Path junction = dataRoot().resolve("junction");
        Process process = new ProcessBuilder("cmd", "/c", "mklink", "/J", junction.toString(), outside.toString())
                .redirectErrorStream(true)
                .start();
        int exitCode = process.waitFor();
        assumeTrue(exitCode == 0, "junction creation is not available for this test process");

        assertCode(ApiErrorCodes.JSON_PATH_FORBIDDEN,
                () -> store.write("junction/escape.json", JsonValue.nullValue()));
    }

    @Test
    void preservesExistingDataWhenAtomicReplacementFails() throws Exception {
        JsonFileStore normal = store();
        normal.write("settings/ui.json", JsonValue.string("old"));
        normal.write("settings/ui.json", JsonValue.string("stable"));
        assertEquals("\"stable\"", Files.readString(dataRoot().resolve("settings/ui.json"), StandardCharsets.UTF_8));
        JsonFileStore failing = new JsonFileStore(dataRoot(), (source, target) -> {
            throw new AtomicMoveNotSupportedException(source.toString(), target.toString(), "forced");
        });

        assertCode(ApiErrorCodes.JSON_ATOMIC_WRITE_FAILED,
                () -> failing.write("settings/ui.json", JsonValue.string("new")));
        assertEquals("\"stable\"", Files.readString(dataRoot().resolve("settings/ui.json"), StandardCharsets.UTF_8));
    }

    private JsonFileStore store() {
        return new JsonFileStore(dataRoot());
    }

    private Path dataRoot() {
        return tempDir.resolve("game").resolve("nekojs").resolve("data");
    }

    private static void assertCode(String expected, ThrowingOperation operation) {
        ApiInvocationException error = assertThrows(ApiInvocationException.class, operation::run);
        assertEquals(expected, error.code());
    }

    @FunctionalInterface
    private interface ThrowingOperation {
        void run() throws Exception;
    }
}
