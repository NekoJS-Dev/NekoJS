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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Path-boundary regression tests for {@link LangGeneratorJS#writeTo(Path, String)}.
 *
 * <p>All writes go through a temporary assets root inside the injected platform game dir, so
 * {@link com.tkisor.nekojs.core.fs.NekoJSPaths#get()} resolves to the test game dir.
 */
class LangGeneratorJSPathTest {
    private static Path gameDir;
    private Path assetsRoot;

    @BeforeAll
    static void initPlatform() {
        TestPlatformInit.ensureInitialized();
        gameDir = Platform.getGameDir();
    }

    @BeforeEach
    void setUp() throws Exception {
        assetsRoot = gameDir.resolve("nekojs").resolve("test-lang-assets-" + System.nanoTime());
        Files.createDirectories(assetsRoot);
    }

    @AfterEach
    void tearDown() throws Exception {
        deleteRecursively(assetsRoot);
    }

    @Test
    void writesValidLangToAssetsRootLangDir() throws Exception {
        LangGeneratorJS generator = new LangGeneratorJS("en_us");
        generator.add("minecraft:item.foo", "Foo");
        generator.addAll(Map.of("minecraft:item.bar", "Bar"));

        generator.writeTo(assetsRoot, "en_us");

        Path file = assetsRoot.resolve("lang").resolve("en_us.json");
        assertTrue(Files.isRegularFile(file), "expected lang file at " + file);
        var json = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
        assertEquals("Foo", json.get("minecraft:item.foo").getAsString());
        assertEquals("Bar", json.get("minecraft:item.bar").getAsString());
    }

    @Test
    void mergesWithExistingEntriesAndOverwritesSameKeys() throws Exception {
        Path langDir = assetsRoot.resolve("lang");
        Files.createDirectories(langDir);
        Files.writeString(langDir.resolve("en_us.json"),
                "{\"minecraft:item.foo\":\"Old\",\"minecraft:item.keep\":\"Keep\"}");

        LangGeneratorJS generator = new LangGeneratorJS("en_us");
        generator.add("minecraft:item.foo", "New");
        generator.add("minecraft:item.added", "Added");
        generator.writeTo(assetsRoot, "en_us");

        var json = JsonParser.parseString(Files.readString(langDir.resolve("en_us.json"))).getAsJsonObject();
        assertEquals("Keep", json.get("minecraft:item.keep").getAsString(), "existing key should be preserved");
        assertEquals("New", json.get("minecraft:item.foo").getAsString(), "new entry should overwrite old value");
        assertEquals("Added", json.get("minecraft:item.added").getAsString(), "new entry should be added");
    }

    @Test
    void rejectsInvalidLangEvenWhenNoEntries() {
        LangGeneratorJS generator = new LangGeneratorJS("en_us");

        assertThrows(IllegalArgumentException.class, () -> generator.writeTo(assetsRoot, "../evil"));
    }

    @Test
    void requiresAssetsRootEvenWhenNoEntries() {
        LangGeneratorJS generator = new LangGeneratorJS("en_us");

        assertThrows(NullPointerException.class, () -> generator.writeTo(null, "en_us"));
    }

    @Test
    void rejectsTraversalAndAbsoluteLangCodesBeforeAnyIo() throws Exception {
        Path escapedInside = assetsRoot.resolve("evil.json");
        Path absoluteLangBase = gameDir.toAbsolutePath().getRoot()
                .resolve("tmp")
                .resolve("nekojs-lang-evil-" + System.nanoTime());
        Path escapedOutside = Path.of(absoluteLangBase.toString() + ".json");

        String[] invalidLangs = {
                "../evil",
                "en_us/../../evil",
                absoluteLangBase.toString(),
                absoluteLangBase.toString().replace('\\', '/')
        };
        for (String invalid : invalidLangs) {
            LangGeneratorJS generator = new LangGeneratorJS(invalid);
            generator.add("minecraft:item.foo", "Foo");
            try {
                assertThrows(IllegalArgumentException.class,
                        () -> generator.writeTo(assetsRoot, invalid),
                        "invalid lang code should be rejected: " + invalid);
            } finally {
                Files.deleteIfExists(escapedInside);
                Files.deleteIfExists(escapedOutside);
            }
            assertTrue(Files.notExists(escapedInside), "must not write an escaped file inside assetsRoot for: " + invalid);
            assertTrue(Files.notExists(escapedOutside), "must not write an escaped file outside assetsRoot for: " + invalid);
        }

        assertTrue(Files.notExists(assetsRoot.resolve("lang")),
                "invalid lang must be rejected before any lang directory is created");
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
