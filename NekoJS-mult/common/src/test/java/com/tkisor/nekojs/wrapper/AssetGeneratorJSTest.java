package com.tkisor.nekojs.wrapper;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import graal.graalvm.polyglot.Value;
import com.tkisor.nekojs.platform.Platform;
import com.tkisor.nekojs.testfixture.TestPlatformInit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link AssetGeneratorJS}（绑定名 {@code Assets}）。
 *
 * <p>All writes go through a temporary root inside the injected platform game dir, so
 * {@link com.tkisor.nekojs.core.fs.NekoJSPaths#get()} resolves to the test game dir.
 */
class AssetGeneratorJSTest {
    private static Path gameDir;
    private Path root;
    private AssetGeneratorJS assets;

    @BeforeAll
    static void initPlatform() {
        TestPlatformInit.ensureInitialized();
        gameDir = Platform.getGameDir();
    }

    @BeforeEach
    void setUp() throws Exception {
        root = gameDir.resolve("nekojs").resolve("test-asset-gen-" + System.nanoTime());
        Files.createDirectories(root);
        assets = new AssetGeneratorJS(root);
    }

    @AfterEach
    void tearDown() throws Exception {
        deleteRecursively(root);
    }

    @Test
    void blockStateVariantsFormWritesParsableJson() throws Exception {
        JsonObject variant = new JsonObject();
        variant.addProperty("model", "mymod:block/my_block");
        JsonObject variants = new JsonObject();
        variants.add("", variant);
        JsonObject state = new JsonObject();
        state.add("variants", variants);

        assets.blockState("mymod:my_block", Value.asValue(state));

        Path file = root.resolve("mymod").resolve("blockstates").resolve("my_block.json");
        assertTrue(Files.isRegularFile(file), "expected blockstate at " + file);
        JsonObject json = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
        assertEquals("mymod:block/my_block",
                json.getAsJsonObject("variants").getAsJsonObject("").get("model").getAsString());
    }

    @Test
    void blockStateMultipartFormWritesParsableJson() throws Exception {
        JsonObject multipartEntry = new JsonObject();
        multipartEntry.addProperty("model", "mymod:block/my_block");
        JsonArray multipart = new JsonArray();
        multipart.add(multipartEntry);
        JsonObject state = new JsonObject();
        state.add("multipart", multipart);

        assets.blockState("mymod:my_block", Value.asValue(state));

        Path file = root.resolve("mymod").resolve("blockstates").resolve("my_block.json");
        JsonObject json = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
        assertTrue(json.has("multipart") && json.get("multipart").isJsonArray());
    }

    @Test
    void blockStateStringShorthandBuildsSingleEmptyVariant() throws Exception {
        assets.blockState("mymod:my_block", Value.asValue("mymod:block/my_block"));

        Path file = root.resolve("mymod").resolve("blockstates").resolve("my_block.json");
        JsonObject json = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
        assertEquals("mymod:block/my_block",
                json.getAsJsonObject("variants").getAsJsonObject("").get("model").getAsString());
    }

    @Test
    void blockStateAcceptsJsonStringInput() throws Exception {
        assets.blockState("mymod:my_block", Value.asValue("{\"variants\":{\"\":{\"model\":\"mymod:block/my_block\"}}}"));

        Path file = root.resolve("mymod").resolve("blockstates").resolve("my_block.json");
        JsonObject json = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
        assertEquals("mymod:block/my_block",
                json.getAsJsonObject("variants").getAsJsonObject("").get("model").getAsString());
    }

    @Test
    void blockStateRejectsObjectWithoutVariantsOrMultipart() {
        JsonObject broken = new JsonObject();
        broken.addProperty("model", "mymod:block/my_block");

        assertThrows(IllegalArgumentException.class, () -> assets.blockState("mymod:my_block", Value.asValue(broken)));
        assertThrows(IllegalArgumentException.class, () -> assets.blockState("mymod:my_block", Value.asValue("{\"model\":\"x\"}")));

        JsonObject both = new JsonObject();
        both.add("variants", new JsonObject());
        both.add("multipart", new JsonArray());
        assertThrows(IllegalArgumentException.class, () -> assets.blockState("mymod:my_block", Value.asValue(both)));
    }

    @Test
    void blockModelWritesCubeAllAndAppliesTextureShorthand() throws Exception {
        JsonObject model = new JsonObject();
        model.addProperty("parent", "minecraft:block/cube_all");
        JsonObject textures = new JsonObject();
        textures.addProperty("all", "my_tex");              // shorthand: no ':' and no '/'
        textures.addProperty("side", "mymod:block/my_tex"); // explicit reference: untouched
        textures.addProperty("top", "block/stone");        // contains '/': untouched
        textures.addProperty("bottom", "minecraft:block/stone"); // contains ':': untouched
        model.add("textures", textures);

        assets.blockModel("mymod:my_block", Value.asValue(model));

        Path file = root.resolve("mymod").resolve("models").resolve("block").resolve("my_block.json");
        assertTrue(Files.isRegularFile(file), "expected block model at " + file);
        JsonObject json = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
        JsonObject written = json.getAsJsonObject("textures");
        assertEquals("mymod:block/my_tex", written.get("all").getAsString());
        assertEquals("mymod:block/my_tex", written.get("side").getAsString());
        assertEquals("block/stone", written.get("top").getAsString());
        assertEquals("minecraft:block/stone", written.get("bottom").getAsString());
    }

    @Test
    void itemModelWritesItemModelWithItemShorthand() throws Exception {
        assets.itemModel("mymod:my_item",
                Value.asValue("{\"parent\":\"minecraft:item/generated\",\"textures\":{\"layer0\":\"my_item\"}}"));

        Path file = root.resolve("mymod").resolve("models").resolve("item").resolve("my_item.json");
        assertTrue(Files.isRegularFile(file), "expected item model at " + file);
        JsonObject json = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
        assertEquals("mymod:item/my_item", json.getAsJsonObject("textures").get("layer0").getAsString());
    }

    @Test
    void defaultNamespaceAndExplicitSubdirectories() throws Exception {
        assets.blockState("plain_block", Value.asValue("minecraft:block/plain_block"));

        Path file = root.resolve("minecraft").resolve("blockstates").resolve("plain_block.json");
        assertTrue(Files.isRegularFile(file), "default namespace should write under minecraft/, got " + file);

        assets.blockModel("mymod:custom/nested/my_block", Value.asValue(new JsonObject()));
        assertTrue(Files.isRegularFile(root.resolve("mymod").resolve("models")
                .resolve("block").resolve("custom").resolve("nested").resolve("my_block.json")));
    }

    @Test
    void textureWritesValidPlaceholderPng() throws Exception {
        assets.texture("mymod:block/my_block");   // explicit block/ directory
        assets.texture("mymod:my_thing");         // no '/': defaults to block/
        assets.texture("mymod:my_item", "item");  // explicit kind overload

        assertPlaceholderPng(root.resolve("mymod").resolve("textures")
                .resolve("block").resolve("my_block.png"));
        assertPlaceholderPng(root.resolve("mymod").resolve("textures")
                .resolve("block").resolve("my_thing.png"));
        assertPlaceholderPng(root.resolve("mymod").resolve("textures")
                .resolve("item").resolve("my_item.png"));
    }

    @Test
    void textureRejectsKindConflictWithDirectoryPath() {
        assertThrows(IllegalArgumentException.class, () -> assets.texture("mymod:block/x", "item"));
        assertThrows(IllegalArgumentException.class, () -> assets.texture("mymod:x", "banner"));
    }

    @Test
    void rejectsInvalidAndTraversingIds() throws Exception {
        assertThrows(IllegalArgumentException.class, () -> assets.blockState("Foo", Value.asValue(new JsonObject())));
        assertThrows(IllegalArgumentException.class, () -> assets.blockState("mymod:My_Block", Value.asValue(new JsonObject())));
        assertThrows(IllegalArgumentException.class, () -> assets.blockModel("mymod:../evil", Value.asValue(new JsonObject())));
        assertThrows(IllegalArgumentException.class,
                () -> assets.blockModel("mymod:block/../../evil", Value.asValue(new JsonObject())));
        assertThrows(IllegalArgumentException.class, () -> assets.itemModel("a:b:c", Value.asValue(new JsonObject())));
        assertThrows(IllegalArgumentException.class, () -> assets.itemModel("mymod:", Value.asValue(new JsonObject())));
        assertThrows(IllegalArgumentException.class, () -> assets.texture("mymod:../evil.png"));
        assertThrows(IllegalArgumentException.class, () -> assets.blockState(null, Value.asValue(new JsonObject())));

        assertTrue(Files.walk(root).noneMatch(Files::isRegularFile), "no file may be written for invalid ids");
    }

    /** 校验占位 PNG：PNG 魔数、16x16 尺寸、洋红色像素。 */
    private static void assertPlaceholderPng(Path file) throws IOException {
        assertTrue(Files.isRegularFile(file), "expected placeholder texture at " + file);
        byte[] bytes = Files.readAllBytes(file);
        assertEquals(0x89, bytes[0] & 0xFF, "PNG signature byte 0");
        assertEquals('P', bytes[1] & 0xFF, "PNG signature byte 1");
        assertEquals('N', bytes[2] & 0xFF, "PNG signature byte 2");
        assertEquals('G', bytes[3] & 0xFF, "PNG signature byte 3");

        BufferedImage image = ImageIO.read(file.toFile());
        assertEquals(16, image.getWidth(), "placeholder width");
        assertEquals(16, image.getHeight(), "placeholder height");
        assertEquals(0xFFFF00FF, image.getRGB(0, 0) & 0xFFFFFFFF, "placeholder should be magenta");
        assertEquals(0xFFFF00FF, image.getRGB(15, 15) & 0xFFFFFFFF, "placeholder should be magenta");
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
