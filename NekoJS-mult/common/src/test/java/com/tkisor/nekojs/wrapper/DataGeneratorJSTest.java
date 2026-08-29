package com.tkisor.nekojs.wrapper;

import com.google.gson.JsonObject;
import com.tkisor.nekojs.platform.Platform;
import com.tkisor.nekojs.testfixture.TestPlatformInit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DataGeneratorJSTest {
    private Path root;

    @BeforeAll
    static void initPlatform() {
        TestPlatformInit.ensureInitialized();
    }

    @BeforeEach
    void setUp() throws Exception {
        root = Platform.getGameDir().resolve("nekojs").resolve("test-data-" + System.nanoTime());
        Files.createDirectories(root);
    }

    @AfterEach
    void tearDown() throws Exception {
        deleteRecursively(root);
    }

    @Test
    void writesJsonToNestedPathsAndReadsItBack() throws Exception {
        DataGeneratorJS generator = new DataGeneratorJS(root);

        generator.json("minecraft/loot_tables/blocks/stone.json", "{\"type\":\"minecraft:block\"}");

        Path file = root.resolve("minecraft/loot_tables/blocks/stone.json");
        assertTrue(Files.isRegularFile(file));
        assertEquals("{\"type\":\"minecraft:block\"}", Files.readString(file));
        assertEquals("minecraft:block", generator.getJson("minecraft/loot_tables/blocks/stone.json")
                .getAsJsonObject().get("type").getAsString());
    }

    @Test
    void writesTextContent() throws Exception {
        DataGeneratorJS generator = new DataGeneratorJS(root);

        generator.text("nekojs/hello.txt", "hello");
        generator.add("nekojs/second.txt", "second");

        assertEquals("hello", Files.readString(root.resolve("nekojs/hello.txt")));
        assertEquals("second", Files.readString(root.resolve("nekojs/second.txt")));
    }

    @Test
    void getJsonReturnsNullForMissingFile() {
        DataGeneratorJS generator = new DataGeneratorJS(root);

        assertNull(generator.getJson("nekojs/missing.json"));
    }

    @Test
    void rejectsAbsoluteAndTraversingPaths() {
        DataGeneratorJS generator = new DataGeneratorJS(root);

        assertThrows(IllegalArgumentException.class, () -> generator.json("../escape.json", "{}"));
        assertThrows(IllegalArgumentException.class, () -> generator.text("a/../../escape.txt", "x"));
    }

    @Test
    void acceptsJsObjectsAsJsonInput() throws Exception {
        DataGeneratorJS generator = new DataGeneratorJS(root);
        JsonObject obj = new JsonObject();
        obj.addProperty("enabled", true);

        generator.json("minecraft/settings.json", obj);

        JsonObject read = generator.getJson("minecraft/settings.json").getAsJsonObject();
        assertEquals(true, read.get("enabled").getAsBoolean());
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
