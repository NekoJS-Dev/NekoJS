package com.tkisor.nekojs.platform.nbt;

import com.tkisor.nekojs.api.data.NbtValue;
import com.tkisor.nekojs.api.error.ApiErrorCodes;
import com.tkisor.nekojs.api.error.ApiInvocationException;
import com.tkisor.nekojs.api.nbt.NbtBinaryException;
import com.tkisor.nekojs.api.nbt.NbtBinaryLimits;
import com.tkisor.nekojs.core.api.facade.DefaultNbtFacade;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.DoubleTag;
import net.minecraft.nbt.LongArrayTag;
import net.minecraft.nbt.NbtIo;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("nbt-smoke")
class NeoForgeNbtBinaryCodecTest {
    private static final String STANDARD_FIXTURE =
            "H4sIAAAAAAAEAONiYOBgYMlLzE0FkqnZ+cwMrMn5pXklDAwMTAwAOXKi+x0AAAA=";
    private final NeoForgeNbtBinaryCodec codec = NeoForgeNbtBinaryCodec.INSTANCE;

    @TempDir
    Path tempDir;

    @Test
    void persistsThroughProductionFacadeAndNormalizesFilesystemErrors() throws Exception {
        Path dataRoot = tempDir.resolve("game").resolve("nekojs").resolve("data");
        NbtValue.CompoundValue expected = portableRoot();
        DefaultNbtFacade writer = new DefaultNbtFacade(dataRoot, codec);

        assertNull(writer.read("smoke/missing.nbt"));
        writer.write("smoke/player.nbt", expected);
        Path file = dataRoot.resolve("smoke/player.nbt");
        assertTrue(Files.isRegularFile(file));

        DefaultNbtFacade afterRestart = new DefaultNbtFacade(dataRoot, codec);
        assertEquals(expected, afterRestart.read("smoke/player.nbt"));
        assertCode(ApiErrorCodes.NBT_PATH_FORBIDDEN, () -> afterRestart.read("../escape.nbt"));
        assertCode(ApiErrorCodes.TYPE_MISMATCH,
                () -> afterRestart.write("smoke/not-compound.nbt", NbtValue.intValue(1)));

        Files.write(file, new byte[] {1, 2, 3, 4});
        assertCode(ApiErrorCodes.INVALID_NBT, () -> afterRestart.read("smoke/player.nbt"));
    }

    @Test
    void roundTripsEveryPortableTagKind() throws Exception {
        NbtValue.CompoundValue expected = portableRoot();

        byte[] compressed = codec.encodeCompressed(expected, NbtBinaryLimits.DEFAULT);

        assertEquals(expected, codec.decodeCompressed(compressed, NbtBinaryLimits.DEFAULT));
    }

    @Test
    void readsPlatformIndependentStandardBinaryFixture() throws Exception {
        NbtValue.CompoundValue expected = NbtValue.compound(Map.of(
                "name", NbtValue.string("neko"),
                "count", NbtValue.intValue(2)));

        assertEquals(expected, codec.decodeCompressed(
                Base64.getDecoder().decode(STANDARD_FIXTURE), NbtBinaryLimits.DEFAULT));
    }

    @Test
    void rejectsCompressedInputAboveTheConfiguredLimit() {
        NbtBinaryException error = assertThrows(NbtBinaryException.class,
                () -> codec.decodeCompressed(new byte[5], new NbtBinaryLimits(4, 1024)));

        assertEquals(NbtBinaryException.Reason.FILE_SIZE, error.reason());
    }

    @Test
    void rejectsValuesOutsideBinaryStringAndPortableArrayLimits() {
        assertLimit(NbtValue.compound(Map.of("string", NbtValue.string("a".repeat(65_536)))));
        assertLimit(NbtValue.compound(Map.of("array", NbtValue.byteArray(new byte[10_001]))));
    }

    @Test
    void appliesPortableDepthFromRootDepthZero() throws Exception {
        NbtValue.CompoundValue maximum = nestedRoot(64);
        assertEquals(maximum, codec.decodeCompressed(
                codec.encodeCompressed(maximum, NbtBinaryLimits.DEFAULT), NbtBinaryLimits.DEFAULT));
        assertLimit(nestedRoot(65));
    }

