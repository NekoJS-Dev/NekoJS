package com.tkisor.nekojs.wrapper;

import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DataGeneratorJSTest {
    @TempDir
    Path tempDir;

    @Test
    void writesJsonToNestedPathsAndReadsItBack() throws Exception {
        DataGeneratorJS generator = new DataGeneratorJS(tempDir);

        generator.json("minecraft/loot_tables/blocks/stone.json", "{\"type\":\"minecraft:block\"}");

        Path file = tempDir.resolve("minecraft/loot_tables/blocks/stone.json");
        assertTrue(Files.isRegularFile(file));
        assertEquals("{\"type\":\"minecraft:block\"}", Files.readString(file));
        assertEquals("minecraft:block", generator.getJson("minecraft/loot_tables/blocks/stone.json")
                .getAsJsonObject().get("type").getAsString());
    }

    @Test
    void writesTextContent() throws Exception {
        DataGeneratorJS generator = new DataGeneratorJS(tempDir);

        generator.text("nekojs/hello.txt", "hello");
        generator.add("nekojs/second.txt", "second");

        assertEquals("hello", Files.readString(tempDir.resolve("nekojs/hello.txt")));
        assertEquals("second", Files.readString(tempDir.resolve("nekojs/second.txt")));
    }

    @Test
    void getJsonReturnsNullForMissingFile() {
        DataGeneratorJS generator = new DataGeneratorJS(tempDir);

        assertNull(generator.getJson("nekojs/missing.json"));
    }

    @Test
    void rejectsAbsoluteAndTraversingPaths() {
        DataGeneratorJS generator = new DataGeneratorJS(tempDir);

        assertThrows(IllegalArgumentException.class, () -> generator.json("../escape.json", "{}"));
        assertThrows(IllegalArgumentException.class, () -> generator.text("a/../../escape.txt", "x"));
    }

    @Test
    void acceptsJsObjectsAsJsonInput() throws Exception {
        DataGeneratorJS generator = new DataGeneratorJS(tempDir);
        JsonObject obj = new JsonObject();
        obj.addProperty("enabled", true);

        generator.json("minecraft/settings.json", obj);

        JsonObject read = generator.getJson("minecraft/settings.json").getAsJsonObject();
        assertEquals(true, read.get("enabled").getAsBoolean());
    }
}
