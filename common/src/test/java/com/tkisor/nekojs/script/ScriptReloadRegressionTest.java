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
import graal.graalvm.polyglot.Context;
import graal.graalvm.polyglot.Engine;
import graal.graalvm.polyglot.Value;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 回归测试：/nekojs reload 对「非入口 helper 模块」的真实生效性。
 *
 * <p>背景缺陷：{@code ScriptManager.reloadScriptFile} 曾走 {@code hotReloadModule} 捷径，
 * 其 relink 因 revision 不匹配静默跳过全部模块却返回 success=true，导致 helper 修改后
 * reload 显示成功但完全不生效（见 ModuleSliceRelinker 的删除历史）。本测试固定修复后的
 * 契约：修改 helper 后 reload 必须让所有受影响的入口重新执行并观察到新值。
 */
class ScriptReloadRegressionTest {

    /** 记录入口脚本观察到的 helper 导出值；作为全局绑定注入沙盒。 */
    public static final class TestRecorder {
        private volatile String value;

        public void record(String value) {
            this.value = value;
        }

        public String value() {
            return value;
        }
    }

    private static final class StubPluginRuntime implements IPluginRuntime {
        private final TestRecorder recorder;

        StubPluginRuntime(TestRecorder recorder) {
            this.recorder = recorder;
        }

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

    private static Path gameDir;

    @BeforeAll
    static void initPlatform(@TempDir Path temp) {
        gameDir = temp.resolve("gamedir");
        TestPlatformInit.ensureInitialized(gameDir);
    }

    /** 测试共享同一个测试 gameDir：每个用例前清空脚本目录，避免用例之间互相发现残留脚本。 */
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

    private static ScriptPropertyRegistry newPropertyRegistry() {
        var impl = new ScriptPropertyRegistry.Impl();
        impl.register(ScriptProperty.AFTER);
        impl.register(ScriptProperty.MODLOADED);
        impl.register(ScriptProperty.DISABLE);
        impl.register(ScriptProperty.PRIORITY);
        impl.freeze();
        return impl;
    }

    @Test
    void reloadingHelperModuleReRunsAffectedEntryWithNewCode() throws Exception {
        NekoJSPaths paths = NekoJSPaths.get();
        Path serverDir = paths.serverScripts();
        Files.createDirectories(serverDir);
        Path entry = serverDir.resolve("entry.mjs");
        Path helper = serverDir.resolve("helper.mjs");

        Files.writeString(entry, """
                import { value } from './helper.mjs';
                TestRecorder.record(value);
                """);
        Files.writeString(helper, "export const value = 'v1';\n");

        TestRecorder recorder = new TestRecorder();
        Engine engine = Engine.newBuilder().build();
        ScriptManager manager = null;
        try {
            manager = newManager(paths, recorder, engine);
            manager.discoverScripts();
            manager.loadScripts();
            assertEquals("v1", recorder.value(), "initial load must execute the entry");

            Files.writeString(helper, "export const value = 'v2';\n");
            manager.reloadScriptFile("helper.mjs");

            assertEquals("v2", recorder.value(),
                    "helper reload must re-run the affected entry so the new export is observed");
        } finally {
            if (manager != null) {
                manager.close();
            }
            engine.close();
        }
    }

    private static ScriptManager newManager(NekoJSPaths paths, TestRecorder recorder, Engine engine) {
        return newManager(paths, recorder, engine, SandboxConfig.defaultConfig());
    }

    private static ScriptManager newManager(NekoJSPaths paths, TestRecorder recorder, Engine engine, SandboxConfig config) {
        return newManager(paths, recorder, engine, config, new DefaultErrorTracker(paths, config));
    }

    private static ScriptManager newManager(NekoJSPaths paths, TestRecorder recorder, Engine engine,
                                            SandboxConfig config, DefaultErrorTracker tracker) {
        return newManager(paths, recorder, engine, config, tracker, ScriptEventBridge.EMPTY);
    }

    private static ScriptManager newManager(NekoJSPaths paths, TestRecorder recorder, Engine engine,
                                            SandboxConfig config, DefaultErrorTracker tracker,
                                            ScriptEventBridge eventBridge) {
        StubPluginRuntime pluginRuntime = new StubPluginRuntime(recorder);
        ScriptCompilerRegistry compilers = ScriptCompilerRegistry.createRuntimeRegistry();
        NekoCoreContext core = new NekoCoreContext(engine, config, new ClassFilter(config), tracker);
        NekoSandboxFactory sandboxFactory = new NekoSandboxFactory(core, paths, compilers, pluginRuntime);
        ScriptEnvironmentFactory environmentFactory =
                new ScriptEnvironmentFactory(eventBridge, pluginRuntime, sandboxFactory);
        return new ScriptManager(ScriptType.SERVER, eventBridge, pluginRuntime,
                newPropertyRegistry(), tracker, paths, config, environmentFactory);
    }

