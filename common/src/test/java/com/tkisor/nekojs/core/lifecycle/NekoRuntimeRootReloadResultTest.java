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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
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
        return newRoot(bridge, SandboxConfig.defaultConfig());
    }

    private static NekoRuntimeRoot newRoot(ScriptEventBridge bridge, SandboxConfig config) {
        NekoJSPaths paths = NekoJSPaths.get();
        DefaultErrorTracker tracker = new DefaultErrorTracker(paths, config);
        NekoCoreContext core = new NekoCoreContext(
                graal.graalvm.polyglot.Engine.newBuilder().build(), config, ClassFilter.INSTANCE, tracker);
        ScriptCompilerRegistry compilers = ScriptCompilerRegistry.createRuntimeRegistry();
        NekoModulePipelineCache cache = new NekoModulePipelineCache(
                new NekoModulePipeline(new NekoCompilationPipeline(), compilers, config),
                new SourceMapRegistry(paths.root()), new NekoEsmVirtualModuleRegistry(paths.root()),
                NekoTrustContext.local());
        NekoSandboxFactory sandboxFactory = new NekoSandboxFactory(
                core, paths, compilers, new StubPluginRuntime(), cache);
        return new NekoRuntimeRoot(core, new StubPluginRuntime(), bridge,
                newPropertyRegistry(), sandboxFactory, cache);
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

            NekoRuntimeRoot.ReloadResult fileResult = root.reloadFile(ScriptType.STARTUP, Path.of("marker.js"));
            assertTrue(fileResult.success());
            assertEquals(ReloadPhase.STARTUP, fileResult.phase(),
                    "targeted STARTUP reload must retain the full reset+load phase");
            assertTrue(fileResult.requiresLoaderRestart());
        } finally {
            root.closeSilently();
        }
    }

    @Test
    void testRunReportsUnconfiguredWithoutCreatingAManager() {
        NekoRuntimeRoot root = newRoot(ScriptEventBridge.EMPTY);
        try {
            var result = root.runTests();
            assertFalse(result.isConfigured());
            assertFalse(result.isCompleted());
            assertNull(root.scriptManagerOrNull(ScriptType.TEST),
                    "an unconfigured TEST command must not create a partial manager");
        } finally {
            root.closeSilently();
        }
    }

    /** AC3/阶段结果：事务式 reload 成功结果的 phase 是 COMMIT；单文件失败结果的 phase 是 FILE. */
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

            NekoRuntimeRoot.ReloadResult fileSuccess = root.reloadFile(ScriptType.SERVER, Path.of("marker.js"));
            assertTrue(fileSuccess.success());
            assertEquals(ReloadPhase.FILE, fileSuccess.phase(),
                    "single-file reload success must keep the FILE phase");
            assertTrue(fileSuccess.sourceLocation().endsWith("marker.js"));

            NekoRuntimeRoot.ReloadResult fileResult = root.reloadFile(ScriptType.SERVER,
                    ScriptTypeEnv.scriptsDir(ScriptType.SERVER).resolve("missing-file.js"));
            assertEquals(false, fileResult.success());
            assertEquals(ReloadPhase.FILE, fileResult.phase(),
                    "single-file reload failure must surface the FILE phase");
            assertTrue(fileResult.error() != null, "failure result carries the error");
            // 审查 A2：FILE 路径此前把 source location 算出来又被 ReloadResult 丢掉，
            // AC3「失败结果含 source location」在非候选路径不成立
            assertTrue(fileResult.sourceLocation() != null
                            && fileResult.sourceLocation().endsWith("missing-file.js"),
                    "FILE failure result must carry the source location: " + fileResult.sourceLocation());
            assertTrue(fileResult.nonTransactional(), "FILE reload is a non-candidate path (AC6 surface)");
            assertTrue(!fileResult.requiresLoaderRestart(), "FILE reload does not require a loader restart");
        } finally {
            root.closeSilently();
        }
    }

    /**
     * W1（ticket 20 watchdog smoke，P4 腿）：单文件 reload 在 active 上下文上求值失控脚本，
     * watchdog 终止的是 active——kill 被 {@code ScriptExecutor.executeEntry} 吞进错误面板
     * （无异常冒泡），但 active 已进入隔离失败。结果必须是携带隔离词汇的失败输出，
     * 而不是 "script reload completed"：命令面措辞与实际后果一致。
     */
    @Test
    void fileReloadThatWatchdogKillsTheActiveReportsIsolationFailure() throws Exception {
        // 与 live smoke 同配置口径：语句预算关闭（0），只有 2s 墙钟守卫可终止空循环，
        // kill 归因唯一落在失控 watchdog。
        SandboxConfig watchdogConfig = new SandboxConfig(false, false, false, false, true, true, false, true,
                60, 0L, 2);
        Path dir = ScriptTypeEnv.scriptsDir(ScriptType.SERVER);
        Files.createDirectories(dir);
        try (var stream = Files.list(dir)) {
            for (Path path : stream.toList()) {
                Files.deleteIfExists(path);
            }
        }
        writeScript(ScriptType.SERVER, "t20w1-good.js", "console.info('t20w1 good');\n");
        NekoRuntimeRoot root = newRoot(ScriptEventBridge.EMPTY, watchdogConfig);
        try {
            root.createScriptManager(ScriptType.SERVER).discoverScripts();
            root.scriptManagerOf(ScriptType.SERVER).loadScripts();
            assertFalse(root.isActiveFailed(ScriptType.SERVER), "baseline active must be healthy");

            writeScript(ScriptType.SERVER, "t20w1-runaway.js", "while (true) { /* spin forever */ }\n");
            NekoRuntimeRoot.ReloadResult killed = assertTimeoutPreemptively(java.time.Duration.ofSeconds(60),
                    () -> root.reloadFile(ScriptType.SERVER, Path.of("t20w1-runaway.js")),
                    "wall-clock watchdog (2s) must terminate the runaway active evaluation");

            assertTrue(root.isActiveFailed(ScriptType.SERVER),
                    "the watchdog kill must leave the active generation isolated");
            assertFalse(killed.success(),
                    "a reload whose evaluation killed the active must not report success (W1)");
            assertEquals(ReloadPhase.FILE, killed.phase(),
                    "the failure surfaces at the FILE phase of this reload");
            assertTrue(killed.sourceLocation() != null && killed.sourceLocation().endsWith("t20w1-runaway.js"),
                    "failure result carries the reload source: " + killed.sourceLocation());

            String wording = RuntimeCommandResultFormatter.reloadResult(
                    killed, root.isActiveFailed(ScriptType.SERVER));
            assertFalse(wording.contains("completed"),
                    "user-facing wording must not claim completion while the active is isolated: " + wording);
            assertTrue(wording.contains("reload failed"), wording);
            assertTrue(wording.contains("phase=FILE source=t20w1-runaway.js"), wording);
            assertTrue(wording.contains("active generation remains isolated; explicit full reload is required."),
                    "failure wording must carry the isolation vocabulary: " + wording);

            // P5（既有隔离拒绝）不受影响：杀过之后的下一次 FILE reload 在入口即被拒绝。
            NekoRuntimeRoot.ReloadResult refused = root.reloadFile(ScriptType.SERVER, Path.of("t20w1-good.js"));
            assertFalse(refused.success(), "post-isolation FILE reload stays refused");
            assertEquals(ReloadPhase.PREPARATION, refused.phase());
            assertTrue(RuntimeCommandResultFormatter.reloadResult(
                            refused, root.isActiveFailed(ScriptType.SERVER))
                            .contains("active generation remains isolated; explicit full reload is required."),
                    "the pre-existing isolation refusal keeps its wording");
        } finally {
            Files.deleteIfExists(dir.resolve("t20w1-runaway.js"));
            root.closeSilently();
        }
    }
}
