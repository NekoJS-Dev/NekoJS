package com.tkisor.nekojs.script;

import com.tkisor.nekojs.api.JSTypeAdapter;
import com.tkisor.nekojs.api.ScriptType;
import com.tkisor.nekojs.api.catalog.ManualDeclarationCatalogEntry;
import com.tkisor.nekojs.api.catalog.TypeDocCatalogEntry;
import com.tkisor.nekojs.api.data.Binding;
import com.tkisor.nekojs.api.event.EventGroup;
import com.tkisor.nekojs.api.plugin.IPluginRuntime;
import com.tkisor.nekojs.api.recipe.RecipeLifecycleContext;
import com.tkisor.nekojs.api.recipe.RecipeNamespaceEntry;
import com.tkisor.nekojs.api.surface.ApiRuntimeView;
import com.tkisor.nekojs.api.surface.ApiSymbolId;
import com.tkisor.nekojs.api.surface.EnvironmentKey;
import com.tkisor.nekojs.core.NekoCoreContext;
import com.tkisor.nekojs.core.NekoSandboxFactory;
import com.tkisor.nekojs.core.ScriptEventBridge;
import com.tkisor.nekojs.core.compiler.ScriptCompilerRegistry;
import com.tkisor.nekojs.core.config.SandboxConfig;
import com.tkisor.nekojs.core.error.DefaultErrorTracker;
import com.tkisor.nekojs.core.fs.ClassFilter;
import com.tkisor.nekojs.core.fs.NekoJSPaths;
import com.tkisor.nekojs.script.prop.ScriptProperty;
import com.tkisor.nekojs.script.prop.ScriptPropertyRegistry;
import com.tkisor.nekojs.testfixture.TestPlatformInit;
import graal.graalvm.polyglot.Engine;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * W4 验收：连续 50 次事务式 reload 的内存稳定性（不单调增长）。
 *
 * <p>每次 reload 创建候选 Graal Context、执行 ESM 入口（虚拟 ESM registry + prepared
 * pipeline cache + source map 全部走一遍）、提交后关闭旧 Context。已知的历史泄漏源：
 * RunawayWatchdog 的 ThreadLocal 强引用最后一次求值的 Source（§3-13，已改弱引用）、
 * CONTEXT_TO_MANAGER / ScriptContextRegistry 残留（close 路径已保证 remove/unbind）、
 * NekoEsmVirtualModuleRegistry generation 计数无界（clear(type) 已重置）。
 *
 * <p>断言策略：每 10 次 reload 后强制 GC 取 used-heap 窗口值；最后一个窗口不得比第一个
 * 窗口高出容差（容差覆盖 JIT/Graal 内部结构的一次性暖机，不允许持续增长趋势）。
 * 窗口序列打印到 stdout，便于 CI 失败时诊断。
 */
class ReloadMemoryStabilityTest {

    private static final int RELOADS = 50;
    private static final int WINDOW = 10;
    /** 允许的一次性暖机增长上限；持续泄漏（每轮恒定增量）会远超它。 */
    private static final long TOLERANCE_BYTES = 32L * 1024 * 1024;

    public static final class Recorder {
        private volatile String value;
        public void record(String value) { this.value = value; }
        public String value() { return value; }
    }

    private static final class StubPluginRuntime implements IPluginRuntime {
        private final Recorder recorder;
        StubPluginRuntime(Recorder recorder) { this.recorder = recorder; }

        @Override public Map<String, Binding> bindings(ScriptType type) {
            return Map.of("TestRecorder", Binding.of("TestRecorder", recorder));
        }
        @Override public Map<String, EventGroup> eventGroups() { return Map.of(); }
        @Override public List<JSTypeAdapter<?>> adapters() { return List.of(); }
        @Override public List<TypeDocCatalogEntry> typeDocs() { return List.of(); }
        @Override public List<ManualDeclarationCatalogEntry> manualDeclarations() { return List.of(); }
        @Override public Map<String, String> nodeModules() { return Map.of(); }
        @Override public Map<String, RecipeNamespaceEntry> recipeNamespaces() { return Map.of(); }
        @Override public void beforeRecipeLoading(RecipeLifecycleContext context) {}
        @Override public void afterRecipes(RecipeLifecycleContext context) {}
        @Override public void fireInit() {}
        @Override public void fireInitStartup() {}
        @Override public void fireAfterInit() {}
        @Override public void fireBeforeScriptsLoaded(ScriptType type) {}
        @Override public void fireAfterScriptsLoaded(ScriptType type) {}
        @Override public ApiRuntimeView apiRuntime(EnvironmentKey environment) { return null; }
        @Override public Object managedApiImplementation(ApiSymbolId globalId) { return null; }
    }

