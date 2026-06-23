package com.tkisor.nekojs.core.lifecycle;

import com.tkisor.nekojs.api.compiler.ScriptCompilerRegistry;
import com.tkisor.nekojs.api.plugin.IPluginRuntime;
import com.tkisor.nekojs.core.ScriptEventBridge;
import com.tkisor.nekojs.core.context.NekoCoreContext;
import com.tkisor.nekojs.core.error.ScriptError;
import com.tkisor.nekojs.script.ScriptManager;
import com.tkisor.nekojs.script.ScriptType;
import com.tkisor.nekojs.script.prop.ScriptPropertyRegistry;

import java.nio.file.Path;
import java.util.Collection;
import java.util.EnumMap;
import java.util.Map;

/**
 * 平台 composition root 的返回对象，由平台入口（mod entry）持有。
 *
 * <p>内部持有完整对象图，但默认对 platform 暴露 lifecycle API，而不是暴露宽对象图 getter。
 * 公开 surface 优先是 {@link #reload(ScriptType)}、{@link #reloadFile(ScriptType, Path)}、
 * {@link #runTests()}、{@link #errors()}、{@link #close()} 这类命令式 lifecycle API；
 * 不要让 platform 调用点重新拼装业务流程。
 *
 * <p>约束（见 {@code ai_arch/architecture.md} §4.2 与 {@code plan.md} Phase 2.3）：
 * <ul>
 *   <li>不提供全局 {@code get()} / {@code current()}。</li>
 *   <li>只由 platform mod entry、command/reload/shutdown lifecycle 边界持有。</li>
 *   <li>不传入 {@code ScriptManager}、sandbox factory、module loader、catalog provider 等普通业务服务——
 *       script managers 由 root 内部按 {@link ScriptType} 持有，不在构造器外部注入。</li>
 *   <li>不要把 root 继续往下传。</li>
 *   <li>内部对象图字段默认不生成 public getter；确实需要 getter 时必须限制在 platform lifecycle /
 *       bootstrap 边界，并在 allowlist 中记录原因。</li>
 * </ul>
 *
 * <p>{@link #close()} 顺序（ownership 表）：script managers → event bridge / listeners →
 * module/runtime resources → core-level resources。
 */
public final class NekoRuntimeRoot implements AutoCloseable {

    private final NekoCoreContext core;
    private final IPluginRuntime pluginRuntime;
    private final ScriptCompilerRegistry compilers;
    private final ScriptEventBridge eventBridge;
    private final ScriptPropertyRegistry scriptProperties;
    private final Map<ScriptType, ScriptManager> scriptManagers;
    private final ResourceTracker resources;

    public NekoRuntimeRoot(
            NekoCoreContext core,
            IPluginRuntime pluginRuntime,
            ScriptCompilerRegistry compilers,
            ScriptEventBridge eventBridge,
            ScriptPropertyRegistry scriptProperties
    ) {
        this.core = core;
        this.pluginRuntime = pluginRuntime;
        this.compilers = compilers;
        this.eventBridge = eventBridge;
        this.scriptProperties = scriptProperties;
        this.scriptManagers = new EnumMap<>(ScriptType.class);
        this.resources = new ResourceTracker();
    }

    public void registerScriptManager(ScriptType type, ScriptManager manager) {
        scriptManagers.put(type, manager);
    }

    public ScriptManager scriptManagerOf(ScriptType type) {
        ScriptManager manager = scriptManagers.get(type);
        if (manager == null) {
            throw new IllegalStateException("No ScriptManager registered for " + type);
        }
        return manager;
    }

    public ScriptManager scriptManagerOrNull(ScriptType type) {
        return scriptManagers.get(type);
    }

    public ScriptManager createScriptManager(ScriptType type) {
        return new ScriptManager(type, eventBridge, scriptProperties, core.errorTracker(), core.paths(), core.sandboxConfig());
    }

    public ReloadResult reload(ScriptType type) {
        ScriptManager manager = scriptManagerOf(type);
        manager.reloadScripts();
        return ReloadResult.success(type);
    }

    public ReloadResult reloadFile(ScriptType type, Path file) {
        ScriptManager manager = scriptManagerOf(type);
        try {
            manager.reloadScriptFile(file.toString());
            return ReloadResult.success(type);
        } catch (Exception e) {
            return ReloadResult.failure(type, e);
        }
    }

    public TestRunResult runTests() {
        ScriptManager testManager = scriptManagers.get(ScriptType.TEST);
        if (testManager == null) {
            return TestRunResult.notConfigured();
        }
        testManager.runTestScripts();
        return TestRunResult.completed();
    }

    public ErrorSnapshot errors() {
        return ErrorSnapshot.of(core.errorTracker());
    }

    @Override
    public void close() {
        closeSilently();
    }

    public void closeSilently() {
        Throwable first = null;
        for (ScriptType type : scriptManagers.keySet()) {
            ScriptManager manager = scriptManagers.get(type);
            try {
                flushAndCloseManager(type, manager);
            } catch (Throwable t) {
                if (first == null) first = t;
                else first.addSuppressed(t);
            }
        }
        scriptManagers.clear();
        try {
            for (ScriptType type : ScriptType.values()) {
                eventBridge.clearListeners(type);
            }
        } catch (Throwable t) {
            if (first == null) first = t;
            else first.addSuppressed(t);
        }
        try {
            resources.close();
        } catch (Throwable t) {
            if (first == null) first = t;
            else first.addSuppressed(t);
        }
        if (first != null) {
            if (first instanceof Error e) throw e;
            if (first instanceof RuntimeException re) throw re;
            throw new RuntimeException("NekoRuntimeRoot close failed", first);
        }
    }

    private void flushAndCloseManager(ScriptType type, ScriptManager manager) {
        try {
            manager.flushReadyNodeTimers();
        } catch (Throwable ignored) {
        }
    }

    public record ReloadResult(ScriptType type, boolean success, Throwable error) {
        public static ReloadResult success(ScriptType type) {
            return new ReloadResult(type, true, null);
        }

        public static ReloadResult failure(ScriptType type, Throwable error) {
            return new ReloadResult(type, false, error);
        }
    }

    public record TestRunResult(boolean isConfigured, boolean isCompleted) {
        public static TestRunResult completed() {
            return new TestRunResult(true, true);
        }

        public static TestRunResult notConfigured() {
            return new TestRunResult(false, false);
        }
    }

    public record ErrorSnapshot(Collection<ScriptError> errors, int count) {
        public static ErrorSnapshot of(com.tkisor.nekojs.core.error.ErrorTracker tracker) {
            Collection<ScriptError> all = tracker.getAllErrors();
            return new ErrorSnapshot(all, all.size());
        }
    }
}
