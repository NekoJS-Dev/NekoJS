package com.tkisor.nekojs.core.plugin;

import com.tkisor.nekojs.api.NekoJSPlugin;
import com.tkisor.nekojs.api.annotation.RegisterNekoJSPlugin;
import com.tkisor.nekojs.core.NekoJSBasePluginManager;
import com.tkisor.nekojs.core.fs.NekoJSPaths;
import com.tkisor.nekojs.testfixture.TestPlatformInit;
import com.tkisor.nekojs.wrapper.DataGeneratorJS;
import com.tkisor.nekojs.wrapper.LangGeneratorJS;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Smoke test for the {@code generateData}, {@code generateAssets} and {@code generateLang} plugin
 * hooks.
 *
 * <p>The platform fires these hooks during a resource reload through entry points such as
 * {@link PluginGenerationHooks#fireGenerateData}. They run before script events, share the same
 * generator instance as scripts, and no built-in plugin implemented them, so this test registers
 * recording plugins in {@link NekoJSBasePluginManager} and fires the entry points. It checks that
 * the chain really calls the plugins: the same generator instance arrives, hooks do not interfere,
 * a plugin's writes land in the NekoJS data pack root, and one plugin's exception is isolated so
 * later plugins still run (priority from high to low sets the order).
 */
class PluginGenerationHooksTest {

    private static final Field ENTRIES_FIELD = field("ENTRIES");
    private static final Field SORTED_VIEW_FIELD = field("sortedView");
    private static final Field OWNED_VIEW_FIELD = field("ownedView");

    private Object previousEntries;
    private Object previousSortedView;
    private Object previousOwnedView;

    @BeforeAll
    static void initPlatform() {
        TestPlatformInit.ensureInitialized();
    }

    @BeforeEach
    void resetPluginManagerAndRecordings() throws Exception {
        // Back up and clear the global plugin manager state (the test hook pattern from NekoJSBasePluginManagerTest),
        // so the fire entry points see only the plugins registered by this test
        previousEntries = ENTRIES_FIELD.get(null);
        previousSortedView = SORTED_VIEW_FIELD.get(null);
        previousOwnedView = OWNED_VIEW_FIELD.get(null);
        ENTRIES_FIELD.set(null, new CopyOnWriteArrayList<>());
        SORTED_VIEW_FIELD.set(null, null);
        OWNED_VIEW_FIELD.set(null, null);

        RecordingGenerationPlugin.reset();
    }

    @AfterEach
    void restorePluginManager() throws Exception {
        SORTED_VIEW_FIELD.set(null, null);
        OWNED_VIEW_FIELD.set(null, null);
        ENTRIES_FIELD.set(null, previousEntries == null ? new CopyOnWriteArrayList<>() : previousEntries);
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
    void fireGenerateDataInvokesRegisteredPluginWithSharedGenerator() {
        NekoJSBasePluginManager.registerClass(RecordingGenerationPlugin.class);
        DataGeneratorJS generator = new DataGeneratorJS(NekoJSPaths.get().data(), "after_mods");

        PluginGenerationHooks.fireGenerateData(generator);

        assertEquals(List.of(generator), RecordingGenerationPlugin.dataCalls,
                "fireGenerateData 必须以同一 generator 实例调用已登记插件的 generateData");
        assertTrue(RecordingGenerationPlugin.assetCalls.isEmpty(), "不应误触 generateAssets");
        assertTrue(RecordingGenerationPlugin.langCalls.isEmpty(), "不应误触 generateLang");
    }

    @Test
    void fireGenerateAssetsInvokesRegisteredPluginWithSharedGenerator() {
        NekoJSBasePluginManager.registerClass(RecordingGenerationPlugin.class);
        DataGeneratorJS generator = new DataGeneratorJS(NekoJSPaths.get().assets());

        PluginGenerationHooks.fireGenerateAssets(generator);

        assertEquals(List.of(generator), RecordingGenerationPlugin.assetCalls,
                "fireGenerateAssets 必须以同一 generator 实例调用已登记插件的 generateAssets");
        assertTrue(RecordingGenerationPlugin.dataCalls.isEmpty(), "不应误触 generateData");
        assertTrue(RecordingGenerationPlugin.langCalls.isEmpty(), "不应误触 generateLang");
    }

    @Test
    void fireGenerateLangInvokesPluginAndSharesCollectorState() {
        NekoJSBasePluginManager.registerClass(RecordingGenerationPlugin.class);
        LangGeneratorJS generator = new LangGeneratorJS("en_us");

        PluginGenerationHooks.fireGenerateLang(generator);

        assertEquals(List.of(generator), RecordingGenerationPlugin.langCalls,
                "fireGenerateLang 必须以同一 generator 实例调用已登记插件的 generateLang");
        assertEquals("from-plugin", generator.entries().get("nekojs.smoke.lang"),
                "插件与脚本事件共享同一 generator：插件 add 的条目应立即可见");
    }

    @Test
    void generateDataHookWritesIntoDataPackRootThroughSharedGenerator() throws Exception {
        Files.createDirectories(NekoJSPaths.get().data());
        NekoJSBasePluginManager.registerClass(WritingGenerationPlugin.class);

        PluginGenerationHooks.fireGenerateData(new DataGeneratorJS(NekoJSPaths.get().data(), "after_mods"));

        Path written = NekoJSPaths.get().data().resolve("plugin_smoke/mark.txt");
        assertEquals("plugin-was-here", Files.readString(written),
                "插件在 generateData 中经共享 generator 写出的文件应落在 <gameDir>/nekojs/data 根下");
    }

    @Test
    void throwingPluginIsIsolatedAndDoesNotBlockLaterPlugins() {
        // priority 1001 > 1000, so the throwing plugin always fires before the recording one
        // (order within one priority is unstable)
        NekoJSBasePluginManager.registerClass(ThrowingGenerationPlugin.class);
        NekoJSBasePluginManager.registerClass(RecordingGenerationPlugin.class);
        DataGeneratorJS dataGen = new DataGeneratorJS(NekoJSPaths.get().data());
        DataGeneratorJS assetGen = new DataGeneratorJS(NekoJSPaths.get().assets());
        LangGeneratorJS langGen = new LangGeneratorJS("en_us");

        assertDoesNotThrow(() -> PluginGenerationHooks.fireGenerateData(dataGen));
        assertDoesNotThrow(() -> PluginGenerationHooks.fireGenerateAssets(assetGen));
        assertDoesNotThrow(() -> PluginGenerationHooks.fireGenerateLang(langGen));

        assertEquals(List.of(dataGen), RecordingGenerationPlugin.dataCalls,
                "前序插件抛异常不应中断后续插件的 generateData");
        assertEquals(List.of(assetGen), RecordingGenerationPlugin.assetCalls,
                "前序插件抛异常不应中断后续插件的 generateAssets");
        assertEquals(List.of(langGen), RecordingGenerationPlugin.langCalls,
                "前序插件抛异常不应中断后续插件的 generateLang");
    }

    /**
     * Records calls to the three generation hooks; the plugin manager creates the instance, and the
     * recording goes through static state.
     */
    @RegisterNekoJSPlugin(priority = 1000)
    public static class RecordingGenerationPlugin implements NekoJSPlugin {
        static final List<DataGeneratorJS> dataCalls = new CopyOnWriteArrayList<>();
        static final List<DataGeneratorJS> assetCalls = new CopyOnWriteArrayList<>();
        static final List<LangGeneratorJS> langCalls = new CopyOnWriteArrayList<>();

        static void reset() {
            dataCalls.clear();
            assetCalls.clear();
            langCalls.clear();
        }

        @Override
        public void generateData(DataGeneratorJS generator) {
            dataCalls.add(generator);
        }

        @Override
        public void generateAssets(DataGeneratorJS generator) {
            assetCalls.add(generator);
        }

        @Override
        public void generateLang(LangGeneratorJS generator) {
            langCalls.add(generator);
            generator.add("nekojs.smoke.lang", "from-plugin");
        }
    }

    /** Throws from all three generation hooks to check how the fire entry points isolate exceptions. */
    @RegisterNekoJSPlugin(priority = 1001)
    public static class ThrowingGenerationPlugin implements NekoJSPlugin {
        @Override
        public void generateData(DataGeneratorJS generator) {
            throw new IllegalStateException("boom-generateData");
        }

        @Override
        public void generateAssets(DataGeneratorJS generator) {
            throw new IllegalStateException("boom-generateAssets");
        }

        @Override
        public void generateLang(LangGeneratorJS generator) {
            throw new IllegalStateException("boom-generateLang");
        }
    }

    /**
     * Writes to disk through the shared generator inside generateData, checking end to end that
     * plugins and scripts share one generator.
     */
    @RegisterNekoJSPlugin(priority = 1000)
    public static class WritingGenerationPlugin implements NekoJSPlugin {
        @Override
        public void generateData(DataGeneratorJS generator) {
            generator.text("plugin_smoke/mark.txt", "plugin-was-here");
        }
    }
}
