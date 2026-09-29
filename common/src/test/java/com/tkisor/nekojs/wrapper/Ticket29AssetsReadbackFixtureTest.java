package com.tkisor.nekojs.wrapper;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.tkisor.nekojs.core.NekoSharedHostAccess;
import com.tkisor.nekojs.core.config.SandboxConfig;
import com.tkisor.nekojs.core.fs.ClassFilter;
import com.tkisor.nekojs.platform.Platform;
import com.tkisor.nekojs.testfixture.TestPlatformInit;
import graal.graalvm.polyglot.Context;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.zip.CRC32;
import java.util.zip.DataFormatException;
import java.util.zip.Inflater;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Ticket 29 AC3 readback fixture for the script-facing asset members: drives
 * {@code Assets.blockState/blockModel/itemModel/texture} from real GraalJS script code
 * (the same host-method boundary production scripts go through — the members take
 * {@code Value} parameters, so JS object/array/string arguments must survive the
 * polyglot conversion exactly), then reads the produced files back from disk and pins
 * the deterministic outputs:
 *
 * <ul>
 *   <li>JSON structure per member, including parameter normalization (plain-string
 *       model shorthand, JSON-string input), default namespace/path completion
 *       ({@code plain_block} -> {@code minecraft:plain_block}, texture {@code block/}
 *       default, texture shorthand {@code my_tex} -> {@code <ns>:block/my_tex});</li>
 *   <li>the placeholder PNG's exact identity: signature, 16x16 8-bit truecolor IHDR,
 *       chunk order and CRCs, and the full decompressed magenta pixel payload (with a
 *       SHA-256 content constant). The zlib-compressed IDAT bytes themselves are
 *       {@code java.util.zip.Deflater} (native zlib) output — stable within one JVM
 *       build, but not a cross-build contract — so the pinned identity is the
 *       zlib-independent structure above plus byte-equality of independently written
 *       placeholders (per-run determinism), not a whole-file hash.</li>
 * </ul>
 *
 * <p>Golden files are deliberately not introduced: readback assertions pin the same
 * determinism without a second artifact to keep in sync.
 */
class Ticket29AssetsReadbackFixtureTest {

    /** SHA-256 of the expected decompressed placeholder pixel payload (16 rows x (1 filter + 16x3 magenta)). */
    private static final String PLACEHOLDER_PIXEL_PAYLOAD_SHA256 =
            "e0d80d942cd2c76219456f9cc1d1ebbe6d2dcab5dfca4cf00fec033ea7e2bd5a";

