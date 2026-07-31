package com.tkisor.nekojs.wrapper;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LangGeneratorJSTest {
    @TempDir
    Path tempDir;

    @Test
    void writesCollectedEntriesToLangJson() throws Exception {
        LangGeneratorJS generator = new LangGeneratorJS("en_us");
        generator.add("minecraft:item.foo", "Foo");
        generator.addAll(Map.of("minecraft:item.bar", "Bar"));

        generator.writeTo(tempDir, "en_us");

        Path file = tempDir.resolve("lang/en_us.json");
        assertTrue(Files.isRegularFile(file));
        var json = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
        assertEquals("Foo", json.get("minecraft:item.foo").getAsString());
        assertEquals("Bar", json.get("minecraft:item.bar").getAsString());
    }

    @Test
    void mergesWithExistingEntriesKeepingOldKeys() throws Exception {
        Path langDir = tempDir.resolve("lang");
        Files.createDirectories(langDir);
        Files.writeString(langDir.resolve("en_us.json"), "{\"minecraft:item.existing\":\"Old\"}");

        LangGeneratorJS generator = new LangGeneratorJS("en_us");
        generator.add("minecraft:item.foo", "Foo");
        generator.writeTo(tempDir, "en_us");

        var json = JsonParser.parseString(Files.readString(langDir.resolve("en_us.json"))).getAsJsonObject();
        assertEquals("Old", json.get("minecraft:item.existing").getAsString());
        assertEquals("Foo", json.get("minecraft:item.foo").getAsString());
    }

    @Test
    void skipsWriteWhenNoEntries() {
        LangGeneratorJS generator = new LangGeneratorJS("en_us");

        generator.writeTo(tempDir, "en_us");

        assertTrue(!Files.exists(tempDir.resolve("lang/en_us.json")));
    }
}