    @Test
    void rejectsNonFiniteNumbersAndLongArraysFromNativeData() throws Exception {
        CompoundTag nonFinite = new CompoundTag();
        nonFinite.put("bad", DoubleTag.valueOf(Double.NaN));
        assertInvalid(nativeCompressed(nonFinite));

        CompoundTag longArray = new CompoundTag();
        longArray.put("unsupported", new LongArrayTag(new long[] {1L}));
        assertInvalid(nativeCompressed(longArray));
    }

    @Test
    void rejectsEmptyNativeListsWithNonEndElementTypes() throws Exception {
        assertInvalid(compressedRootWithEmptyTypedList());
    }

    private void assertInvalid(byte[] compressed) {
        NbtBinaryException error = assertThrows(NbtBinaryException.class,
                () -> codec.decodeCompressed(compressed, NbtBinaryLimits.DEFAULT));
        assertEquals(NbtBinaryException.Reason.INVALID, error.reason());
    }

    private void assertLimit(NbtValue.CompoundValue value) {
        NbtBinaryException error = assertThrows(NbtBinaryException.class,
                () -> codec.encodeCompressed(value, NbtBinaryLimits.DEFAULT));
        assertEquals(NbtBinaryException.Reason.LIMIT, error.reason());
    }

    private static void assertCode(String expected, ThrowingOperation operation) {
        ApiInvocationException error = assertThrows(ApiInvocationException.class, operation::run);
        assertEquals(expected, error.code());
    }

    private static byte[] nativeCompressed(CompoundTag root) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        NbtIo.writeCompressed(root, output);
        return output.toByteArray();
    }

    /**
     * Hand-builds gzip'd compressed NBT whose root compound holds a single empty
     * list with a non-END element type (TAG_Byte, count 0). The native tag APIs
     * offer no portable way to construct that shape, so the bytes are written
     * directly with JDK gzip only, which works on every platform this test
     * compiles against.
     */
    private static byte[] compressedRootWithEmptyTypedList() throws Exception {
        byte[] payload = {
                0x0A, 0x00, 0x00,                    // root TAG_Compound named ""
                0x09, 0x00, 0x03, 'b', 'a', 'd',     // entry "bad": TAG_List
                0x01,                                // element type TAG_Byte
                0x00, 0x00, 0x00, 0x00,              // count = 0
                0x00                                 // root TAG_END
        };
        ByteArrayOutputStream compressed = new ByteArrayOutputStream();
        try (GZIPOutputStream gzip = new GZIPOutputStream(compressed)) {
            gzip.write(payload);
        }
        return compressed.toByteArray();
    }

    private static NbtValue.CompoundValue portableRoot() {
        Map<String, NbtValue> nested = new LinkedHashMap<>();
        nested.put("byte", NbtValue.byteValue((byte) 1));
        nested.put("short", NbtValue.shortValue((short) 2));
        nested.put("int", NbtValue.intValue(3));
        nested.put("long", NbtValue.longValue(4L));
        nested.put("float", NbtValue.floatValue(5.5F));
        nested.put("double", NbtValue.doubleValue(6.5D));
        nested.put("string", NbtValue.string("neko"));
        nested.put("bytes", NbtValue.byteArray(new byte[] {1, -2}));
        nested.put("ints", NbtValue.intArray(new int[] {3, -4}));
        nested.put("list", NbtValue.list(List.of(NbtValue.intValue(7), NbtValue.intValue(8))));
        nested.put("empty", NbtValue.list(List.of()));
        nested.put("compound", NbtValue.compound(Map.of("value", NbtValue.string("nested"))));
        return NbtValue.compound(nested);
    }

    private static NbtValue.CompoundValue nestedRoot(int valueDepth) {
        NbtValue value = NbtValue.intValue(1);
        for (int depth = 0; depth < valueDepth; depth++) {
            value = NbtValue.compound(Map.of("child", value));
        }
        return (NbtValue.CompoundValue) value;
    }

    @FunctionalInterface
    private interface ThrowingOperation {
        void run() throws Exception;
    }
}
