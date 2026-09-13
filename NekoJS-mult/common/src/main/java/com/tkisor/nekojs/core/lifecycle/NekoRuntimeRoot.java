package com.tkisor.nekojs.core.lifecycle;

import com.tkisor.nekojs.api.plugin.IPluginRuntime;
import com.tkisor.nekojs.core.NekoSandboxFactory;
import com.tkisor.nekojs.core.ScriptEventBridge;
import com.tkisor.nekojs.core.NekoCoreContext;
import com.tkisor.nekojs.core.error.ErrorTracker;
import com.tkisor.nekojs.core.error.ScriptError;
import com.tkisor.nekojs.core.fs.NekoJSPaths;
import com.tkisor.nekojs.script.ScriptEnvironmentFactory;
import com.tkisor.nekojs.script.ScriptManager;
import com.tkisor.nekojs.api.ScriptType;
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
 * {@link #runTests()}、{@link #errors()}、{@link #close()} 这类命令式 lifecycle API。
 *
 * <ul>
 *   <li>不提供全局 {@code get()} / {@code current()}。</li>
 *   <li>只由 platform mod entry、command/reload/shutdown lifecycle 边界持有。</li>
 *   <li>不要把 root 继续往下传给普通业务类。</li>
 * </ul>
 *
 * <p>{@link #close()} 顺序：script managers → event bridge / listeners → resources。
 */
public final class NekoRuntimeRoot implements AutoCloseable {

    private final NekoCoreContext core;
    private final IPluginRuntime pluginRuntime;
    private final ScriptEventBridge eventBridge;
    private final ScriptPropertyRegistry scriptProperties;
    private final ScriptEnvironmentFactory environmentFactory;
    private final Map<ScriptType, ScriptManager> scriptManagers;
    private final ResourceTracker resources;

    public NekoRuntimeRoot(
            NekoCoreContext core,
            IPluginRuntime pluginRuntime,
            ScriptEventBridge eventBridge,
            ScriptPropertyRegistry scriptProperties,
            NekoSandboxFactory sandboxFactory
    ) {
        this.core = core;
        this.pluginRuntime = pluginRuntime;
        this.eventBridge = eventBridge;
        this.scriptProperties = scriptProperties;
        this.environmentFactory = new ScriptEnvironmentFactory(eventBridge, pluginRuntime, sandboxFactory);
        this.scriptManagers = new EnumMap<>(ScriptType.class);
        this.resources = new ResourceTracker();
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
        ScriptManager manager = new ScriptManager(type, eventBridge, pluginRuntime, scriptProperties, core.errorTracker(), NekoJSPaths.get(), core.sandboxConfig(), environmentFactory);
        scriptManagers.put(type, manager);
        return manager;
    }

    /**
     * 事务式完整 reload：候选 generation 全部阶段通过后经单一 commit 点切换（工单 06）。
     *
     * <p>失败时抛 {@link NekoReloadException}，其 {@link NekoReloadException#report()}
     * 携带结构化失败结果（generation / phase / source location / owner / domain）；
     * 候选 generation 的全部资源已随失败关闭，active 原样可用。
     */
    public ReloadResult reload(ScriptType type) {
        ScriptManager manager = scriptManagerOf(type);
        manager.reloadScripts();
        // STARTUP 的重载路径是 reset+load 非事务语义（不可逆平台注册未被域 Adapter 证明
        // 可回滚，显式 restart/unsupported 边界，见 ScriptManager#reloadScripts 的 STARTUP
        // 分支警告）——结果中显式标记，不宣称候选/commit 事务成功。
        return type == ScriptType.STARTUP
                ? ReloadResult.successNonTransactional(type, manager.generationId(), ReloadPhase.STARTUP)
                : ReloadResult.success(type, manager.generationId());
    }

    public ReloadResult reloadFile(ScriptType type, Path file) {
        ScriptManager manager = scriptManagerOf(type);
        try {
            manager.reloadScriptFile(file.toString());
            return ReloadResult.success(type, manager.generationId());
        } catch (Exception e) {
            // 单文件重载沿用 active 环境（非候选路径）：以失败结果返回并显式标记 FILE 阶段
            return ReloadResult.failure(type, manager.generationId(), ReloadPhase.FILE, file.toString(), e);
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

    public ErrorTracker errorTracker() {
        return core.errorTracker();
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
            com.tkisor.nekojs.NekoJS.LOGGER.warn("Failed to flush pending {} timers before close", type.name(), ignored);
        }
        manager.close();
    }

    /**
     * reload 结果（工单 06 阶段结果契约）。
     *
     * @param type       reload 的脚本类型
     * @param success    是否成功
     * @param error      失败原因（成功为 null；失败经由 {@link NekoReloadException} 抛出时
     *                   该字段与 report.error() 同源）
     * @param generation 成功后的 generation 序号 / 失败候选的 generation 序号
     * @param phase      结果阶段：成功为 COMMIT；STARTUP 非事务重载为 STARTUP（显式
     *                   restart/unsupported 边界）；单文件重载为 FILE
     */
    public record ReloadResult(ScriptType type, boolean success, Throwable error, long generation, ReloadPhase phase) {
        public static ReloadResult success(ScriptType type, long generation) {
            return new ReloadResult(type, true, null, generation, ReloadPhase.COMMIT);
        }

        /**
         * 结果是否来自非事务路径（{@link ReloadPhase#STARTUP} 的 reset+load、{@link ReloadPhase#FILE}
         * 的单文件重载）：这些路径<strong>不</strong>宣称候选 + commit 事务成功。
         *
         * <p>STARTUP 的不可逆平台注册（物品/方块/实体）未被域 Adapter 证明可回滚，因此该路径
         * 的调用方必须显式要求 loader restart 才能取得干净的 STARTUP 状态（AC6）；本谓词是
         * 入口给外部调用方的显式判定面，避免只读 {@link #success()} 时把非事务路径当作事务提交。
         */
        public boolean nonTransactional() {
            return phase == ReloadPhase.STARTUP || phase == ReloadPhase.FILE;
        }

        /** STARTUP 非事务重载：调用方应显式要求 loader restart（不可逆平台注册不回滚）。 */
        public boolean requiresLoaderRestart() {
            return phase == ReloadPhase.STARTUP;
        }

        /** STARTUP reset+load 重载的显式非事务结果（不宣称候选/commit 事务成功）。 */
        public static ReloadResult successNonTransactional(ScriptType type, long generation, ReloadPhase phase) {
            return new ReloadResult(type, true, null, generation, phase);
        }

        public static ReloadResult success(ScriptType type) {
            return success(type, -1);
        }

        public static ReloadResult failure(ScriptType type, Throwable error) {
            return new ReloadResult(type, false, error, -1, ReloadPhase.UNKNOWN);
        }

        public static ReloadResult failure(ScriptType type, long generation, ReloadPhase phase, String sourceLocation, Throwable error) {
            return new ReloadResult(type, false, error, generation, phase);
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
