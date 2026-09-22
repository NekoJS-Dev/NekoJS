package com.tkisor.nekojs.script;

import com.tkisor.nekojs.api.ScriptType;
import com.tkisor.nekojs.api.data.Binding;
import com.tkisor.nekojs.api.plugin.IPluginRuntime;
import com.tkisor.nekojs.core.NekoCoreContext;
import com.tkisor.nekojs.core.NekoSandboxFactory;
import com.tkisor.nekojs.core.ScriptEventBridge;
import com.tkisor.nekojs.core.compiler.ScriptCompilerRegistry;
import com.tkisor.nekojs.core.config.SandboxConfig;
import com.tkisor.nekojs.core.error.DefaultErrorTracker;
import com.tkisor.nekojs.core.fs.ClassFilter;
import com.tkisor.nekojs.core.fs.NekoJSPaths;
import com.tkisor.nekojs.core.module.NekoModulePipelineCache;
import com.tkisor.nekojs.core.pack.ScriptPack;
import com.tkisor.nekojs.core.pack.ScriptPackRegistry;
import com.tkisor.nekojs.script.prop.ScriptPropertyRegistry;
import com.tkisor.nekojs.testfixture.NekoModuleTestFixtures;
import com.tkisor.nekojs.testfixture.TestPlatformInit;
import graal.graalvm.polyglot.Engine;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * WORLD 包相对路径回归（票 03 §3-1 实码缺陷、票 07 G4 遗留、票 19 承接）：
 * 平台传入的 world 目录可能是相对形式（Windows 专用服观察到 {@code .\world\.}），而
 * nekojs root 恒为绝对路径。若包脚本路径保持相对形式，{@code ScriptExecutor} 里
 * {@code paths.root().relativize(script.path)} 混用绝对/相对路径抛 IAE——WORLD 包激活后的
 * SERVER reload 中该包脚本全部执行失败（错误进面板、脚本 disabled），即「激活了但没跑」。
 *
 * <p>修复语义：{@link ScriptPackRegistry#activateWorldPacks} 把 world 目录归一为绝对
 * 路径后再扫描，包脚本路径进入 ScriptContainer 前已是绝对形式。
 *
 * <p>测试几何：共享测试 gameDir 在系统临时盘，与 Gradle CWD（模块目录）可能不在同一盘，
 * 无法从 CWD 构造指向它的相对路径。因此本测试临时把 {@code NekoJSPaths} 单例换成在
 * CWD 同盘 {@code build/} 下自建的 gameDir（相对 world 目录由此可达），结束后恢复原
 * 实例并重扫全局包。这是测试专用反射 seam（同根树测试反射 Platform.INSTANCE 的先例）。
 */
class WorldPackRelativePathExecutionTest {

    @BeforeAll
    static void initPlatform() {
        TestPlatformInit.ensureInitialized();
    }

    @Test
    void worldPackActivatedFromRelativeDirExecutesAfterReload() throws Exception {
        Path cwd = Path.of("").toAbsolutePath();
        Path gameDir = cwd.resolve("build").resolve("nekojs-g4-worldpack-gamedir");
        deleteRecursively(gameDir);
        Path worldDir = gameDir.resolve("world");
        Path packScripts = worldDir.resolve(ScriptPackRegistry.WORLD_PACKS_DIR)
                .resolve("g4demo").resolve("server_scripts");
        Files.createDirectories(packScripts);
        Files.writeString(packScripts.getParent().resolve("manifest.json"), "{\"id\": \"g4demo\"}");
        Files.writeString(packScripts.resolve("w.js"), "TestRecorder.record('g4-world-pack-ok');\n");

        // 相对形式的 world 目录：与生产平台入口（server.getWorldPath）观察到的形态同构
        Path relativeWorldDir = cwd.relativize(worldDir);
        assertFalse(relativeWorldDir.isAbsolute(), "test setup must hand the registry a relative world dir");

        NekoJSPaths originalPaths = NekoJSPaths.get();
        NekoJSPaths testPaths = NekoJSPaths.fromGameDir(gameDir);
        swapPathsInstance(testPaths);
        Recorder recorder = new Recorder();
        Engine engine = Engine.newBuilder().build();
        ScriptManager manager = null;
        try {
            ScriptPackRegistry.get().refreshGlobalPacks();
            ScriptPackRegistry.get().activateWorldPacks(relativeWorldDir);

            List<ScriptPack> worldPacks = ScriptPackRegistry.get().worldPacks();
            assertEquals(1, worldPacks.size(), "the relative world dir must still be scannable");
            assertTrue(worldPacks.get(0).root().isAbsolute(),
                    "activation must normalize pack roots to absolute paths before they reach ScriptContainer");

            manager = newManager(testPaths, recorder, engine);
            manager.discoverScripts();
            manager.loadScripts();
            assertEquals("g4-world-pack-ok", recorder.value(),
                    "world pack scripts must execute after the activation reload");
        } finally {
            ScriptPackRegistry.get().deactivateWorldPacks();
            if (manager != null) {
                manager.close();
            }
            engine.close();
            swapPathsInstance(originalPaths);
            ScriptPackRegistry.get().refreshGlobalPacks();
            deleteRecursively(gameDir);
        }
    }

    /* ================= 测试装配 ================= */

    /** 脚本侧上报通道（global binding {@code TestRecorder}）。 */
    public static final class Recorder {
        private volatile String value;

        public void record(String value) {
            this.value = value;
        }

        String value() {
            return value;
        }
    }

    private static ScriptManager newManager(NekoJSPaths paths, Recorder recorder, Engine engine) {
        SandboxConfig config = SandboxConfig.defaultConfig();
        DefaultErrorTracker tracker = new DefaultErrorTracker(paths, config);
        StubPluginRuntime pluginRuntime = new StubPluginRuntime(recorder);
        ScriptCompilerRegistry compilers = ScriptCompilerRegistry.createRuntimeRegistry();
        NekoCoreContext core = new NekoCoreContext(engine, config, new ClassFilter(config), tracker);
        NekoModulePipelineCache cache = NekoModuleTestFixtures.newCache(paths, compilers, config);
        NekoSandboxFactory sandboxFactory = new NekoSandboxFactory(core, paths, compilers, pluginRuntime, cache);
        ScriptEnvironmentFactory environmentFactory = new ScriptEnvironmentFactory(
                ScriptEventBridge.EMPTY, pluginRuntime, sandboxFactory,
                new com.tkisor.nekojs.core.state.GlobalStateStores());
        return new ScriptManager(ScriptType.SERVER, ScriptEventBridge.EMPTY, pluginRuntime,
                newPropertyRegistry(), tracker, paths, config, environmentFactory,
                List.of(), cache);
    }

    private static ScriptPropertyRegistry newPropertyRegistry() {
        ScriptPropertyRegistry.Impl impl = new ScriptPropertyRegistry.Impl();
        impl.register(com.tkisor.nekojs.script.prop.ScriptProperty.AFTER);
        impl.register(com.tkisor.nekojs.script.prop.ScriptProperty.MODLOADED);
        impl.register(com.tkisor.nekojs.script.prop.ScriptProperty.DISABLE);
        impl.register(com.tkisor.nekojs.script.prop.ScriptProperty.PRIORITY);
        impl.freeze();
        return impl;
    }

    private static final class StubPluginRuntime implements IPluginRuntime {
        private final Recorder recorder;

        StubPluginRuntime(Recorder recorder) {
            this.recorder = recorder;
        }

        @Override public Map<String, Binding> bindings(ScriptType type) {
            return Map.of("TestRecorder", new Binding() {
                @Override public String name() { return "TestRecorder"; }
                @Override public Object value() { return recorder; }
                @Override public void close(ScriptType closedType) {}
            });
        }

        @Override public Map<String, com.tkisor.nekojs.api.event.EventGroup> eventGroups() { return Map.of(); }
        @Override public List<com.tkisor.nekojs.api.JSTypeAdapter<?>> adapters() { return List.of(); }
        @Override public List<com.tkisor.nekojs.api.catalog.TypeDocCatalogEntry> typeDocs() { return List.of(); }
        @Override public List<com.tkisor.nekojs.api.catalog.ManualDeclarationCatalogEntry> manualDeclarations() { return List.of(); }
        @Override public Map<String, String> nodeModules() { return Map.of(); }
        @Override public Map<String, com.tkisor.nekojs.api.recipe.RecipeNamespaceEntry> recipeNamespaces() { return Map.of(); }
        @Override public void beforeRecipeLoading(com.tkisor.nekojs.api.recipe.RecipeLifecycleContext context) {}
        @Override public void afterRecipes(com.tkisor.nekojs.api.recipe.RecipeLifecycleContext context) {}
        @Override public void fireInit() {}
        @Override public void fireInitStartup() {}
        @Override public void fireAfterInit() {}
        @Override public void fireBeforeScriptsLoaded(ScriptType type) {}
        @Override public void fireAfterScriptsLoaded(ScriptType type) {}
        @Override public com.tkisor.nekojs.api.surface.ApiRuntimeView apiRuntime(com.tkisor.nekojs.api.surface.EnvironmentKey environment) { return null; }
        @Override public Object managedApiImplementation(com.tkisor.nekojs.api.surface.ApiSymbolId globalId) { return null; }
    }

    /** 临时替换 {@code NekoJSPaths} 单例（仅本测试 JVM、用后恢复）。 */
    private static void swapPathsInstance(NekoJSPaths replacement) throws Exception {
        Field instance = NekoJSPaths.class.getDeclaredField("INSTANCE");
        instance.setAccessible(true);
        instance.set(null, replacement);
    }

    private static void deleteRecursively(Path dir) {
        if (!Files.exists(dir)) return;
        try (Stream<Path> stream = Files.walk(dir)) {
            stream.sorted(Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (Exception ignored) {
                    // 日志 FileAppender 可能占用句柄；残留位于 build/ 下，随构建清理
                }
            });
        } catch (Exception ignored) {
            // 同上：best-effort 清理
        }
    }
}