    @BeforeAll
    static void initPlatform() {
        // 固定非临时 gameDir：FileAppender 持有日志句柄直到 JVM 退出，@TempDir 会因清理失败挂掉
        Path gameDir = Path.of(System.getProperty("java.io.tmpdir"), "nekojs-test-gamedir");
        TestPlatformInit.ensureInitialized(gameDir);
    }

    @BeforeEach
    void cleanScriptDir() throws Exception {
        Path serverDir = NekoJSPaths.get().serverScripts();
        Files.createDirectories(serverDir);
        try (var stream = Files.list(serverDir)) {
            for (Path path : stream.toList()) {
                Files.deleteIfExists(path);
            }
        }
    }

    @Test
    void fiftyConsecutiveReloadsDoNotGrowMemoryMonotonically() throws Exception {
        NekoJSPaths paths = NekoJSPaths.get();
        Path serverDir = paths.serverScripts();
        Path entry = serverDir.resolve("entry.mjs");
        Path helper = serverDir.resolve("helper.mjs");
        Files.writeString(entry, """
                import { value } from './helper.mjs';
                TestRecorder.record(value);
                """);
        Files.writeString(helper, "export const value = 'stable';\n");

        Recorder recorder = new Recorder();
        Engine engine = Engine.newBuilder().build();
        SandboxConfig config = SandboxConfig.defaultConfig();
        DefaultErrorTracker tracker = new DefaultErrorTracker(paths, config);
        StubPluginRuntime pluginRuntime = new StubPluginRuntime(recorder);
        NekoCoreContext core = new NekoCoreContext(engine, config, new ClassFilter(config), tracker);
        NekoSandboxFactory sandboxFactory = new NekoSandboxFactory(core, paths,
                ScriptCompilerRegistry.createRuntimeRegistry(), pluginRuntime);
        ScriptEnvironmentFactory environmentFactory =
                new ScriptEnvironmentFactory(ScriptEventBridge.EMPTY, pluginRuntime, sandboxFactory);
        ScriptManager manager = new ScriptManager(ScriptType.SERVER, ScriptEventBridge.EMPTY, pluginRuntime,
                propertyRegistry(), tracker, paths, config, environmentFactory);

        long[] windows = new long[RELOADS / WINDOW];
        try {
            assertDoesNotThrow(() -> {
                manager.discoverScripts();
                manager.loadScripts();
                for (int i = 1; i <= RELOADS; i++) {
                    manager.reloadScripts();
                    assertEquals("stable", recorder.value(),
                            "reload #" + i + " must re-execute the entry and observe the helper export");
                    if (i % WINDOW == 0) {
                        windows[i / WINDOW - 1] = usedHeapAfterGc();
                        System.out.printf("[ReloadMemoryStability] after %2d reloads: used heap = %d KB%n",
                                i, windows[i / WINDOW - 1] / 1024);
                    }
                }
            });
        } finally {
            manager.close();
            engine.close();
            Files.deleteIfExists(entry);
            Files.deleteIfExists(helper);
        }

        long first = windows[0];
        long last = windows[windows.length - 1];
        assertTrue(last <= first + TOLERANCE_BYTES,
                "used heap after " + RELOADS + " reloads (" + (last / 1024) + " KB) grew by "
                        + ((last - first) / 1024) + " KB over the first window (" + (first / 1024)
                        + " KB) — sustained per-reload leak; windows="
                        + java.util.Arrays.toString(java.util.Arrays.stream(windows).map(w -> w / 1024).toArray()));
        // 额外诊断：中间窗口若已出现单调递增趋势，打印警告（不判失败，避免 GC 噪声误报）
        for (int i = 1; i < windows.length; i++) {
            if (windows[i] > windows[i - 1]) {
                System.out.printf("[ReloadMemoryStability] note: window %d (%d KB) > window %d (%d KB)%n",
                        i, windows[i] / 1024, i - 1, windows[i - 1] / 1024);
            }
        }
    }

    private static ScriptPropertyRegistry propertyRegistry() {
        var impl = new ScriptPropertyRegistry.Impl();
        impl.register(ScriptProperty.AFTER);
        impl.register(ScriptProperty.MODLOADED);
        impl.register(ScriptProperty.DISABLE);
        impl.register(ScriptProperty.PRIORITY);
        impl.freeze();
        return impl;
    }

    private static long usedHeapAfterGc() throws InterruptedException {
        for (int i = 0; i < 3; i++) {
            System.gc();
            Thread.sleep(50);
        }
        Runtime rt = Runtime.getRuntime();
        return rt.totalMemory() - rt.freeMemory();
    }
}