    private static final byte[] PNG_SIGNATURE = {
            (byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'};

    private static Path base;
    private Path root;

    @BeforeAll
    static void initPlatform() {
        TestPlatformInit.ensureInitialized();
        base = Platform.getGameDir().resolve("nekojs").resolve("test-29-assets-readback-" + System.nanoTime());
    }

    @BeforeEach
    void setUp() throws IOException {
        root = Files.createDirectories(base.resolve("case-" + System.nanoTime()));
    }

    @AfterEach
    void tearDown() throws IOException {
        deleteRecursively(base);
    }

    /** Real GraalJS context wired like {@code NekoSandboxFactory.build}, member name = production binding name. */
    private Context newScriptContext() {
        NekoSharedHostAccess hostAccess = new NekoSharedHostAccess(List.of());
        ClassFilter classFilter = new ClassFilter(SandboxConfig.defaultConfig());
        Context context = Context.newBuilder("js")
                .allowExperimentalOptions(true)
                .allowHostAccess(hostAccess.get())
                .allowHostClassLookup(classFilter)
                .allowCreateProcess(false)
                .allowValueSharing(true)
                .option("js.foreign-object-prototype", "true")
                .option("js.nashorn-compat", "true")
                .option("js.ecmascript-version", "latest")
                .option("js.strict", "true")
                .build();
        context.getBindings("js").putMember("Assets", new AssetGeneratorJS(root));
        return context;
    }

    /* ================= blockState ================= */

    @Test
    void blockStateFormsReadBackAsDeterministicJson() throws IOException {
        try (Context context = newScriptContext()) {
            // plain-string shorthand: single empty variant, model id used verbatim
            context.eval("js", "Assets.blockState('mymod:my_block', 'mymod:block/my_block')");
            // variants object + default namespace completion: plain_block -> minecraft:plain_block
            context.eval("js", "Assets.blockState('plain_block', "
                    + "{ variants: { '': { model: 'minecraft:block/plain_block' } } })");
            // multipart array form with a nested options object
            context.eval("js", "Assets.blockState('mymod:multi_part', "
                    + "{ multipart: [ { model: 'mymod:block/a', apply: { x: 90 } }, { model: 'mymod:block/b' } ] })");
            // pre-encoded JSON-string input
            context.eval("js", "Assets.blockState('mymod:from_json', "
                    + "'{\"variants\":{\"\":{\"model\":\"mymod:block/from_json\"}}}')");
        }

        // String shorthand: exact on-disk serialization form (deterministic gson output, member order kept).
        Path shorthand = root.resolve("mymod/blockstates/my_block.json");
        assertTrue(Files.isRegularFile(shorthand), "expected blockstate at " + shorthand);
        assertEquals("{\"variants\":{\"\":{\"model\":\"mymod:block/my_block\"}}}",
                Files.readString(shorthand), "string shorthand must serialize to the single empty-variant form");

        // Default namespace: plain id lands under minecraft/, structure fully pinned.
        Path defaulted = root.resolve("minecraft/blockstates/plain_block.json");
        assertTrue(Files.isRegularFile(defaulted), "default namespace must write under minecraft/: " + defaulted);
        JsonObject variantsForm = JsonParser.parseString(Files.readString(defaulted)).getAsJsonObject();
        assertEquals(List.of("variants"), List.copyOf(variantsForm.keySet()));
        JsonObject emptyVariant = variantsForm.getAsJsonObject("variants").getAsJsonObject("");
        assertEquals(List.of("model"), List.copyOf(emptyVariant.keySet()));
        assertEquals("minecraft:block/plain_block", emptyVariant.get("model").getAsString());

        // Multipart: member set and nested structure preserved verbatim.
        Path multipart = root.resolve("mymod/blockstates/multi_part.json");
        JsonObject multipartForm = JsonParser.parseString(Files.readString(multipart)).getAsJsonObject();
        assertEquals(List.of("multipart"), List.copyOf(multipartForm.keySet()));
        var parts = multipartForm.getAsJsonArray("multipart");
        assertEquals(2, parts.size());
        JsonObject first = parts.get(0).getAsJsonObject();
        assertEquals(List.of("model", "apply"), List.copyOf(first.keySet()));
        assertEquals("mymod:block/a", first.get("model").getAsString());
        assertEquals(90, first.getAsJsonObject("apply").get("x").getAsInt());
        assertEquals("mymod:block/b", parts.get(1).getAsJsonObject().get("model").getAsString());

        // JSON-string input parses to the same structure as the equivalent object input.
        Path fromJson = root.resolve("mymod/blockstates/from_json.json");
        JsonObject jsonForm = JsonParser.parseString(Files.readString(fromJson)).getAsJsonObject();
        assertEquals("mymod:block/from_json",
                jsonForm.getAsJsonObject("variants").getAsJsonObject("").get("model").getAsString());
    }

    /* ================= blockModel / itemModel ================= */

    @Test
    void blockModelReadBackPinsTextureShorthandCompletionAndSubdirectories() throws IOException {
        try (Context context = newScriptContext()) {
            context.eval("js", "Assets.blockModel('mymod:custom/nested/my_block', {"
                    + " parent: 'minecraft:block/cube_all',"
                    + " textures: {"
                    + "   all: 'my_tex',"                      // shorthand: completed below
                    + "   side: 'mymod:block/explicit',"       // already namespaced: untouched
                    + "   top: 'block/stone',"                 // already has a directory: untouched
                    + "   bottom: 'minecraft:block/stone'"     // already namespaced: untouched
                    + " } })");
        }

        Path file = root.resolve("mymod/models/block/custom/nested/my_block.json");
        assertTrue(Files.isRegularFile(file), "subdirectory id must land under models/block/custom/nested/: " + file);
        JsonObject model = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
        assertEquals(List.of("parent", "textures"), List.copyOf(model.keySet()), "no members may be added or dropped");
        assertEquals("minecraft:block/cube_all", model.get("parent").getAsString());

        JsonObject textures = model.getAsJsonObject("textures");
        assertEquals(List.of("all", "side", "top", "bottom"), List.copyOf(textures.keySet()),
                "texture keys keep script insertion order");
        assertEquals("mymod:block/my_tex", textures.get("all").getAsString(),
                "bare shorthand completes to <ns>:block/<value>");
        assertEquals("mymod:block/explicit", textures.get("side").getAsString());
        assertEquals("block/stone", textures.get("top").getAsString());
        assertEquals("minecraft:block/stone", textures.get("bottom").getAsString());
    }

    @Test
    void itemModelReadBackPinsItemKindCompletion() throws IOException {
        try (Context context = newScriptContext()) {
            context.eval("js", "Assets.itemModel('mymod:my_item', {"
                    + " parent: 'minecraft:item/generated',"
                    + " textures: { layer0: 'my_item', layer1: 'mymod:item/explicit' } })");
        }

        Path file = root.resolve("mymod/models/item/my_item.json");
        assertTrue(Files.isRegularFile(file), "expected item model at " + file);
        JsonObject model = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
        JsonObject textures = model.getAsJsonObject("textures");
        assertEquals("mymod:item/my_item", textures.get("layer0").getAsString(),
                "bare shorthand completes to <ns>:item/<value> for item models");
        assertEquals("mymod:item/explicit", textures.get("layer1").getAsString());
    }

    /* ================= texture (placeholder PNG identity) ================= */

    @Test
    void placeholderTextureReadBackPinsDeterministicPngIdentity() throws IOException {
        try (Context context = newScriptContext()) {
            context.eval("js", "Assets.texture('mymod:block/my_block')");   // path with '/': used as-is
            context.eval("js", "Assets.texture('mymod:my_thing')");         // no '/': defaults to block/
            context.eval("js", "Assets.texture('mymod:my_item', 'item')");  // explicit kind overload
        }

        Path explicit = root.resolve("mymod/textures/block/my_block.png");
        Path defaulted = root.resolve("mymod/textures/block/my_thing.png");
        Path itemKind = root.resolve("mymod/textures/item/my_item.png");
        assertPlaceholderIdentity(explicit);
        assertPlaceholderIdentity(defaulted);
        assertPlaceholderIdentity(itemKind);

        byte[] first = Files.readAllBytes(explicit);
        assertArrayEquals(first, Files.readAllBytes(defaulted),
                "independently written placeholders must be byte-identical (per-run determinism)");
        assertArrayEquals(first, Files.readAllBytes(itemKind),
                "the kind overload writes the same placeholder payload");
    }

    /**
     * Pins the placeholder PNG identity from raw bytes: signature, chunk order (IHDR, IDAT,
     * IEND), per-chunk CRC validity, exact IHDR fields (16x16, 8-bit, truecolor RGB, no
     * compression/filter/interlace surprises), the full decompressed magenta pixel payload
     * (byte-for-byte plus a SHA-256 constant), and an empty IEND. Whole-file hashing is
     * intentionally not used: see the class javadoc for why the IDAT stream is not a
     * cross-JVM contract.
     */
    private static void assertPlaceholderIdentity(Path file) throws IOException {
        assertTrue(Files.isRegularFile(file), "expected placeholder texture at " + file);
        byte[] bytes = Files.readAllBytes(file);
        assertArrayEquals(PNG_SIGNATURE, java.util.Arrays.copyOfRange(bytes, 0, 8), "PNG signature");

        // Chunk walk: length(4) type(4) data(length) crc(4); every CRC must match type+data.
        java.util.List<String> chunkOrder = new java.util.ArrayList<>();
        byte[] idat = null;
        boolean idatSeen = false;
        int offset = 8;
        while (offset < bytes.length) {
            assertTrue(offset + 8 <= bytes.length, "truncated chunk header at " + offset);
            int length = readInt(bytes, offset);
            String type = new String(bytes, offset + 4, 4, java.nio.charset.StandardCharsets.US_ASCII);
            assertTrue(offset + 12 + length <= bytes.length, "truncated chunk " + type);
            byte[] data = java.util.Arrays.copyOfRange(bytes, offset + 8, offset + 8 + length);
            CRC32 crc = new CRC32();
            crc.update(bytes, offset + 4, 4 + length);
            assertEquals(readInt(bytes, offset + 8 + length), (int) crc.getValue(),
                    "chunk " + type + " CRC must be valid");
            chunkOrder.add(type);
            if (type.equals("IHDR")) {
                assertEquals(13, length, "IHDR data length");
                assertEquals(16, readInt(data, 0), "width");
                assertEquals(16, readInt(data, 4), "height");
                assertEquals(8, data[8] & 0xFF, "bit depth");
                assertEquals(2, data[9] & 0xFF, "color type: truecolor RGB");
                assertEquals(0, data[10] & 0xFF, "compression method");
                assertEquals(0, data[11] & 0xFF, "filter method");
                assertEquals(0, data[12] & 0xFF, "interlace method");
            } else if (type.equals("IDAT")) {
                assertTrue(!idatSeen, "IDAT appears exactly once");
                idatSeen = true;
                idat = data;
            } else if (type.equals("IEND")) {
                assertEquals(0, length, "IEND is empty");
            }
            offset += 12 + length;
        }
        assertEquals(offset, bytes.length, "no trailing garbage after IEND");
        assertEquals(java.util.List.of("IHDR", "IDAT", "IEND"), chunkOrder, "chunk order");
        assertNotNull(idat, "IDAT chunk present");

        // Decompressed payload: exact scanline bytes (filter 0 + magenta pixels), then the content hash.
        byte[] scanlines = inflate(idat);
        byte[] expected = expectedPlaceholderScanlines();
        assertEquals(expected.length, scanlines.length, "16 rows x (1 filter byte + 16x3 RGB) = 784 bytes");
        assertArrayEquals(expected, scanlines, "pixel payload is uniformly magenta with filter bytes 0");
        assertEquals(PLACEHOLDER_PIXEL_PAYLOAD_SHA256, sha256Hex(scanlines),
                "stable content hash of the placeholder pixel payload");
    }

    private static byte[] expectedPlaceholderScanlines() {
        byte[] scanlines = new byte[16 * (1 + 16 * 3)];
        for (int row = 0; row < 16; row++) {
            int base = row * (1 + 16 * 3);
            scanlines[base] = 0; // filter: none
            for (int col = 0; col < 16; col++) {
                int pixel = base + 1 + col * 3;
                scanlines[pixel] = (byte) 0xFF;     // R
                scanlines[pixel + 1] = 0;           // G
                scanlines[pixel + 2] = (byte) 0xFF; // B
            }
        }
        return scanlines;
    }

    private static byte[] inflate(byte[] zlibStream) {
        Inflater inflater = new Inflater();
        try {
            inflater.setInput(zlibStream);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[1024];
            while (!inflater.finished()) {
                int produced = inflater.inflate(buffer);
                if (produced == 0 && inflater.needsInput()) {
                    throw new IllegalStateException("truncated IDAT stream");
                }
                out.write(buffer, 0, produced);
            }
            return out.toByteArray();
        } catch (DataFormatException error) {
            throw new IllegalStateException("invalid IDAT stream: " + error.getMessage(), error);
        } finally {
            inflater.end();
        }
    }

    private static int readInt(byte[] source, int offset) {
        return ((source[offset] & 0xFF) << 24) | ((source[offset + 1] & 0xFF) << 16)
                | ((source[offset + 2] & 0xFF) << 8) | (source[offset + 3] & 0xFF);
    }

    private static String sha256Hex(byte[] data) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data));
        } catch (java.security.NoSuchAlgorithmException error) {
            throw new IllegalStateException("SHA-256 unavailable", error);
        }
    }

    private static void deleteRecursively(Path path) throws IOException {
        if (path == null || !Files.exists(path)) {
            return;
        }
        try (var stream = Files.walk(path)) {
            stream.sorted(Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (IOException ignored) {
                    // best-effort cleanup
                }
            });
        }
    }
}
