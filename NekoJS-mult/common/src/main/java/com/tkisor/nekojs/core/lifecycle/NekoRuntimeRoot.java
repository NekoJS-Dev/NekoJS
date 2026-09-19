package com.tkisor.nekojs.core.lifecycle;

import com.tkisor.nekojs.api.plugin.IPluginRuntime;
import com.tkisor.nekojs.core.NekoSandboxFactory;
import com.tkisor.nekojs.core.ScriptEventBridge;
import com.tkisor.nekojs.core.NekoCoreContext;
import com.tkisor.nekojs.core.error.ErrorTracker;
import com.tkisor.nekojs.core.error.ScriptError;
import com.tkisor.nekojs.core.fs.NekoJSPaths;
import com.tkisor.nekojs.core.module.NekoTrustContext;
import com.tkisor.nekojs.core.module.NekoRuntimeTrustContext;
import com.tkisor.nekojs.script.ScriptEnvironmentFactory;
import com.tkisor.nekojs.script.ScriptManager;
import com.tkisor.nekojs.api.ScriptType;
import com.tkisor.nekojs.script.prop.ScriptPropertyRegistry;

import java.nio.file.Path;
import java.util.Collection;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

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
    /**
     * root 拥有的 prepared 模块缓存（票 11 W3）：模块 cache/session 生命周期归属。
     * 与 sandbox factory（module host / filesystem）共享同一实例；跨普通 reload、
     * server stop、切换世界保留（按 ScriptType 分区清理）；由 {@link #closeSilently()}
     * 全清释放。无任何 static 状态——新的独立 root / 测试 runner 从空开始，互不可见。
     */
    private final com.tkisor.nekojs.core.module.NekoModulePipelineCache preparationCache;
    private final NekoTrustContext trustContext;
    /**
     * root 拥有的受管 global/shared 状态域（票 10）：按 {@link ScriptType} 的私有 backing
     * store + 显式共享 store（工作名 shared）。跨普通 reload、server stop、切换世界保留；
     * 由 {@link #closeSilently()} 释放；generation close 只失效该 generation 的 guest 值。
     * 无任何 static 状态——新的独立 root / 测试 runner 从空开始，互不可见。
     */
    private final com.tkisor.nekojs.core.state.GlobalStateStores globalState =
            new com.tkisor.nekojs.core.state.GlobalStateStores();
    /**
     * root 拥有的候选域收集器注册表（票 39）：领域收集器（如 Item/Block modification 的
     * domain owner）由平台装配注册进 root，reload 事务的 DOMAIN_PLAN 阶段经各
     * {@link ScriptManager} 消费（见 {@code CandidateDomainCollector}）。按引用与
     * manager 共享（注册晚于 manager 创建也可见）；读取只发生在 reload 的 owner thread
     * 临界区。root close 时对 AutoCloseable 收集器逐一 close（基线恢复等清理——独立测试
     * root 不互相污染，AC5）。
     */
    private final java.util.List<com.tkisor.nekojs.core.lifecycle.CandidateDomainCollector> domainCollectors =
            new java.util.concurrent.CopyOnWriteArrayList<>();

    /**
     * 生产装配入口（票 11 W3）：与 sandbox factory 共享同一个 prepared 缓存实例
     * （见 {@code NekoRuntimeAssembly}）。
     */
    public NekoRuntimeRoot(
            NekoCoreContext core,
            IPluginRuntime pluginRuntime,
            ScriptEventBridge eventBridge,
            ScriptPropertyRegistry scriptProperties,
            NekoSandboxFactory sandboxFactory,
            com.tkisor.nekojs.core.module.NekoModulePipelineCache preparationCache
    ) {
        this(core, pluginRuntime, eventBridge, scriptProperties, sandboxFactory, preparationCache,
                NekoTrustContext.local());
    }

    public NekoRuntimeRoot(
            NekoCoreContext core,
            IPluginRuntime pluginRuntime,
            ScriptEventBridge eventBridge,
            ScriptPropertyRegistry scriptProperties,
            NekoSandboxFactory sandboxFactory,
            com.tkisor.nekojs.core.module.NekoModulePipelineCache preparationCache,
            NekoTrustContext trustContext
    ) {
        Objects.requireNonNull(sandboxFactory, "sandboxFactory");
        Objects.requireNonNull(preparationCache, "preparationCache");
        if (!sandboxFactory.usesPreparationCache(preparationCache)) {
            throw new IllegalArgumentException("NekoRuntimeRoot and NekoSandboxFactory must share the same preparation cache");
        }
        this.core = core;
        this.pluginRuntime = pluginRuntime;
        this.eventBridge = eventBridge;
        this.scriptProperties = scriptProperties;
        this.environmentFactory = new ScriptEnvironmentFactory(eventBridge, pluginRuntime, sandboxFactory, globalState);
        this.scriptManagers = new EnumMap<>(ScriptType.class);
        this.resources = new ResourceTracker();
        this.preparationCache = preparationCache;
        this.trustContext = trustContext;
    }

    /** 本 root 的 prepared cache，仅供同包生命周期诊断；reload/失效经各 manager 入口。 */
    com.tkisor.nekojs.core.module.NekoModulePipelineCache preparationCache() {
        return preparationCache;
    }

    int preparedModuleCountForDiagnostics() {
        return preparationCache.preparedEntryCount();
    }

    /** Pack activation owner injects verified remote sources and their protected cache root. */
    public void authorizeRemoteSources(java.util.Collection<NekoTrustContext.RemoteSource> sources,
                                       java.nio.file.Path remoteRoot) {
        if (!(trustContext instanceof NekoRuntimeTrustContext runtimeTrust)) {
            throw new IllegalStateException("Runtime was assembled without a mutable trust context");
        }
        runtimeTrust.authorizeRemoteSources(sources, remoteRoot);
    }

    /** Connection teardown revokes credentials while retaining protection for the cache root. */
    public void revokeRemoteSources(java.nio.file.Path remoteRoot) {
        if (trustContext instanceof NekoRuntimeTrustContext runtimeTrust) {
            runtimeTrust.revokeRemoteSources(remoteRoot);
        }
    }

    /** 本 root 的受管 global/shared 状态域（Java 侧「其他 writer」与测试观察 seam）。 */
    public com.tkisor.nekojs.core.state.GlobalStateStores globalState() {
        return globalState;
    }

    /**
     * 注册候选域收集器（票 39，平台装配期一次）：收集器实例持有领域自有状态（如
     * Item/Block modification 的基线），由 root 按生命周期归类持有——不是新的 runtime
     * owner，也不新增公开 Modification Runtime。重复注册同一收集器被拒绝。
     */
    public void registerDomainCollector(com.tkisor.nekojs.core.lifecycle.CandidateDomainCollector collector) {
        if (collector == null) throw new NullPointerException("collector");
        if (domainCollectors.contains(collector)) {
            throw new IllegalStateException("domain collector '" + collector.domain() + "' already registered");
        }
        domainCollectors.add(collector);
    }

    /** 已注册的候选域收集器（只读视图；诊断/测试观察 seam）。 */
    public java.util.List<com.tkisor.nekojs.core.lifecycle.CandidateDomainCollector> domainCollectors() {
        return java.util.List.copyOf(domainCollectors);
    }

    /** 按 domain 标识查找收集器（平台侧收口入口：server 启动收集等；不存在返回 null）。 */
    public com.tkisor.nekojs.core.lifecycle.CandidateDomainCollector domainCollector(String domain) {
        for (var collector : domainCollectors) {
            if (collector.domain().equals(domain)) return collector;
        }
        return null;
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
        ScriptManager manager = new ScriptManager(type, eventBridge, pluginRuntime, scriptProperties, core.errorTracker(), NekoJSPaths.get(), core.sandboxConfig(), environmentFactory, domainCollectors, preparationCache);
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
        // 票 11：root 最终关闭释放 prepared 模块缓存（server stop/切世界/reload 不清空；
        // 按类型清理走各 manager 的 fullReloadCleanup，此处释放 owner 持有的全部条目）。
        try {
            preparationCache.closeOwner();
        } catch (Throwable t) {
            if (first == null) first = t;
            else first.addSuppressed(t);
        }
        // 票 10：root 最终关闭释放 global/shared 状态域（server stop/切世界不清空；此处释放）。
        try {
            globalState.closeAll();
        } catch (Throwable t) {
            if (first == null) first = t;
            else first.addSuppressed(t);
        }
        // 票 39：root 拥有的候选域收集器逐一关闭（Item/Block modification 基线恢复等
        // 领域清理）——独立测试 root 各自从干净基线开始，不互相污染（AC5）。
        for (com.tkisor.nekojs.core.lifecycle.CandidateDomainCollector collector : domainCollectors) {
            if (!(collector instanceof AutoCloseable closeable)) continue;
            try {
                closeable.close();
            } catch (Throwable t) {
                if (first == null) first = t;
                else first.addSuppressed(t);
            }
        }
        domainCollectors.clear();
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
     * @param type           reload 的脚本类型
     * @param success        是否成功
     * @param error          失败原因（成功为 null；失败经由 {@link NekoReloadException} 抛出时
     *                       该字段与 report.error() 同源）
     * @param generation     成功后的 generation 序号 / 失败候选的 generation 序号
     * @param phase          结果阶段：成功为 COMMIT；STARTUP 非事务重载为 STARTUP（显式
     *                       restart/unsupported 边界）；单文件重载为 FILE
     * @param sourceLocation 失败脚本位置（{@code server/foo.js} 风格；阶段级失败或成功为 null）。
     *                       审查 A2：非候选路径（{@link #reloadFile}）此前把 source location
     *                       算出来却丢掉，AC3 要求的「失败结果含 source location」在该路径不成立
     */
    public record ReloadResult(ScriptType type, boolean success, Throwable error, long generation,
                               ReloadPhase phase, String sourceLocation) {
        public static ReloadResult success(ScriptType type, long generation) {
            return new ReloadResult(type, true, null, generation, ReloadPhase.COMMIT, null);
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
            return new ReloadResult(type, true, null, generation, phase, null);
        }

        /**
         * 非候选路径（{@link ReloadPhase#FILE}）的失败结果，携带失败脚本位置。
         *
         * <p>原有的 {@code success(type)} / {@code failure(type, error)} 两个重载已删除
         * （审查 A2）：前者返回 {@code phase=COMMIT} + {@code generation=-1} 自相矛盾，
         * 二者都没有调用方，属死码。
         */
        public static ReloadResult failure(ScriptType type, long generation, ReloadPhase phase,
                                           String sourceLocation, Throwable error) {
            return new ReloadResult(type, false, error, generation, phase, sourceLocation);
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
