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
 * {@code generateData} / {@code generateAssets} / {@code generateLang} 三个插件钩子的冒烟测试。
 *
 * <p>这三个钩子由平台层在资源 reload 时经 {@link PluginGenerationHooks#fireGenerateData} 等
 * 入口触发、先于脚本事件、与脚本共享同一 generator 实例，但此前没有任何内置插件实现它们
 * （dogfooding 缺口）。本测试向 {@link NekoJSBasePluginManager} 登记录制插件后触发 fire 入口，
 * 证明链路确实会调用插件：同一 generator 实例送达、钩子间不串扰、插件写盘落到
 * NekoJS 的 data pack 根、且单个插件异常被隔离不中断后续插件（priority 降序决定触发顺序）。
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
        // 插件管理器全局态备份 + 清空（test seam 用法照 NekoJSBasePluginManagerTest），
        // 保证 fire 入口遍历到的只有本测试登记的插件
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
        // priority 1001 > 1000：抛异常的插件必然先于录制插件触发（同优先级顺序不稳定，见注解契约）
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

    /** 记录三个生成钩子调用（实例由插件管理器创建，录制走静态状态）。 */
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

    /** 三个生成钩子全部抛异常，验证 fire 入口的异常隔离语义。 */
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

    /** 在 generateData 钩子里经共享 generator 写盘，验证「与脚本共享同一 generator」的端到端语义。 */
    @RegisterNekoJSPlugin(priority = 1000)
    public static class WritingGenerationPlugin implements NekoJSPlugin {
        @Override
        public void generateData(DataGeneratorJS generator) {
            generator.text("plugin_smoke/mark.txt", "plugin-was-here");
        }
    }
}
