package com.tkisor.nekojs.wrapper;

import com.google.gson.JsonParser;
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
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LangGeneratorJSTest {
    private Path assetsRoot;

    @BeforeAll
    static void initPlatform() {
        TestPlatformInit.ensureInitialized();
    }

    @BeforeEach
    void setUp() throws Exception {
        assetsRoot = Platform.getGameDir().resolve("nekojs").resolve("test-lang-" + System.nanoTime());
        Files.createDirectories(assetsRoot);
    }

    @AfterEach
    void tearDown() throws Exception {
        deleteRecursively(assetsRoot);
    }

    @Test
    void writesCollectedEntriesToLangJson() throws Exception {
        LangGeneratorJS generator = new LangGeneratorJS("en_us");
        generator.add("minecraft:item.foo", "Foo");
        generator.addAll(Map.of("minecraft:item.bar", "Bar"));

        generator.writeTo(assetsRoot, "en_us");

        Path file = assetsRoot.resolve("lang/en_us.json");
        assertTrue(Files.isRegularFile(file));
        var json = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
        assertEquals("Foo", json.get("minecraft:item.foo").getAsString());
        assertEquals("Bar", json.get("minecraft:item.bar").getAsString());
    }

    @Test
    void mergesWithExistingEntriesKeepingOldKeys() throws Exception {
        Path langDir = assetsRoot.resolve("lang");
        Files.createDirectories(langDir);
        Files.writeString(langDir.resolve("en_us.json"), "{\"minecraft:item.existing\":\"Old\"}");

        LangGeneratorJS generator = new LangGeneratorJS("en_us");
        generator.add("minecraft:item.foo", "Foo");
        generator.writeTo(assetsRoot, "en_us");

        var json = JsonParser.parseString(Files.readString(langDir.resolve("en_us.json"))).getAsJsonObject();
        assertEquals("Old", json.get("minecraft:item.existing").getAsString());
        assertEquals("Foo", json.get("minecraft:item.foo").getAsString());
    }

    @Test
    void skipsWriteWhenNoEntries() {
        LangGeneratorJS generator = new LangGeneratorJS("en_us");

        generator.writeTo(assetsRoot, "en_us");

        assertTrue(!Files.exists(assetsRoot.resolve("lang/en_us.json")));
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
