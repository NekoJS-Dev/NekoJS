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
import com.tkisor.nekojs.core.compiler.ScriptCompilerRegistry;
import com.tkisor.nekojs.core.config.SandboxConfig;
import com.tkisor.nekojs.core.error.DefaultErrorTracker;
import com.tkisor.nekojs.core.fs.ClassFilter;
import com.tkisor.nekojs.core.fs.NekoJSPaths;
import com.tkisor.nekojs.script.ScriptTypeEnv;
import com.tkisor.nekojs.script.prop.ScriptProperty;
import com.tkisor.nekojs.script.prop.ScriptPropertyRegistry;
import com.tkisor.nekojs.testfixture.TestPlatformInit;
import graal.graalvm.polyglot.Value;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ticket 06 AC6 / AC3：reload 入口的阶段结果契约——
 * <ul>
 *   <li>事务式 reload（SERVER/CLIENT/TEST）成功结果的 phase 是 COMMIT（候选+commit）；</li>
 *   <li>STARTUP reload 保持显式非事务边界（reset+load，不可逆平台注册不做候选/commit），
 *       结果 phase 是 STARTUP——入口不把 STARTUP 重载宣称成候选事务成功；</li>
 *   <li>单文件重载失败结果的 phase 是 FILE 且携带 source location。</li>
 * </ul>
 */
class NekoRuntimeRootReloadResultTest {

    private static final class StubPluginRuntime implements IPluginRuntime {
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

    private static ScriptPropertyRegistry newPropertyRegistry() {
        var impl = new ScriptPropertyRegistry.Impl();
        impl.register(ScriptProperty.AFTER);
        impl.register(ScriptProperty.MODLOADED);
        impl.register(ScriptProperty.DISABLE);
        impl.register(ScriptProperty.PRIORITY);
        impl.freeze();
        return impl;
    }

    /**
     * 独立 Engine：NekoSharedEngine 的 HostAccess 按 factory 实例身份比较（生产进程只有
     * 一份装配 factory），多 root 各建 factory 会撞共享引擎约束——本测试不依赖该进程级例外。
     */
    private static NekoRuntimeRoot newRoot(ScriptEventBridge bridge) {
        NekoJSPaths paths = NekoJSPaths.get();
        SandboxConfig config = SandboxConfig.defaultConfig();
        DefaultErrorTracker tracker = new DefaultErrorTracker(paths, config);
        NekoCoreContext core = new NekoCoreContext(
                graal.graalvm.polyglot.Engine.newBuilder().build(), config, ClassFilter.INSTANCE, tracker);
        NekoSandboxFactory sandboxFactory = new NekoSandboxFactory(
                core, paths, ScriptCompilerRegistry.createRuntimeRegistry(), new StubPluginRuntime());
        return new NekoRuntimeRoot(core, new StubPluginRuntime(), bridge,
                newPropertyRegistry(), sandboxFactory);
    }

    private static void writeScript(ScriptType type, String fileName, String source) throws Exception {
        Path dir = ScriptTypeEnv.scriptsDir(type);
        Files.createDirectories(dir);
        Files.writeString(dir.resolve(fileName), source);
        dir.resolve(fileName).toFile().deleteOnExit();
    }

    /** AC6：STARTUP reload 保持 reset+load 非事务语义，结果显式标记 STARTUP 阶段。 */
    @Test
    void startupReloadKeepsExplicitNonTransactionalBoundary() throws Exception {
        writeScript(ScriptType.STARTUP, "marker.js", "console.info('startup marker');\n");
        NekoRuntimeRoot root = newRoot(ScriptEventBridge.EMPTY);
        try {
            root.createScriptManager(ScriptType.STARTUP).discoverScripts();
            root.scriptManagerOf(ScriptType.STARTUP).loadScripts();

            NekoRuntimeRoot.ReloadResult result = root.reload(ScriptType.STARTUP);
            assertTrue(result.success(), "STARTUP reload keeps working (reset+load semantics)");
            assertEquals(ReloadPhase.STARTUP, result.phase(),
                    "STARTUP reload must be explicitly marked as the non-transactional boundary (AC6)");
            assertTrue(result.generation() >= 0, "result carries the generation");
            // AC6：入口给外部调用方的显式判定面——非事务路径必须可被识别，且显式要求
            // loader restart（不可逆平台注册不回滚），否则只读 success() 的调用方会把
            // reset+load 读成候选 + commit 事务成功。
            assertTrue(result.nonTransactional(), "STARTUP result must be identifiable as non-transactional");
            assertTrue(result.requiresLoaderRestart(), "STARTUP result must ask the caller for a loader restart");
        } finally {
            root.closeSilently();
        }
    }

    /** AC3/阶段结果：事务式 reload 成功结果的 phase 是 COMMIT；单文件失败结果的 phase 是 FILE。 */
    @Test
    void reloadResultPhasesExposeCommitAndFileBoundaries() throws Exception {
        writeScript(ScriptType.SERVER, "marker.js", "console.info('server marker');\n");
        NekoRuntimeRoot root = newRoot(ScriptEventBridge.EMPTY);
        try {
            root.createScriptManager(ScriptType.SERVER).discoverScripts();
            root.scriptManagerOf(ScriptType.SERVER).loadScripts();

            NekoRuntimeRoot.ReloadResult result = root.reload(ScriptType.SERVER);
            assertTrue(result.success());
            assertEquals(ReloadPhase.COMMIT, result.phase(),
                    "transactional reload success must surface the commit boundary");
            assertTrue(!result.nonTransactional(), "transactional reload must not be marked non-transactional");
            assertTrue(!result.requiresLoaderRestart(), "transactional reload must not ask for a loader restart");

            NekoRuntimeRoot.ReloadResult fileResult = root.reloadFile(ScriptType.SERVER,
                    ScriptTypeEnv.scriptsDir(ScriptType.SERVER).resolve("missing-file.js"));
            assertEquals(false, fileResult.success());
            assertEquals(ReloadPhase.FILE, fileResult.phase(),
                    "single-file reload failure must surface the FILE phase");
            assertTrue(fileResult.error() != null, "failure result carries the error");
            assertTrue(fileResult.nonTransactional(), "FILE reload is a non-candidate path (AC6 surface)");
            assertTrue(!fileResult.requiresLoaderRestart(), "FILE reload does not require a loader restart");
        } finally {
            root.closeSilently();
        }
    }
}
