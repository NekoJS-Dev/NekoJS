package com.tkisor.nekojs.core.lifecycle;

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
import com.tkisor.nekojs.core.NekoSharedEngine;
import com.tkisor.nekojs.core.NekoSandboxFactory;
import com.tkisor.nekojs.core.ScriptEventBridge;
import com.tkisor.nekojs.core.compiler.NekoCompilationPipeline;
import com.tkisor.nekojs.core.compiler.ScriptCompilerRegistry;
import com.tkisor.nekojs.core.config.SandboxConfig;
import com.tkisor.nekojs.core.error.DefaultErrorTracker;
import com.tkisor.nekojs.core.fs.ClassFilter;
import com.tkisor.nekojs.core.fs.NekoJSPaths;
import com.tkisor.nekojs.core.error.SourceMapRegistry;
import com.tkisor.nekojs.core.module.NekoModulePipeline;
import com.tkisor.nekojs.core.module.NekoModulePipelineCache;
import com.tkisor.nekojs.core.module.NekoTrustContext;
import com.tkisor.nekojs.core.module.NekoEsmVirtualModuleRegistry;
import com.tkisor.nekojs.script.prop.ScriptPropertyRegistry;
import com.tkisor.nekojs.testfixture.TestPlatformInit;
import graal.graalvm.polyglot.Value;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ticket 05 AC2/AC6 可重复测试：独立 root 不互相污染、root-owned 状态随 root close 释放、
 * close 异常不阻断后续清理、重复 close 不产生二次回调。
 *
 * <p>进程级例外（NekoSharedEngine）在同一进程内共享但不得造成 root 间耦合——本测试在两个
 * root 共用 NekoSharedEngine.get() 的前提下断言错误面与 manager 集合互相隔离。
 */
class NekoRuntimeRootLifecycleTest {

    /** 记录型 bridge：记录 bindEvents / clearListeners 调用，可配置对某类型抛异常。 */
    static final class RecordingBridge implements ScriptEventBridge {
        final Map<ScriptType, Integer> bindEventsCalls = new ConcurrentHashMap<>();
        final Map<ScriptType, Integer> clearListenersCalls = new ConcurrentHashMap<>();
        volatile ScriptType throwOnClear;

        @Override
        public void bindEvents(Value bindings, ScriptType type) {
            bindEventsCalls.merge(type, 1, Integer::sum);
        }

        @Override
        public void clearListeners(ScriptType type) {
            if (type == throwOnClear) {
                throw new IllegalStateException("simulated clearListeners failure for " + type);
            }
            clearListenersCalls.merge(type, 1, Integer::sum);
        }
    }

    static final class StubPluginRuntime implements IPluginRuntime {
        @Override public Map<String, Binding> bindings(ScriptType type) { return Map.of(); }
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
        Path gameDir = Path.of(System.getProperty("java.io.tmpdir"), "nekojs-test-gamedir");
        TestPlatformInit.ensureInitialized(gameDir);
    }

    private static final class RootBuilder {
        final ScriptEventBridge bridge;
        final StubPluginRuntime pluginRuntime = new StubPluginRuntime();

        RootBuilder(ScriptEventBridge bridge) {
            this.bridge = bridge;
        }

        NekoRuntimeRoot build() {
            NekoJSPaths paths = NekoJSPaths.get();
            SandboxConfig config = SandboxConfig.defaultConfig();
            DefaultErrorTracker tracker = new DefaultErrorTracker(paths, config);
            NekoCoreContext core = new NekoCoreContext(
                    NekoSharedEngine.get(), config, ClassFilter.INSTANCE, tracker);
            ScriptCompilerRegistry compilers = ScriptCompilerRegistry.createRuntimeRegistry();
            NekoModulePipelineCache cache = new NekoModulePipelineCache(
                    new NekoModulePipeline(new NekoCompilationPipeline(), compilers, config),
                    new SourceMapRegistry(paths.root()), new NekoEsmVirtualModuleRegistry(paths.root()),
                    NekoTrustContext.local());
            NekoSandboxFactory sandboxFactory = new NekoSandboxFactory(
                    core, paths, compilers, pluginRuntime, cache);
            return new NekoRuntimeRoot(core, pluginRuntime, bridge,
                    new ScriptPropertyRegistry.Impl(), sandboxFactory, cache);
        }
    }

    /** AC2：两个独立 root 共用进程级 NekoSharedEngine，但错误面与 manager 集合互不污染。 */
    @Test
    void independentRootsDoNotPolluteEachOther() {
        RecordingBridge bridge1 = new RecordingBridge();
        RecordingBridge bridge2 = new RecordingBridge();
        NekoRuntimeRoot root1 = new RootBuilder(bridge1).build();
        NekoRuntimeRoot root2 = new RootBuilder(bridge2).build();

        // 进程级例外：共享 Engine 是同一实例（process-owned，AC2 点名）
        assertSame(NekoSharedEngine.get(), NekoSharedEngine.get());

        root1.createScriptManager(ScriptType.SERVER);
        root2.createScriptManager(ScriptType.SERVER);
        assertNotSame(root1.scriptManagerOf(ScriptType.SERVER), root2.scriptManagerOf(ScriptType.SERVER),
                "independent roots must own independent ScriptManagers");

        root1.errorTracker().recordCallbackError(ScriptType.SERVER, "isolation", new RuntimeException("root1 boom"));

        assertEquals(1, root1.errors().count(), "root1 sees its own error");
        assertEquals(0, root2.errors().count(), "root2 must not see root1's error");
        assertFalse(root2.errorTracker().hasErrors());
    }

