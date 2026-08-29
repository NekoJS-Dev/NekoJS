package com.tkisor.nekojs.core.api.nbt;

import com.tkisor.nekojs.api.data.NbtValue;
import com.tkisor.nekojs.api.error.ApiErrorCodes;
import com.tkisor.nekojs.api.error.ApiInvocationException;
import com.tkisor.nekojs.api.nbt.NbtBinaryCodec;
import com.tkisor.nekojs.api.nbt.NbtBinaryException;
import com.tkisor.nekojs.api.nbt.NbtBinaryLimits;
import com.tkisor.nekojs.core.api.facade.DefaultNbtFacade;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.io.IOException;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

@Tag("nbt-smoke")
class NbtFileStoreTest {
    private static final NbtValue.CompoundValue VALUE = NbtValue.compound(Map.of("count", NbtValue.intValue(2)));

    @TempDir
    Path tempDir;

    @Test
    void writesCompressedBytesReadsPortableCompoundsAndReturnsNullForMissingFiles() throws Exception {
        NbtFileStore store = store(new FixtureCodec());

        assertNull(store.read("missing.nbt"));
        store.write("players/neko.nbt", VALUE);

        assertArrayEquals(FixtureCodec.BYTES, Files.readAllBytes(dataRoot().resolve("players/neko.nbt")));
        assertEquals(VALUE, store.read("players/neko.nbt"));
    }

    @Test
    void rejectsNonPortableAndTraversingPaths() {
        NbtFileStore store = store(new FixtureCodec());
        for (String path : List.of("", "../escape.nbt", "a/../b.nbt", "./value.nbt", "a\\value.nbt", "/value.nbt", "C:/value.nbt")) {
            ApiInvocationException error = assertThrows(ApiInvocationException.class, () -> store.read(path));
            assertEquals(ApiErrorCodes.NBT_PATH_FORBIDDEN, error.code(), path);
            assertEquals(path, error.details().get("path"));
            assertEquals("read", error.details().get("operation"));
        }
    }

    @Test
    void normalizesCodecFailuresAndRejectsOversizedFiles() throws Exception {
        Files.createDirectories(dataRoot());
        Files.write(dataRoot().resolve("large.nbt"), new byte[NbtBinaryLimits.DEFAULT.maxCompressedBytes() + 1]);
        assertCode(ApiErrorCodes.NBT_FILE_TOO_LARGE, () -> store(new FixtureCodec()).read("large.nbt"));

        assertCode(ApiErrorCodes.INVALID_NBT, () -> store(failing(NbtBinaryException.Reason.INVALID)).read("value.nbt"));
        assertCode(ApiErrorCodes.NBT_LIMIT_EXCEEDED, () -> store(failing(NbtBinaryException.Reason.LIMIT)).read("value.nbt"));
        assertCode(ApiErrorCodes.NBT_FILE_TOO_LARGE, () -> store(failing(NbtBinaryException.Reason.FILE_SIZE)).read("value.nbt"));
        assertCode(ApiErrorCodes.UNSUPPORTED_CAPABILITY, () -> store(failing(NbtBinaryException.Reason.UNSUPPORTED)).read("value.nbt"));
    }

    @Test
    void preservesExistingDataWhenAtomicReplacementFails() throws Exception {
        NbtFileStore normal = store(new FixtureCodec());
        normal.write("value.nbt", VALUE);
        NbtFileStore failing = new NbtFileStore(dataRoot(), new FixtureCodec(), NbtBinaryLimits.DEFAULT,
                (source, target) -> {
                    throw new AtomicMoveNotSupportedException(source.toString(), target.toString(), "forced");
                });

        assertCode(ApiErrorCodes.NBT_ATOMIC_WRITE_FAILED, () -> failing.write("value.nbt", VALUE));
        assertArrayEquals(FixtureCodec.BYTES, Files.readAllBytes(dataRoot().resolve("value.nbt")));
    }

    @Test
    void rejectsSymlinkParentsAndLeaves() throws Exception {
        NbtFileStore store = store(new FixtureCodec());
        Path outside = tempDir.resolve("outside");
        Files.createDirectories(outside);
        Files.createDirectories(dataRoot());
        try {
            Files.createSymbolicLink(dataRoot().resolve("link"), outside);
            Files.createSymbolicLink(dataRoot().resolve("leaf.nbt"), outside.resolve("leaf.nbt"));
        } catch (UnsupportedOperationException | IOException error) {
            assumeTrue(false, "symbolic links are not available for this test process");
            return;
        }

        assertCode(ApiErrorCodes.NBT_PATH_FORBIDDEN, () -> store.write("link/escape.nbt", VALUE));
        assertCode(ApiErrorCodes.NBT_PATH_FORBIDDEN, () -> store.read("leaf.nbt"));
    }

