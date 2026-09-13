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
import com.tkisor.nekojs.core.lifecycle.NekoReloadException;
import com.tkisor.nekojs.core.lifecycle.ReloadPhase;
import com.tkisor.nekojs.script.prop.ScriptProperty;
import com.tkisor.nekojs.script.prop.ScriptPropertyRegistry;
import com.tkisor.nekojs.testfixture.TestPlatformInit;
import graal.graalvm.polyglot.Engine;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

/**
 * 诊断用（ticket 06 烟测设计输入 + 缺陷复现）：time-window runaway 路径是否真的能终止
 * {@code while(true)}。
 *
 * <p><b>结论（2026-09-12，@Disabled：当前实现下必然超时，不能让 check 变红）</b>：
 * {@code scriptStatementLimit = 0} + {@code scriptRunawayTimeoutSeconds = 2} 时，
 * {@code while(true){}} 在 20s 断言窗口内<strong>没有</strong>被终止（线程仍停在
 * {@code DefaultLoopNode.execute / WhileNode.execute}）；同一 runner 在真实
 * {@code :26.1.2:runServer} 会话里也复现（注入脚本后 RCON 180s 无应答，日志无
 * ResourceLimits 行）。语句上限路径（{@code scriptStatementLimit > 0}）有效，是现有
 * 回归测试（{@code infiniteLoopInScriptEntryDoesNotFreezeServerThread} 等）唯一验证过的机制。
 *
 * <p>因此本测试对 ticket 06 的作用是：候选被资源上限终止的<strong>接收面</strong>
 * （candidateKilled → 丢弃候选、保留 active）可用语句上限路径确定性验证；时间窗口路径
 * 的失效属既有 core 缺陷（watchdog 调度归工单 07 的 owner-thread/watchdog 面），
 * 见 REPORT.md 的 not-verified 与发现节。修好后把本注解去掉即为通过证据。
 */
@Disabled("finding: time-window runaway path does not terminate while(true); see REPORT.md not-verified")
class Ticket06RunawayProbeTest {

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

    private static ScriptPropertyRegistry props() {
        var impl = new ScriptPropertyRegistry.Impl();
        impl.register(ScriptProperty.AFTER);
        impl.register(ScriptProperty.MODLOADED);
        impl.register(ScriptProperty.DISABLE);
        impl.register(ScriptProperty.PRIORITY);
        impl.freeze();
        return impl;
    }

    /** timeout=5s 求值超时、statementLimit=0、runaway=2s：只靠时间窗口路径终止死循环。 */
    private static ScriptManager manager(SandboxConfig config, Engine engine) {
        NekoJSPaths paths = NekoJSPaths.get();
        StubPluginRuntime pluginRuntime = new StubPluginRuntime();
        DefaultErrorTracker tracker = new DefaultErrorTracker(paths, config);
        NekoCoreContext core = new NekoCoreContext(engine, config, new ClassFilter(config), tracker);
        NekoSandboxFactory factory = new NekoSandboxFactory(
                core, paths, ScriptCompilerRegistry.createRuntimeRegistry(), pluginRuntime);
        ScriptEnvironmentFactory envFactory = new ScriptEnvironmentFactory(ScriptEventBridge.EMPTY, pluginRuntime, factory);
        return new ScriptManager(ScriptType.SERVER, ScriptEventBridge.EMPTY, pluginRuntime,
                props(), tracker, paths, config, envFactory);
    }

    @Test
    void runawayWindowAloneTerminatesInfiniteLoop() throws Exception {
        Path dir = ScriptTypeEnv.scriptsDir(ScriptType.SERVER);
        Files.createDirectories(dir);
        Files.writeString(dir.resolve("loop.js"), "while (true) { /* spin forever */ }\n");
        Engine engine = Engine.newBuilder().build();
        SandboxConfig config = new SandboxConfig(false, false, false, false, true, true, false, true, 5, 0L, 2);
        ScriptManager manager = manager(config, engine);
        try {
            manager.discoverScripts();
            assertTimeoutPreemptively(Duration.ofSeconds(20), () -> {
                NekoReloadException failure = assertThrows(NekoReloadException.class, manager::reloadScripts);
                assertEquals(ReloadPhase.EXECUTION, failure.report().phase());
            }, "runaway window (2s) must terminate the infinite loop without the statement cap");
        } finally {
            Path lock = dir.resolve("loop.js");
            Files.deleteIfExists(lock);
            manager.close();
            engine.close();
        }
    }
}