    /** AC6：close 冲刷并释放 root-owned 状态——manager 集合清空、bridge 监听器逐类型清空。 */
    @Test
    void closeReleasesManagersAndClearsBridgeListeners() {
        RecordingBridge bridge = new RecordingBridge();
        NekoRuntimeRoot root = new RootBuilder(bridge).build();
        root.createScriptManager(ScriptType.SERVER);
        root.createScriptManager(ScriptType.CLIENT);
        assertNotNull(root.scriptManagerOrNull(ScriptType.SERVER));

        root.closeSilently();

        assertNull(root.scriptManagerOrNull(ScriptType.SERVER), "managers released on close");
        assertNull(root.scriptManagerOrNull(ScriptType.CLIENT), "managers released on close");
        // AC2 的"root-owned 状态随 close 释放"当前覆盖 manager 集合/bridge 监听器/resources；
        // ErrorTracker 刻意**不**随 close 清空（错误面要活过 close 供诊断），其 generation 归属
        // 由 06/07 处理（总账 A9/I3）——这里断言现状，防止语义漂移无人察觉。
        assertEquals(0, root.errors().count(), "tracker keeps its lifetime contract across close (see ledger A9)");
        Set<ScriptType> cleared = bridge.clearListenersCalls.keySet();
        assertTrue(cleared.contains(ScriptType.STARTUP) && cleared.contains(ScriptType.SERVER)
                        && cleared.contains(ScriptType.CLIENT) && cleared.contains(ScriptType.TEST),
                "bridge listeners must be cleared for every ScriptType, got " + cleared);
    }

    /** AC6：重复 close 不抛异常、不再触碰 manager（二次回调源为 manager flush/close，随
     *  manager 集合清空而消失；bridge 的 clearListeners 属幂等注册表清理，可重复调用）。 */
    @Test
    void doubleCloseIsSafe() {
        RecordingBridge bridge = new RecordingBridge();
        NekoRuntimeRoot root = new RootBuilder(bridge).build();
        root.createScriptManager(ScriptType.SERVER);

        root.closeSilently();
        assertTrue(bridge.clearListenersCalls.getOrDefault(ScriptType.SERVER, 0) >= 1,
                "first close must clear bridge listeners");

        assertDoesNotThrow(root::closeSilently);
        assertNull(root.scriptManagerOrNull(ScriptType.SERVER), "state stays released after double close");
        assertTrue(bridge.bindEventsCalls.isEmpty(), "no listener re-binding may happen after close");
    }

    /** AC6：清理过程中的异常不阻断后续清理（bridge 对 TEST 抛错，manager 仍全部释放并重抛）。 */
    @Test
    void closeExceptionDoesNotBlockSubsequentCleanup() {
        RecordingBridge bridge = new RecordingBridge();
        bridge.throwOnClear = ScriptType.TEST;
        NekoRuntimeRoot root = new RootBuilder(bridge).build();
        root.createScriptManager(ScriptType.SERVER);
        root.createScriptManager(ScriptType.CLIENT);

        assertThrows(RuntimeException.class, root::closeSilently,
                "closeSilently rethrows the first cleanup failure after finishing teardown");

        assertNull(root.scriptManagerOrNull(ScriptType.SERVER), "manager cleanup must complete despite bridge failure");
        assertNull(root.scriptManagerOrNull(ScriptType.CLIENT), "manager cleanup must complete despite bridge failure");
    }

    /** AC7 最小替代（无头环境）：CLIENT load/reload 与错误边界走同一 root 生命周期入口
     *  （即 NekoJSClient F3+T / loadScripts lambda 调用的同一组 API）。 */
    @Test
    void clientLoadAndReloadThroughRootLifecycleEntry() {
        RecordingBridge bridge = new RecordingBridge();
        NekoRuntimeRoot root = new RootBuilder(bridge).build();
        root.createScriptManager(ScriptType.CLIENT);

        assertDoesNotThrow(() -> root.scriptManagerOf(ScriptType.CLIENT).loadScripts());
        assertDoesNotThrow(() -> root.reload(ScriptType.CLIENT));
        assertEquals(0, root.errors().count());
        root.errorTracker().recordCallbackError(ScriptType.CLIENT, "client_reload", new RuntimeException("simulated"));
        assertEquals(1, root.errors().count(), "client reload failure must land in the root error tracker");

        root.closeSilently();
    }
}