    @Test
    void rejectsWindowsJunctionParents() throws Exception {
        assumeTrue(System.getProperty("os.name").toLowerCase(java.util.Locale.ROOT).contains("win"),
                "junctions are a Windows filesystem feature");
        NbtFileStore store = store(new FixtureCodec());
        Path outside = tempDir.resolve("outside");
        Files.createDirectories(outside);
        Files.createDirectories(dataRoot());
        Path junction = dataRoot().resolve("junction");
        Process process = new ProcessBuilder("cmd", "/c", "mklink", "/J", junction.toString(), outside.toString())
                .redirectErrorStream(true)
                .start();
        int exitCode = process.waitFor();
        assumeTrue(exitCode == 0, "junction creation is not available for this test process");

        assertCode(ApiErrorCodes.NBT_PATH_FORBIDDEN, () -> store.write("junction/escape.nbt", VALUE));
    }

    @Test
    void facadeMapsUnsupportedCodecToCapabilityErrorEndToEnd() throws Exception {
        DefaultNbtFacade unsupported = new DefaultNbtFacade(dataRoot(), NbtBinaryCodec.unsupported());

        // Reading a missing file never invokes the codec and returns null regardless of codec support.
        assertNull(unsupported.read("anywhere/missing.nbt"));

        // To exercise the codec on read, a real file must exist first. Seed it with a working codec,
        // then read it back through the unsupported codec to prove decode failure surfaces as a
        // capability error rather than a raw exception.
        new DefaultNbtFacade(dataRoot(), new FixtureCodec()).write("anywhere/value.nbt", VALUE);

        ApiInvocationException readError = assertThrows(ApiInvocationException.class,
                () -> unsupported.read("anywhere/value.nbt"));
        assertEquals(ApiErrorCodes.UNSUPPORTED_CAPABILITY, readError.code());
        assertEquals("nbt-binary-io", readError.details().get("requiredCapability"));
        assertEquals("read", readError.details().get("operation"));

        ApiInvocationException writeError = assertThrows(ApiInvocationException.class,
                () -> unsupported.write("anywhere/other.nbt", VALUE));
        assertEquals(ApiErrorCodes.UNSUPPORTED_CAPABILITY, writeError.code());
        assertEquals("nbt-binary-io", writeError.details().get("requiredCapability"));
        assertEquals("write", writeError.details().get("operation"));
    }

    private NbtFileStore store(NbtBinaryCodec codec) {
        return new NbtFileStore(dataRoot(), codec);
    }

    private Path dataRoot() {
        return tempDir.resolve("game").resolve("nekojs").resolve("data");
    }

    private NbtBinaryCodec failing(NbtBinaryException.Reason reason) throws Exception {
        Files.createDirectories(dataRoot());
        Files.write(dataRoot().resolve("value.nbt"), FixtureCodec.BYTES);
        return new NbtBinaryCodec() {
            @Override
            public NbtValue.CompoundValue decodeCompressed(byte[] compressed, NbtBinaryLimits limits) throws NbtBinaryException {
                throw new NbtBinaryException(reason, "fixture failure");
            }

            @Override
            public byte[] encodeCompressed(NbtValue.CompoundValue root, NbtBinaryLimits limits) throws NbtBinaryException {
                throw new NbtBinaryException(reason, "fixture failure");
            }
        };
    }

    private static void assertCode(String expected, ThrowingOperation operation) {
        ApiInvocationException error = assertThrows(ApiInvocationException.class, operation::run);
        assertEquals(expected, error.code());
    }

    private static final class FixtureCodec implements NbtBinaryCodec {
        private static final byte[] BYTES = {31, -117, 8, 0};

        @Override
        public NbtValue.CompoundValue decodeCompressed(byte[] compressed, NbtBinaryLimits limits) {
            return VALUE;
        }

        @Override
        public byte[] encodeCompressed(NbtValue.CompoundValue root, NbtBinaryLimits limits) {
            assertEquals(VALUE, root);
            return BYTES.clone();
        }
    }

    @FunctionalInterface
    private interface ThrowingOperation {
        void run() throws Exception;
    }
}
