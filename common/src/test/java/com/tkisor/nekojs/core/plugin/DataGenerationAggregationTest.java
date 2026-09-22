package com.tkisor.nekojs.core.plugin;

import com.tkisor.nekojs.api.NekoJSPlugin;
import com.tkisor.nekojs.api.annotation.RegisterNekoJSPlugin;
import com.tkisor.nekojs.core.NekoJSBasePluginManager;
import com.tkisor.nekojs.core.fs.NekoJSPaths;
import com.tkisor.nekojs.testfixture.TestPlatformInit;
import com.tkisor.nekojs.wrapper.DataGenerationBatch;
import com.tkisor.nekojs.wrapper.DataGeneratorJS;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Ticket 23 AC4/AC5 fixtures: the plugin {@code generateData} hook and the script
 * {@code ServerEvents.generateData} event aggregate into ONE data generation path
 * ({@link PluginGenerationHooks#runGenerateData}) — plugins fire before scripts, both share
 * the same generator instance, both stage into the same candidate area, and a single
 * validation+publish closes the batch. Contributor attribution (plugin id / script phase) and
 * failure isolation (throwing plugin) are observable through the published artifacts.
 */
class DataGenerationAggregationTest {

    private static final Field ENTRIES_FIELD = field("ENTRIES");
    private static final Field SORTED_VIEW_FIELD = field("sortedView");
    private static final Field OWNED_VIEW_FIELD = field("ownedView");

    private final List<Path> roots = new ArrayList<>();
    private Object previousEntries;

    @BeforeAll
    static void initPlatform() {
        TestPlatformInit.ensureInitialized();
    }

    @BeforeEach
    void resetPluginManagerAndRecordings() throws Exception {
        previousEntries = ENTRIES_FIELD.get(null);
        ENTRIES_FIELD.set(null, new CopyOnWriteArrayList<>());
        SORTED_VIEW_FIELD.set(null, null);
        OWNED_VIEW_FIELD.set(null, null);
        OrderRecordingPlugin.reset();
    }

    @AfterEach
    void restorePluginManagerAndScratch() throws Exception {
        // restore first so unrelated tests see the original plugin entries again
        SORTED_VIEW_FIELD.set(null, null);
        OWNED_VIEW_FIELD.set(null, null);
        ENTRIES_FIELD.set(null, previousEntries == null ? new CopyOnWriteArrayList<>() : previousEntries);
        for (Path root : roots) {
            deleteRecursively(root);
            deleteRecursively(root.getParent()
                    .resolve(DataGenerationBatch.STATE_DIR_NAME).resolve("after_mods"));
        }
    }

    private static Field field(String name) {
        try {
            Field f = NekoJSBasePluginManager.class.getDeclaredField(name);
            f.setAccessible(true);
            return f;
        } catch (NoSuchFieldException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    @Test
    void pluginsFireBeforeScriptsThroughOneCandidateAndPublishPath() throws Exception {
        NekoJSBasePluginManager.registerClass(OrderRecordingPlugin.class);
        Path dataRoot = uniqueRoot("aggregate");
        List<DataGeneratorJS> scriptGenerators = new CopyOnWriteArrayList<>();

        PluginGenerationHooks.runGenerateData(dataRoot, "after_mods", generator -> {
            OrderRecordingPlugin.sequence.add("script");
            scriptGenerators.add(generator);
            generator.json("by_script/note.json", "{\"from\":\"script\"}");
        });

        // Contribution order: plugins first, then the script phase (KubeJS-aligned sharing).
        assertEquals(List.of("plugin", "script"), OrderRecordingPlugin.sequence);
        // Same generator instance reaches plugins and the script event: one path, one candidate.
        assertEquals(1, OrderRecordingPlugin.dataCalls.size());
        assertSame(OrderRecordingPlugin.dataCalls.get(0), scriptGenerators.get(0),
                "plugin hook and script event must share the same generator instance");
        // Both contributions publish in one batch after the script phase completed.
        assertEquals("{\"from\":\"plugin\"}", Files.readString(dataRoot.resolve("by_plugin/mark.json")));
        assertEquals("{\"from\":\"script\"}", Files.readString(dataRoot.resolve("by_script/note.json")));
    }

    @Test
    void pluginContributionsAreAttributedAndThrowingPluginsAreIsolated() throws Exception {
        NekoJSBasePluginManager.registerClass(ThrowingDataPlugin.class);
        NekoJSBasePluginManager.registerClass(OrderRecordingPlugin.class);
        Path dataRoot = uniqueRoot("isolation");

        PluginGenerationHooks.runGenerateData(dataRoot, "after_mods", generator ->
                generator.text("by_script/ok.txt", "ok"));

        // The throwing plugin neither blocked the batch nor poisoned the script contribution.
        assertEquals("ok", Files.readString(dataRoot.resolve("by_script/ok.txt")));
        assertEquals("{\"from\":\"plugin\"}", Files.readString(dataRoot.resolve("by_plugin/mark.json")));
        assertEquals("plugin", OrderRecordingPlugin.sequence.get(0));
    }

    @Test
    void scriptPhaseFailureRetainsPreviousActiveAndPublishesNothing() throws Exception {
        NekoJSBasePluginManager.registerClass(OrderRecordingPlugin.class);
        Path dataRoot = uniqueRoot("failure");
        // Seed one successful batch so "previous active" exists.
        PluginGenerationHooks.runGenerateData(dataRoot, "after_mods",
                generator -> generator.json("gen/old.json", "{\"v\":1}"));

        RuntimeException thrown = assertThrows(RuntimeException.class, () ->
                PluginGenerationHooks.runGenerateData(dataRoot, "after_mods", generator -> {
                    generator.json("gen/new.json", "{\"v\":2}");
                    throw new IllegalStateException("script phase crashed");
                }));

        assertEquals("script phase crashed", thrown.getMessage());
        assertEquals("{\"v\":1}", Files.readString(dataRoot.resolve("gen/old.json")),
                "previous active data must be retained when the script phase fails");
        assertFalse(Files.exists(dataRoot.resolve("gen/new.json")),
                "a failed batch must not leave half-written or mixed-origin artifacts");
    }

    @Test
    void validationFailureRetainsPreviousActiveAndPublishesNothing() throws Exception {
        NekoJSBasePluginManager.registerClass(OrderRecordingPlugin.class);
        Path dataRoot = uniqueRoot("validation");
        PluginGenerationHooks.runGenerateData(dataRoot, "after_mods",
                generator -> generator.json("gen/old.json", "{\"v\":1}"));

        assertThrows(IllegalStateException.class, () ->
                PluginGenerationHooks.runGenerateData(dataRoot, "after_mods", generator -> {
                    // Valid write through the generator...
                    generator.json("gen/new.json", "{\"v\":2}");
                    // ...plus an external invalid file dropped into the candidate area.
                    try {
                        Path candidate = dataRoot.getParent()
                                .resolve(DataGenerationBatch.STATE_DIR_NAME)
                                .resolve("after_mods").resolve("candidate");
                        Files.createDirectories(candidate.resolve("gen"));
                        Files.writeString(candidate.resolve("gen/broken.json"), "{invalid");
                    } catch (IOException e) {
                        throw new UncheckedIOException(e);
                    }
                }));

        assertEquals("{\"v\":1}", Files.readString(dataRoot.resolve("gen/old.json")));
        assertFalse(Files.exists(dataRoot.resolve("gen/new.json")));
    }

    private Path uniqueRoot(String label) throws IOException {
        Path root = NekoJSPaths.get().root()
                .resolve("test-datagen-agg-" + label + "-" + System.nanoTime());
        roots.add(root);
        return Files.createDirectories(root);
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

    /** priority 1001 > 1000: throws before the recording plugin, pinning per-plugin isolation. */
    @RegisterNekoJSPlugin(priority = 1001)
    public static class ThrowingDataPlugin implements NekoJSPlugin {
        @Override
        public void generateData(DataGeneratorJS generator) {
            throw new IllegalStateException("boom-generateData-plugin");
        }
    }

    /** Records hook order and writes one attributed plugin artifact. */
    @RegisterNekoJSPlugin(priority = 1000)
    public static class OrderRecordingPlugin implements NekoJSPlugin {
        static final List<DataGeneratorJS> dataCalls = new CopyOnWriteArrayList<>();
        static final List<String> sequence = new CopyOnWriteArrayList<>();

        static void reset() {
            dataCalls.clear();
            sequence.clear();
        }

        @Override
        public void generateData(DataGeneratorJS generator) {
            dataCalls.add(generator);
            sequence.add("plugin");
            generator.json("by_plugin/mark.json", "{\"from\":\"plugin\"}");
        }
    }
}