    /**
     * 回归测试：顶层死循环（CJS 入口体在服务器线程同步执行）不得无限冻结服务器线程。
     * 修复契约：超过 scriptEvaluationTimeoutSeconds 后通过 Context.interrupt 注入中断，
     * 脚本按失败处理，loadScripts 正常返回。
     */
    @Test
    void infiniteLoopInScriptEntryDoesNotFreezeServerThread() throws Exception {
        NekoJSPaths paths = NekoJSPaths.get();
        Path serverDir = paths.serverScripts();
        Files.createDirectories(serverDir);
        Files.writeString(serverDir.resolve("loop.js"), "while (true) { /* spin forever */ }\n");

        TestRecorder recorder = new TestRecorder();
        Engine engine = Engine.newBuilder().build();
        // 语句上限 100_000（测试用）：死循环迅速烧尽 → Graal 关闭 Context 并中断当前求值，
        // 服务器线程恢复；生产默认 0（禁用），整合包服务器可按需在 config/nekojs-engine.toml 开启
        SandboxConfig config = new SandboxConfig(false, false, false, false, true, true, false, true, 30, 100_000L);
        DefaultErrorTracker tracker = new DefaultErrorTracker(paths, config);
        ScriptManager manager = null;
        try {
            manager = newManager(paths, recorder, engine, config, tracker);
            manager.discoverScripts();

            assertTimeoutPreemptively(Duration.ofSeconds(30), manager::loadScripts,
                    "infinite loop must be aborted by the statement limit");
            assertTrue(tracker.hasErrors(), "statement-limit failure must be recorded");

            // Context 已被 Graal 关闭：下一次取用必须自动重建，脚本环境保持可用
            assertTimeoutPreemptively(Duration.ofSeconds(30), manager::loadScripts,
                    "context must be rebuilt automatically after the statement limit closed it");
        } finally {
            // 修复前（RED）阶段：被 assertTimeoutPreemptively 放弃的求值线程仍卡在死循环里，
            // 强制关闭存活 Context 防止测试 JVM 无法退出。
            forceCloseLiveContexts();
            if (manager != null) {
                manager.close();
            }
            engine.close();
        }
    }

    /**
     * 回归测试：语句上限 kill 后的自动 Context 重建（getOrCreateContext 的重建分支）
     * 必须先清空本 scriptType 的事件监听器（与事务式 reload / fullReloadCleanup 顺序一致）。
     * 修复前重建分支只关闭旧环境而不清 listener，残留监听器闭包指向已死 Context，
     * 之后每次事件分发都会在死环境上报错，直到手动 /nekojs reload。
     */
    @Test
    void rebuildAfterStatementLimitKillClearsEventListeners() throws Exception {
        NekoJSPaths paths = NekoJSPaths.get();
        Path serverDir = paths.serverScripts();
        Files.createDirectories(serverDir);
        Files.writeString(serverDir.resolve("loop.js"), "while (true) { /* spin forever */ }\n");

        TestRecorder recorder = new TestRecorder();
        Engine engine = Engine.newBuilder().build();
        SandboxConfig config = new SandboxConfig(false, false, false, false, true, true, false, true, 30, 100_000L);
        DefaultErrorTracker tracker = new DefaultErrorTracker(paths, config);
        RecordingEventBridge bridge = new RecordingEventBridge();
        // 直接在声明处构造：lambda 捕获要求 manager 为实际上的 final 变量
        ScriptManager manager = newManager(paths, recorder, engine, config, tracker, bridge);
        try {
            manager.discoverScripts();
            assertTimeoutPreemptively(Duration.ofSeconds(30), manager::loadScripts,
                    "initial load must abort via the statement limit");
            assertTrue(bridge.typeOnlyCleared.isEmpty(),
                    "first (fresh) environment creation must not clear listeners");

            // 下一次取用 Context 触发自动重建：重建分支必须清空本 scriptType 的全部监听器
            assertTimeoutPreemptively(Duration.ofSeconds(30), () -> manager.reloadScriptFile("loop.js"));
            assertTrue(bridge.typeOnlyCleared.contains(ScriptType.SERVER),
                    "context rebuild after statement-limit kill must clear this scriptType's listeners");
        } finally {
            forceCloseLiveContexts();
            manager.close();
            engine.close();
        }
    }

    /** 记录 clearListeners 调用的桥接桩：区分「整类型清除」（kill 后自动重建路径）与「按脚本清除」（单文件重载路径）。 */
    private static final class RecordingEventBridge implements ScriptEventBridge {
        final List<ScriptType> typeOnlyCleared = new CopyOnWriteArrayList<>();
        final List<String> scriptScopedCleared = new CopyOnWriteArrayList<>();

        @Override
        public void bindEvents(Value bindings, ScriptType type) {
        }

        @Override
        public void clearListeners(ScriptType type) {
            typeOnlyCleared.add(type);
        }

        @Override
        public void clearListeners(ScriptType type, String scriptId) {
            scriptScopedCleared.add(type + ":" + scriptId);
        }
    }

    @SuppressWarnings("unchecked")
    private static void forceCloseLiveContexts() {
        try {
            Field field = ScriptManager.class.getDeclaredField("CONTEXT_TO_MANAGER");
            field.setAccessible(true);
            Map<Context, ScriptManager> map = (Map<Context, ScriptManager>) field.get(null);
            for (Context context : List.copyOf(map.keySet())) {
                try {
                    context.close(true);
                } catch (Exception ignored) {
                }
            }
        } catch (Exception ignored) {
        }
    }
}
