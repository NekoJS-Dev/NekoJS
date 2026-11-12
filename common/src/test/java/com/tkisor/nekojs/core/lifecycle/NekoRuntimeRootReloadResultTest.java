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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
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

    private static class StubPluginRuntime implements IPluginRuntime {
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

    @BeforeEach
    void clearTestScriptDirectories() throws java.io.IOException {
        for (ScriptType type : ScriptType.values()) {
            Path scripts = ScriptTypeEnv.scriptsDir(type);
            Files.createDirectories(scripts);
            try (var files = Files.walk(scripts)) {
                for (Path path : files.filter(path -> !path.equals(scripts))
                        .sorted(java.util.Comparator.reverseOrder()).toList()) {
                    Files.deleteIfExists(path);
                }
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

    /**
     * 独立 Engine：NekoSharedEngine 的 HostAccess 按 factory 实例身份比较（生产进程只有
     * 一份装配 factory），多 root 各建 factory 会撞共享引擎约束——本测试不依赖该进程级例外。
     */
    private static NekoRuntimeRoot newRoot(ScriptEventBridge bridge) {
        return newRoot(bridge, SandboxConfig.defaultConfig());
    }

    private static NekoRuntimeRoot newRoot(ScriptEventBridge bridge, SandboxConfig config) {
        return newRoot(bridge, config, new StubPluginRuntime());
    }

    private static NekoRuntimeRoot newRoot(ScriptEventBridge bridge, SandboxConfig config, IPluginRuntime runtime) {
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
                core, paths, compilers, runtime, cache);
        return new NekoRuntimeRoot(core, runtime, bridge,
                newPropertyRegistry(), sandboxFactory, cache);
    }

    @Test
    void mixinHooksUseCandidateRollbackDuplicatePreflightAndRootLeases() throws Exception {
        Path dir = ScriptTypeEnv.scriptsDir(ScriptType.SERVER);
        Files.createDirectories(dir);
        try (var files = Files.list(dir)) { for (Path file : files.toList()) Files.deleteIfExists(file); }
        String hash = "a".repeat(64);
        var profile = new com.tkisor.nekojs.core.mixin.MixinPlan.Profile("26.1.2", "neoforge", "26.1.2.71", "runtime", "client");
        var hook = new com.tkisor.nekojs.core.mixin.MixinPlan.Hook("demo:health", "demo.Target", "health", "()F", "RETURN", true, "SERVER", "SERVER_MAIN", 1, false);
        var plan = new com.tkisor.nekojs.core.mixin.MixinPlan(1, profile, hash, java.util.List.of(hook));
        var root = newRoot(ScriptEventBridge.EMPTY);
        var mainThread = Thread.currentThread();
        root.mixins().install(java.util.List.of(plan), profile, thread -> Thread.currentThread() == mainThread);
        try {
            writeScript(ScriptType.SERVER, "mixin-hook.js", "MixinEvents.inject('demo:health', e => { e.returnValue = 40; });");
            root.createScriptManager(ScriptType.SERVER).discoverScripts(); root.scriptManagerOf(ScriptType.SERVER).loadScripts();
            assertTrue(root.errorTracker().getAllErrors().isEmpty(), () -> root.errorTracker().getAllErrors().stream().map(error -> error.getFullDetailText()).toList().toString());
            assertTrue(com.tkisor.nekojs.core.mixin.MixinNativeBridge.enabled(hash, hook.id()), () -> "candidate=" + com.tkisor.nekojs.script.ScriptManager.isAnyCandidatePreparing());
            assertEquals(40F, com.tkisor.nekojs.core.mixin.MixinNativeBridge.invoke(hash, hook.id(), null, new Object[0], 20F).value(), () -> root.errorTracker().getAllErrors().stream().map(error -> error.getFullDetailText()).toList().toString());
            writeScript(ScriptType.SERVER, "mixin-hook.js", "MixinEvents.inject('demo:health', e => { e.returnValue = 41; });");
            assertTrue(root.reload(ScriptType.SERVER).success());
            assertEquals(41F, com.tkisor.nekojs.core.mixin.MixinNativeBridge.invoke(hash, hook.id(), null, new Object[0], 20F).value());
            writeScript(ScriptType.SERVER, "mixin-hook.js", "MixinEvents.inject('demo:health', e => { e.returnValue = 99; }); throw new Error('rollback fixture');");
            assertThrows(NekoReloadException.class, () -> root.reload(ScriptType.SERVER));
            assertEquals(41F, com.tkisor.nekojs.core.mixin.MixinNativeBridge.invoke(hash, hook.id(), null, new Object[0], 20F).value());
            writeScript(ScriptType.SERVER, "mixin-hook.js", "MixinEvents.inject('demo:health', e => {}); MixinEvents.inject('demo:health', e => {});");
            assertThrows(NekoReloadException.class, () -> root.reload(ScriptType.SERVER));
            assertEquals(41F, com.tkisor.nekojs.core.mixin.MixinNativeBridge.invoke(hash, hook.id(), null, new Object[0], 20F).value());
            writeScript(ScriptType.SERVER, "mixin-hook.js", "MixinEvents.inject('demo:health', e => { e.returnValue = 99; return Promise.resolve(); });");
            assertTrue(root.reload(ScriptType.SERVER).success());
            assertFalse(com.tkisor.nekojs.core.mixin.MixinNativeBridge.invoke(hash, hook.id(), null, new Object[0], 20F).changed());
            var wrongThread = java.util.concurrent.CompletableFuture.supplyAsync(() -> com.tkisor.nekojs.core.mixin.MixinNativeBridge.enabled(hash, hook.id()));
            assertFalse(wrongThread.get());
            root.mixins().clearCallbacks();
            assertFalse(com.tkisor.nekojs.core.mixin.MixinNativeBridge.enabled(hash, hook.id()));
            writeScript(ScriptType.SERVER, "mixin-hook.js", "MixinEvents.inject('demo:health', e => { e.returnValue = 42; });");
            assertTrue(root.reload(ScriptType.SERVER).success());
            assertEquals(42F, com.tkisor.nekojs.core.mixin.MixinNativeBridge.invoke(hash, hook.id(), null, new Object[0], 20F).value());
        } finally { root.closeSilently(); }
        assertFalse(com.tkisor.nekojs.core.mixin.MixinNativeBridge.enabled(hash, hook.id()));
    }

    private static void writeScript(ScriptType type, String fileName, String source) throws Exception {
        Path dir = ScriptTypeEnv.scriptsDir(type);
        Files.createDirectories(dir);
        Files.writeString(dir.resolve(fileName), source);
        dir.resolve(fileName).toFile().deleteOnExit();
    }

    public record SourceTick(int tick) {}

    @Test
    void stableTickSnapshotsUseSourceValuesAcrossSuccessfulAndFailedReloadWithoutDuplicateListeners() throws Exception {
        var registry = new com.tkisor.nekojs.api.event.EventGroupRegistry.Impl().scopedTo("nekojs");
        var group = EventGroup.of("ServerEvents");
        var pre = group.server("tickPre", SourceTick.class);
        var post = group.server("tickPost", SourceTick.class);
        registry.register(group);
        registry.register(group, e -> e.stable().scriptTypes(ScriptType.SERVER).events("tickPre", "tickPost")
                .view("tickPre", com.tkisor.nekojs.api.event.StableServerTickEvent.class,
                        (SourceTick event) -> new com.tkisor.nekojs.api.event.StableServerTickEvent(event.tick()))
                .view("tickPost", com.tkisor.nekojs.api.event.StableServerTickEvent.class,
                        (SourceTick event) -> new com.tkisor.nekojs.api.event.StableServerTickEvent(event.tick())));
        var seen = new java.util.ArrayList<String>();
        var recorder = (graal.graalvm.polyglot.proxy.ProxyExecutable) arguments -> {
            seen.add(arguments[0].asString() + ":" + arguments[1].asLong());
            return null;
        };
        var runtime = new StubPluginRuntime() {
            @Override public Map<String, Binding> bindings(ScriptType type) { return Map.of("recordTick", Binding.of("recordTick", recorder)); }
            @Override public Map<String, EventGroup> eventGroups() { return registry.view(); }
            @Override public List<com.tkisor.nekojs.api.event.ModuleEventGroup> moduleEventGroups() { return registry.moduleGroups(); }
            @Override public List<EventGroup> lifecycleEventGroups() { return registry.sources(); }
        };
        var bridge = new com.tkisor.nekojs.core.DefaultScriptEventBridge(null);
        bridge.setPluginRuntime(runtime);
        Path dir = ScriptTypeEnv.scriptsDir(ScriptType.SERVER);
        Files.createDirectories(dir);
        try (var stream = Files.list(dir)) {
            for (Path path : stream.toList()) Files.deleteIfExists(path);
        }
        String source = "const { ServerEvents: ticks } = require('nekojs/api/stable');\n"
                + "ticks.tickPre(e => recordTick('v1-pre', e.tick));\n"
                + "ticks.tickPost(e => recordTick('v1-post', e.tick));\n";
        writeScript(ScriptType.SERVER, "stable-ticks.js", source);
        var root = newRoot(bridge, SandboxConfig.defaultConfig(), runtime);
        try {
            root.createScriptManager(ScriptType.SERVER).discoverScripts();
            root.scriptManagerOf(ScriptType.SERVER).loadScripts();
            pre.post(new SourceTick(37));
            post.post(new SourceTick(37));
            assertEquals(List.of("v1-pre:37", "v1-post:37"), seen);
            writeScript(ScriptType.SERVER, "stable-ticks.js", source.replace("v1-", "v2-"));
            assertTrue(root.reload(ScriptType.SERVER).success());
            pre.post(new SourceTick(91));
            post.post(new SourceTick(91));
            assertEquals(List.of("v1-pre:37", "v1-post:37", "v2-pre:91", "v2-post:91"), seen);
            writeScript(ScriptType.SERVER, "stable-ticks.js", source.replace("v1-", "candidate-") + "throw new Error('fixture rollback');");
            org.junit.jupiter.api.Assertions.assertThrows(NekoReloadException.class, () -> root.reload(ScriptType.SERVER));
            pre.post(new SourceTick(105));
            post.post(new SourceTick(105));
            assertEquals(List.of("v1-pre:37", "v1-post:37", "v2-pre:91", "v2-post:91", "v2-pre:105", "v2-post:105"), seen);
        } finally {
            root.closeSilently();
            Files.deleteIfExists(dir.resolve("stable-ticks.js"));
        }
        assertFalse(pre.hasListeners());
        assertFalse(post.hasListeners());
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

    public static final class CancellationFixture {
        public void cancelCurrentContext() throws ReflectiveOperationException {
            var borrowed = graal.graalvm.polyglot.Context.getCurrent();
            var field = com.tkisor.nekojs.script.ScriptManager.class.getDeclaredField("CONTEXT_TO_MANAGER");
            field.setAccessible(true);
            // Only the registered creator wrapper owns the right to cancel its Context.
            for (Object key : ((Map<?, ?>) field.get(null)).keySet()) {
                if (key instanceof graal.graalvm.polyglot.Context owner && owner.equals(borrowed)) {
                    owner.close(true);
                    return;
                }
            }
            throw new AssertionError("Current Context has no registered creator wrapper");
        }
    }

    @Test
    void fileReloadThatCancelsTheActiveReportsIsolationFailure() throws Exception {
        Path dir = ScriptTypeEnv.scriptsDir(ScriptType.SERVER);
        Files.createDirectories(dir);
        try (var stream = Files.list(dir)) {
            for (Path path : stream.toList()) Files.deleteIfExists(path);
        }
        writeScript(ScriptType.SERVER, "cancel-good.js", "console.info('good');\n");
        var runtime = new StubPluginRuntime() {
            @Override public Map<String, Binding> bindings(ScriptType type) {
                return Map.of("Cancellation", Binding.of("Cancellation", new CancellationFixture()));
            }
        };
        NekoRuntimeRoot root = newRoot(ScriptEventBridge.EMPTY, SandboxConfig.defaultConfig(), runtime);
        try {
            root.createScriptManager(ScriptType.SERVER).discoverScripts();
            root.scriptManagerOf(ScriptType.SERVER).loadScripts();
            assertFalse(root.isActiveFailed(ScriptType.SERVER), "baseline active must be healthy");
            writeScript(ScriptType.SERVER, "cancel-active.js", "Cancellation.cancelCurrentContext();\n");
            NekoRuntimeRoot.ReloadResult cancelled = assertTimeoutPreemptively(java.time.Duration.ofSeconds(60),
                    () -> root.reloadFile(ScriptType.SERVER, Path.of("cancel-active.js")));
            assertTrue(root.isActiveFailed(ScriptType.SERVER), "explicit cancellation isolates the active generation");
            assertFalse(cancelled.success(), "a cancelled active reload must not report success");
            assertEquals(ReloadPhase.FILE, cancelled.phase());
            assertTrue(cancelled.error() != null);
            assertEquals("script evaluation was terminated and the active generation is isolated", cancelled.error().getMessage());
            assertTrue(cancelled.sourceLocation() != null && cancelled.sourceLocation().endsWith("cancel-active.js"));
            String wording = RuntimeCommandResultFormatter.reloadResult(cancelled, root.isActiveFailed(ScriptType.SERVER));
            assertFalse(wording.contains("completed"), wording);
            assertTrue(wording.contains("reload failed"), wording);
            assertTrue(wording.contains("phase=FILE source=cancel-active.js"), wording);
            assertTrue(wording.contains("active generation remains isolated; explicit full reload is required."), wording);
            NekoRuntimeRoot.ReloadResult refused = root.reloadFile(ScriptType.SERVER, Path.of("cancel-good.js"));
            assertFalse(refused.success(), "post-cancellation FILE reload stays refused");
            assertEquals(ReloadPhase.PREPARATION, refused.phase());
            assertTrue(RuntimeCommandResultFormatter.reloadResult(refused, root.isActiveFailed(ScriptType.SERVER))
                    .contains("active generation remains isolated; explicit full reload is required."));
        } finally {
            root.closeSilently();
            Files.deleteIfExists(dir.resolve("cancel-active.js"));
            Files.deleteIfExists(dir.resolve("cancel-good.js"));
        }
    }
}
