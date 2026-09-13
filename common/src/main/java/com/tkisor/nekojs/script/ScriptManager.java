package com.tkisor.nekojs.script;

import com.tkisor.nekojs.script.ScriptTypeEnv;
import com.tkisor.nekojs.api.ScriptType;
import com.tkisor.nekojs.NekoJS;
import com.tkisor.nekojs.core.JavaClassLoadTelemetrySink;
import com.tkisor.nekojs.api.event.ScriptEventRegistrar;
import com.tkisor.nekojs.api.event.ScriptEvents;
import com.tkisor.nekojs.core.JavaClassLoadTelemetry;
import com.tkisor.nekojs.core.ScriptEventBridge;
import com.tkisor.nekojs.core.ScriptFilePolicy;
import com.tkisor.nekojs.core.ScriptLocator;
import com.tkisor.nekojs.core.config.SandboxConfig;
import com.tkisor.nekojs.core.error.ErrorTracker;
import com.tkisor.nekojs.core.fs.ClassFilter;
import com.tkisor.nekojs.core.fs.NekoJSPaths;
import com.tkisor.nekojs.core.lifecycle.NekoReloadException;
import com.tkisor.nekojs.core.lifecycle.ReloadFailureReport;
import com.tkisor.nekojs.core.lifecycle.ReloadPhase;
import com.tkisor.nekojs.core.lifecycle.ReloadProgressTracker;
import com.tkisor.nekojs.core.log.LoggerStream;
import com.tkisor.nekojs.core.module.NekoModulePipelineCache;
import com.tkisor.nekojs.core.module.esm.NekoEsmVirtualModuleRegistry;
import com.tkisor.nekojs.core.node.NekoNodeRuntime;
import com.tkisor.nekojs.api.plugin.IPluginRuntime;
import com.tkisor.nekojs.script.ScriptContextRegistry;
import com.tkisor.nekojs.script.prop.ScriptPropertyRegistry;
import graal.graalvm.polyglot.Context;
import graal.graalvm.polyglot.Value;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import com.tkisor.nekojs.api.annotation.CalledByDynamicCode;

/**
 * NekoJS 脚本引擎核心生命周期调度器。
 * <p>
 * 每个实例管理一种 {@link ScriptType}（STARTUP / SERVER / CLIENT / TEST）的完整脚本生命周期。
 * 通过构造器注入 {@link ScriptEventBridge}、{@link ErrorTracker}、{@link NekoJSPaths} 等协作者。
 */
public final class ScriptManager implements AutoCloseable {

    // ---- 最小静态：Context → ScriptManager 反向查找 ----
    // 用 ConcurrentHashMap 而非 synchronizedMap(WeakHashMap)：isContextDead / reportContextKilled
    // 位于每次事件与 timer 回调的热路径，synchronizedMap 会把所有回调在 map 锁上串行化。
    // 键改为强引用，因此不变式：Context 的销毁必须全部经 closeRuntimeResources（其内保证
    // remove）。当前全部销毁路径（kill 重建、事务 reload 成功/失败、resetEnvironment、close）
    // 均满足；若新增绕过 closeRuntimeResources 的销毁路径，将同时泄漏 Context 与 ScriptManager。
    private static final Map<Context, ScriptManager> CONTEXT_TO_MANAGER = new ConcurrentHashMap<>();

    /**
     * 从 GraalVM Context 反向查找所属的 ScriptManager 实例。
     * 用于 EventBusJS / NekoNodeTimers 等 JS 回调场景，这些场景仅有 Context 引用。
     */
    public static ScriptManager from(Context context) {
        ScriptManager sm = CONTEXT_TO_MANAGER.get(context);
        if (sm == null) {
            throw new IllegalStateException("No ScriptManager registered for Context: " + context);
        }
        return sm;
    }

    /**
     * JS 回调（事件监听器 / timer）catch 路径共享的 kill 上报：异常链表明 Graal 已因
     * 资源上限（语句上限）关闭该 Context 时，按求值所属 generation 记账——候选 Context
     * 记 {@code candidateKilled}（候选按失败处理），active Context 记 {@code contextKilled}
     * （下次取用时重建）。未注册的 Context（测试等场景）安全忽略。
     */
    public static void reportContextKilled(Context context, Throwable t) {
        if (context == null || !ScriptExecutor.isContextKilledByResourceLimits(t)) return;
        ScriptManager manager = CONTEXT_TO_MANAGER.get(context);
        if (manager != null) {
            manager.markContextKilled(context);
        }
    }

    /**
     * JS 回调（事件监听器 / timer）分发短路判定：闭包捕获的 Context 是否已死。
     *
     * <p>本仓库使用的 relocated Graal polyglot API 没有 {@code Context.isOpen()}，
     * 这里以 ScriptManager 侧的状态等价判定。ticket 06 generation 语义：
     * <ul>
     *   <li>active Context：死 = {@code contextKilled}（语句上限 kill，待重建）；</li>
     *   <li>候选 Context（构建中）：死 = {@code candidateKilled}——候选 timer 在候选执行
     *       路径（waitForEvaluation flush）中可运行；候选监听器在 commit 前根本不挂总线，
     *       因此不存在「候选监听器提前接收生产事件」的路径；</li>
     *   <li>已注册但既非 active 也非候选：旧 generation 残留闭包 → dead（commit 后旧
     *       generation 立即停止接收新 callback 的兜底，即使总线清扫与激活之间被并发
     *       dispatch 也不会旧新双重执行）。</li>
     * </ul>
     * 未注册的 Context（测试等场景）返回 false，走原有 try/catch 路径。
     */
    public static boolean isContextDead(Context context) {
        if (context == null) return false;
        ScriptManager manager = CONTEXT_TO_MANAGER.get(context);
        if (manager == null) return false;
        // Graal 的 Value.getContext() 可能返回与 Context.Builder.build() 引用不同但
        // equals/hashCode 相同的包装对象；必须用 equals 比较，否则正常回调也会被误判为 dead。
        Context active = manager.runtime.context();
        if (active != null && active.equals(context)) {
            return manager.contextKilled;
        }
        Context candidate = manager.candidateContext;
        if (candidate != null && candidate.equals(context)) {
            return manager.candidateKilled;
        }
        return true;
    }

    // ---- 实例字段 ----

    private final ScriptEventBridge scriptEventBridge;
    private final IPluginRuntime pluginRuntime;
    private final ScriptPropertyRegistry scriptProperties;
    private final ErrorTracker errorTracker;
    private final NekoJSPaths paths;
    private final SandboxConfig sandboxConfig;
    private final ScriptExecutor scriptExecutor;
    private final ScriptEnvironmentFactory environmentFactory;

    /**
     * 本实例管理的脚本类型
     */
    public final ScriptType scriptType;

    /**
     * context 与随行资源（node runtime、out/err 流）的不可分成对快照。
     *
     * <p>必须整体经单个 volatile 引用发布：旧实现 {@code context} 是 volatile 但
     * {@code nodeRuntime}/{@code contextOutStream}/{@code contextErrStream} 是普通字段，
     * tick 线程无锁读这一组字段时会看到「新 context 配旧 runtime」的错配——reload 后 timer
     * 打到已关闭的旧运行时，或 null 检查与使用之间字段被清导致 NPE。
     */
    private record RuntimeEnvironment(Context context, NekoNodeRuntime nodeRuntime,
                                      LoggerStream outStream, LoggerStream errStream) {
        static final RuntimeEnvironment EMPTY = new RuntimeEnvironment(null, null, null, null);

        boolean isEmpty() {
            return context == null;
        }
    }

    private volatile RuntimeEnvironment runtime = RuntimeEnvironment.EMPTY;
    private List<ScriptContainer> scripts;

    /**
     * 已提交的 generation 序号（单调递增；owner thread 访问，reload/load 与命令同锁）。
     * 初始环境创建、kill 重建与事务式 commit 各递增一次（工单 06 generation 契约）。
     */
    private long generation;

    /**
     * 构建中的候选 generation Context；null 表示当前没有候选在构建。
     * 候选阶段（preparation/execution/binding）内该 Context 是「临时存活」：
     * timer 回调可在候选执行路径（ScriptExecutor.waitForEvaluation 的候选 flush）中运行，
     * 但候选监听器只收集（{@link #pendingListeners}）、不挂生产总线，commit 前不可见。
     */
    private volatile Context candidateContext;

    /** 候选环境被语句上限杀死（与 active 的 {@link #contextKilled} 分开记账）。 */
    private volatile boolean candidateKilled;

    /** 候选加载中首个触发语句上限 kill 的脚本（失败结果 source location 归因）。 */
    private ScriptContainer candidateKillSource;

    /**
     * 候选 generation 收集的挂起监听器注册（EventBusJS.PendingListener）。
     * 只在 owner thread（reload 持实例锁）上读写；commit 点统一激活，失败随候选丢弃。
     */
    private final List<com.tkisor.nekojs.api.event.EventBusJS.PendingListener> pendingListeners = new ArrayList<>();

    /** 一次性标记：STARTUP reload 的非事务语义只警告一次（每个 ScriptType 一个实例）。 */
    private boolean warnedStartupReloadNonTransactional;

    /** Graal 因语句上限（scriptStatementLimit）关闭了当前 Context；下次取用时重建。 */
    private volatile boolean contextKilled;

    // ---- 构造函数 ----

    public ScriptManager(ScriptType scriptType, ScriptEventBridge scriptEventBridge, IPluginRuntime pluginRuntime, ScriptPropertyRegistry scriptProperties, ErrorTracker errorTracker, NekoJSPaths paths, SandboxConfig sandboxConfig, ScriptEnvironmentFactory environmentFactory) {
        this.scriptType = scriptType;
        this.scriptEventBridge = scriptEventBridge;
        this.pluginRuntime = pluginRuntime;
        this.scriptProperties = scriptProperties;
        this.errorTracker = errorTracker;
        this.paths = paths;
        this.sandboxConfig = sandboxConfig;
        this.scriptExecutor = new ScriptExecutor(errorTracker, paths, sandboxConfig, this::markContextKilled);
        this.environmentFactory = environmentFactory;
    }

    /** 当前已提交的 generation 序号（诊断/失败结果用；非契约稳定性保证）。 */
    public long generationId() {
        return generation;
    }

    /**
     * api.event 回调 seam（{@code EventBusJS.execute} 调用）：注册来源 Context 属于
     * 构建中的候选 generation 时，把挂起注册收进候选收集器并返回 true（注册对生产总线
     * 不可见，commit 点统一激活）；否则返回 false（普通注册路径，调用方自行激活）。
     *
     * <p>线程约定：候选构建与收集都发生在 reload 的 owner thread（reload 持实例锁），
     * 无并发写；完整 owner-thread 队列/重入调度归工单 07。
     */
    public static boolean collectPendingListener(Context context, com.tkisor.nekojs.api.event.EventBusJS.PendingListener pending) {
        if (context == null || pending == null) return false;
        ScriptManager manager = CONTEXT_TO_MANAGER.get(context);
        if (manager == null) return false;
        Context candidate = manager.candidateContext;
        if (candidate == null || !candidate.equals(context)) return false;
        manager.pendingListeners.add(pending);
        return true;
    }

    // ---- 配置 ----

    public void setJavaClassLoadTelemetrySink(JavaClassLoadTelemetrySink sink) {
        JavaClassLoadTelemetry.setSink(sink);
    }

    // ---- Context 访问（懒初始化） ----

    /** ScriptExecutor 回调：Graal 因语句上限关闭了求值所属的 Context（active 或候选）。 */
    private void markContextKilled(Context context) {
        Context candidate = this.candidateContext;
        if (candidate != null && candidate.equals(context)) {
            this.candidateKilled = true;
        } else {
            this.contextKilled = true;
        }
    }

    private synchronized RuntimeEnvironment getOrCreateEnvironment() {
        if (runtime.isEmpty() || contextKilled) {
            if (!runtime.isEmpty()) {
                // 旧 Context 已被 Graal 关闭（语句上限触发）；清理注册与残留资源。
                // 必须与 resetEnvironment / close 的 teardown 等价：监听器闭包
                // 持有指向已死 Context 的 Value，errorTracker/模块/ESM 缓存与 binding 状态
                // 同属旧环境。缺一步就是「kill 重建后静默携带脏状态」——例如残留 errorTracker
                // 条目永远不清、binding 缓存的旧 Context helper 下次取用报已关闭。
                // 此处无法重跑脚本重建监听器（重建只创建空环境），恢复需再次 reload。
                fullReloadCleanup();
                for (var binding : pluginRuntime.bindings(scriptType).values()) {
                    binding.close(scriptType);
                }
                closeRuntimeResources(this.runtime);
            }
            ScriptEnvironmentFactory.Environment env = environmentFactory.create(scriptType);
            RuntimeEnvironment created = new RuntimeEnvironment(
                    env.context(), env.nodeRuntime(), env.outStream(), env.errStream());
            this.runtime = created;
            CONTEXT_TO_MANAGER.put(created.context(), this);
            ScriptContextRegistry.bind(created.context(), scriptType);
            contextKilled = false;
            // 新 active 环境实例 = 新 generation（initial load / kill 重建各递增一次；
            // 事务式 reload 的递增在 commit 点）
            this.generation++;
            return created;
        }
        return runtime;
    }

    private Context getOrCreateContext() {
        return getOrCreateEnvironment().context();
    }

    // ---- 脚本发现 ----

    /**
     * 发现本类型对应的脚本文件
     */
    public void discoverScripts() {
        List<ScriptContainer> discovered = discoverWithPacks();
        this.scripts = discovered;
        com.tkisor.nekojs.script.ScriptTypeEnv.logger(scriptType).info("发现了 {} 个 {} 脚本。", discovered.size(), scriptType.name());
    }

    /**
     * 发现脚本前重扫全局脚本包，使包的启用/禁用与增删在下次 reload 即时生效
     * （WORLD 包由平台生命周期钩子另行激活/卸载，见 ScriptPackRegistry）。
     */
    private List<ScriptContainer> discoverWithPacks() {
        com.tkisor.nekojs.core.pack.ScriptPackRegistry.get().refreshGlobalPacks();
        return ScriptLocator.discover(scriptType, scriptProperties);
    }

    /**
     * 卸载世界脚本包在本类型上注册的一切：按包 scriptId 前缀反注册事件监听器
     * （含动态事件定义）并取消其 timer。供平台 serverStopped / 断线钩子调用——
     * 不做完整 reload（Context 与平铺/全局包脚本保持存活）。
     */
    public void clearWorldPackListeners(List<com.tkisor.nekojs.core.pack.ScriptPack> packs) {
        for (com.tkisor.nekojs.core.pack.ScriptPack pack : packs) {
            if (pack.scope() != com.tkisor.nekojs.core.pack.ScriptPackScope.WORLD) continue;
            String prefix = pack.scriptIdPrefix(scriptType);
            scriptEventBridge.clearListenersByPrefix(scriptType, prefix);
            NekoNodeRuntime nodeRuntime = this.runtime.nodeRuntime();
            if (nodeRuntime != null) {
                nodeRuntime.timers().cancelScriptByPrefix(prefix);
            }
        }
    }

    // ---- 脚本加载与执行 ----

    /**
     * 加载并顺序执行所有脚本。
     *
     * <p>synchronized：与 {@link #reloadScripts()} / {@link #reloadScriptFile(String)} 同一把
     * 实例锁。首次加载（平台 init / client setup）与命令触发的 reload 可能从不同线程进入，
     * 并发执行会让两个线程各自在 {@link #getOrCreateContext()} 创建候选 Context，输家创建的
     * Context（及其 timer 调度线程）永远不会被关闭。锁可重入：reloadScripts 内部调用本方法
     * 不受影响。
     */
    public synchronized void loadScripts() {
        // Reload progress HUD：首次加载独占会话；被 reloadScripts 嵌套调用时（STARTUP 分支）
        // 已有活动会话，begin 返回 false，finish 交由外层 reload 负责。
        final boolean progressOwned = ReloadProgressTracker.begin(scriptType.name, 1);
        try {
            pluginRuntime.fireBeforeScriptsLoaded(scriptType);
            try {
                loadScriptsInto(scripts);
                if (scriptType == ScriptType.STARTUP) {
                    flushReadyNodeTimers();
                    ScriptEvents.post(getScriptEventRegistrar());
                }
            } finally {
                pluginRuntime.fireAfterScriptsLoaded(scriptType);
            }
            ReloadProgressTracker.step(scriptType.name, "scripts executed");
            if (progressOwned) {
                ReloadProgressTracker.finish(scriptType.name, true);
            }
        } catch (Throwable t) {
            if (progressOwned) {
                ReloadProgressTracker.finish(scriptType.name, false);
            }
            throw t;
        }
    }

    /**
     * 在指定 Context / Node runtime 中执行给定脚本列表：preload、按 priority 与 after 依赖排序、逐个执行入口。
     *
     * <p>被首次 {@link #loadScripts()} 和事务式 reload 复用。空列表只记录日志，不创建副作用。
     */
    private void loadScriptsInto(List<ScriptContainer> scriptsToLoad) {
        if (!prepareScriptsForLoad(scriptsToLoad)) {
            return;
        }
        for (ScriptContainer script : scriptsToLoad) {
            if (script.shouldRun()) {
                // 逐个脚本取最新环境：某脚本触发语句上限导致 Context 被 Graal 关闭时，
                // 下一个脚本能在自动重建的环境中继续执行，而不是全军覆没。context 与
                // nodeRuntime 从同一次快照取出，避免读到错配对。
                RuntimeEnvironment env = getOrCreateEnvironment();
                scriptExecutor.executeEntry(env.context(), script, env.nodeRuntime());
            }
        }
    }

    private void loadScriptsInto(List<ScriptContainer> scriptsToLoad, Context context, NekoNodeRuntime nodeRuntime) {
        if (!prepareScriptsForLoad(scriptsToLoad)) {
            return;
        }
        for (ScriptContainer script : scriptsToLoad) {
            if (script.shouldRun()) {
                scriptExecutor.executeEntry(context, script, nodeRuntime);
            }
        }
    }

    private boolean prepareScriptsForLoad(List<ScriptContainer> scriptsToLoad) {
        if (scriptsToLoad == null || scriptsToLoad.isEmpty()) {
            com.tkisor.nekojs.script.ScriptTypeEnv.logger(scriptType).info("没有需要加载的 {} 脚本。", scriptType.name());
            return false;
        }
        for (var script : scriptsToLoad) {
            script.preload();
            reportPreloadFailure(script);
        }
        ScriptLoadOrderSorter.Result orderResult =
                ScriptLoadOrderSorter.applyAfterOrder(scriptsToLoad, ScriptContainer::shouldRun);
        if (orderResult.hasProblems()) {
            com.tkisor.nekojs.script.ScriptTypeEnv.logger(scriptType).warn("{} 脚本 after 依赖排序存在问题：{}", scriptType.name(), orderResult.describe());
        }
        return true;
    }

    /**
     * preload 读失败（编码/权限/文件消失）时 {@code disabled} 置位、{@code shouldRun()}
     * 静默跳过，{@code lastError} 旧逻辑无人读——脚本直接消失且日志/错误面板均无痕迹。
     * 这里上报错误面板并落 error 日志，让「脚本为什么没跑」可被看见。
     */
    private void reportPreloadFailure(ScriptContainer script) {
        if (script.disabled && script.lastError != null) {
            errorTracker.record(script, script.lastError);
            com.tkisor.nekojs.script.ScriptTypeEnv.logger(scriptType).error("无法读取脚本 {}，已跳过：{}", script.path, script.lastError.toString());
        }
    }

        // ---- 重载 ----

        public synchronized void reloadScripts () {
            ReloadProgressTracker.begin(scriptType.name, scriptType == ScriptType.STARTUP ? 3 : 5);
            boolean progressSuccess = false;
            try {
                com.tkisor.nekojs.script.ScriptTypeEnv.logger(scriptType).info("正在重载 {} 脚本...", scriptType.name());
                if (scriptType == ScriptType.STARTUP) {
                    if (!warnedStartupReloadNonTransactional) {
                        warnedStartupReloadNonTransactional = true;
                        com.tkisor.nekojs.script.ScriptTypeEnv.logger(scriptType).warn(
                                "{} 脚本重载为非事务式语义（STARTUP 涉及物品/方块/实体等不可逆注册，无法安全回滚）；"
                                        + "若重载期间脚本出错，已注册内容不会回退。",
                                scriptType.name());
                    }
                    // STARTUP 涉及不可逆注册（物品、方块、实体），无法安全回滚，保持 reset+load 语义。
                    resetEnvironment();
                    ReloadProgressTracker.step(scriptType.name, "environment reset");
                    discoverScripts();
                    ReloadProgressTracker.step(scriptType.name, "scripts discovered");
                    loadScripts();
                    ReloadProgressTracker.step(scriptType.name, "scripts executed");
                } else {
                    reloadScriptsTransactional();
                }
                progressSuccess = true;
                com.tkisor.nekojs.script.ScriptTypeEnv.logger(scriptType).info("{} 脚本重载完毕。", scriptType.name());
            } finally {
                ReloadProgressTracker.finish(scriptType.name, progressSuccess);
            }
        }

        /**
         * 事务式完整 reload：候选 generation 依次通过 preparation、binding、execution
         * （含事件计划收集）后，才在单一 commit 点切换为 active；任一阶段失败时关闭候选
         * 全部资源并保留 active（工单 06）。
         *
         * <p>generation 隔离语义：
         * <ul>
         *   <li>候选 Context / binding 安装 / 脚本执行全程不触碰 active 的生产路由——候选
         *       监听器只收集为 {@link EventBusJS.PendingListener}（commit 前不上总线）、
         *       候选 timer 只进候选自己的 node runtime（生产 tick flush 仍冲刷 active）；</li>
         *   <li>失败：候选 Context、timer、listener（挂起注册）、模块编译产物全部关闭，
         *       active 的 Context、监听器、timer、脚本状态原样可用；</li>
         *   <li>commit：清扫旧 generation 监听器 → 发布新 runtime → 激活候选挂起监听器 →
         *       释放旧 module session → 按 timer、Context 所有权顺序释放旧环境。</li>
         * </ul>
         *
         * <p>线程约定：候选构建/执行在当前调用线程（owner thread）同步完成，reload 由
         * 实例锁串行；owner-thread 队列、重入与 watchdog 调度归工单 07。
         * binding.close(type)（进程级注册账本重置，域 Adapter 持有）保持候选构建前调用：
         * 其「先清账本再注册」的顺序是领域契约，账本快照/回滚归 W6/W7 域 Adapter。
         */
        private void reloadScriptsTransactional () {
            ReloadProgressTracker.begin(scriptType.name, 5);
            boolean progressSuccess = false;
            try {
                com.tkisor.nekojs.script.ScriptTypeEnv.logger(scriptType).info("正在重载 {} 脚本...", scriptType.name());
                long candidateGeneration = this.generation + 1;
                RuntimeEnvironment oldEnvironment = this.runtime;
                RuntimeEnvironment candidateEnvironment = null;
                try {
                    // 诊断状态清空（与既有实现同位次；失败结果中的候选错误因此可见）
                    errorTracker.clearByType(scriptType);
                    // 域 Adapter 的进程级注册账本重置（PostEffects/NativeEvents/DynamicRegistry 等）：
                    // 「先 close 再注册」是 binding 契约，保持原有位次；共享 Java 对象不做
                    // generation 私有快照（spec 09 user story 15），账本快照/恢复归域 Adapter。
                    for (var binding : pluginRuntime.bindings(scriptType).values()) {
                        binding.close(scriptType);
                    }

                    // ---- Phase PREPARATION：候选 Context + node runtime（生产路由不动）----
                    try {
                        candidateEnvironment = createCandidateEnvironment();
                    } catch (Throwable t) {
                        throw reloadFailure(candidateGeneration, ReloadPhase.PREPARATION, null, "candidate-context", t);
                    }
                    ReloadProgressTracker.step(scriptType.name, "candidate environment created");

                    // ---- Phase BINDING：事件组/插件 binding/schema 安装进候选 Context ----
                    try {
                        environmentFactory.installEnvironmentBindings(candidateEnvironment.context(), scriptType);
                    } catch (Throwable t) {
                        throw reloadFailure(candidateGeneration, ReloadPhase.BINDING, null, "binding-install", t);
                    }
                    ReloadProgressTracker.step(scriptType.name, "candidate bindings installed");

                    // ---- Phase EXECUTION + EVENT_PLAN：候选脚本执行；监听器只收集、timer 只进候选收集器 ----
                    List<ScriptContainer> candidateScripts;
                    try {
                        candidateScripts = discoverWithPacks();
                        com.tkisor.nekojs.script.ScriptTypeEnv.logger(scriptType).info("发现了 {} 个 {} 脚本。", candidateScripts.size(), scriptType.name());
                        ReloadProgressTracker.step(scriptType.name, "discovered " + candidateScripts.size() + " scripts");

                        this.candidateKilled = false;
                        this.candidateKillSource = null;
                        pluginRuntime.fireBeforeScriptsLoaded(scriptType);
                        try {
                            loadCandidateScripts(candidateScripts, candidateEnvironment.context(), candidateEnvironment.nodeRuntime());
                        } finally {
                            pluginRuntime.fireAfterScriptsLoaded(scriptType);
                        }
                        ReloadProgressTracker.step(scriptType.name, "candidate scripts executed");

                        if (this.candidateKilled) {
                            // 候选加载期间被脚本资源上限终止（runaway watchdog 的 2s 滑动窗口
                            // 或 scriptStatementLimit 总量），Graal 已关闭候选 Context：按失败处理，
                            // 不把死掉的候选提交为 live（watchdog 语义的候选丢弃面归工单 07）。
                            throw reloadFailure(candidateGeneration, ReloadPhase.EXECUTION,
                                    sourceOf(this.candidateKillSource), "candidate-killed",
                                    new RuntimeException(scriptType.name()
                                            + " candidate context was terminated by script resource limits"
                                            + " (runaway watchdog / statement limit) during reload"));
                        }
                    } catch (NekoReloadException f) {
                        throw f;
                    } catch (Throwable t) {
                        throw reloadFailure(candidateGeneration, ReloadPhase.EXECUTION, null, "script-execution", t);
                    }

                    // ---- COMMIT POINT（单一原子切换；owner thread 同步执行）----
                    commitGeneration(candidateGeneration, candidateEnvironment, candidateScripts, oldEnvironment);
                    ReloadProgressTracker.step(scriptType.name, "committed");
                    progressSuccess = true;
                    com.tkisor.nekojs.script.ScriptTypeEnv.logger(scriptType).info("{} 脚本重载完毕。", scriptType.name());
                } catch (NekoReloadException f) {
                    discardCandidate(candidateEnvironment);
                    com.tkisor.nekojs.script.ScriptTypeEnv.logger(scriptType).error("{} 脚本事务重载失败，候选 generation 已关闭，active 环境保持不变",
                            scriptType.name(), f);
                    throw f;
                }
            } finally {
                ReloadProgressTracker.finish(scriptType.name, progressSuccess);
            }
        }

        /**
         * 创建候选 generation 环境：Context + node runtime，并登记 Context → manager
         * 映射与候选标记（候选 timer 回调的存活判定依赖该登记）。
         */
        private RuntimeEnvironment createCandidateEnvironment () {
            ScriptEnvironmentFactory.Environment candidate = environmentFactory.createContext(scriptType);
            RuntimeEnvironment candidateEnvironment = new RuntimeEnvironment(
                    candidate.context(), candidate.nodeRuntime(), candidate.outStream(), candidate.errStream());
            CONTEXT_TO_MANAGER.put(candidate.context(), this);
            ScriptContextRegistry.bind(candidate.context(), scriptType);
            this.candidateContext = candidate.context();
            this.candidateKilled = false;
            this.candidateKillSource = null;
            this.pendingListeners.clear();
            return candidateEnvironment;
        }

        /**
         * 单一 commit 点（工单 06）：candidate 全部阶段通过后的原子切换。
         *
         * <p>顺序（owner thread 临界区内完成）：
         * <ol>
         *   <li>清扫旧 generation 监听器——此刻总线 type 桶里只有旧 generation 的 token
         *       （候选监听器是挂起收集、从未上总线），整类型清空即旧 generation 清扫；
         *       共享静态总线无法按 generation 分桶，此步与下一步合起来等价于「切换生产
         *       路由 + 旧 generation 停止接收」，跨线程 dispatch 的临界区安全由 07 的
         *       owner-thread 序列化补齐；</li>
         *   <li>发布新 runtime：生产 timer flush 目标与 isContextDead 判定同步切换，
         *       同一事件自此只由新 generation 接收（旧闭包经 isContextDead 判 dead 双保险）；</li>
         *   <li>激活候选挂起监听器：新 generation 成为唯一新 callback 接收者；</li>
         *   <li>释放旧 module session（编译模块缓存 / 虚拟 ESM URI 按类型清除）；</li>
         *   <li>按 timer、Context 所有权顺序释放旧环境（closeRuntimeResources：
         *       node runtime/timer → Context → streams）。</li>
         * </ol>
         */
        private void commitGeneration (long candidateGeneration, RuntimeEnvironment candidateEnvironment,
                List<ScriptContainer> candidateScripts, RuntimeEnvironment oldEnvironment) {
            // (1) 旧 generation 监听器清扫
            scriptEventBridge.clearListeners(scriptType);
            // (2) 生产路由切换：新 generation 成为 live 环境
            this.runtime = candidateEnvironment;
            this.scripts = candidateScripts;
            this.generation = candidateGeneration;
            this.contextKilled = false;
            this.candidateContext = null;
            this.candidateKilled = false;
            // (3) 激活候选挂起监听器（新 generation 唯一 callback 接收者）
            List<com.tkisor.nekojs.api.event.EventBusJS.PendingListener> activation =
                    List.copyOf(this.pendingListeners);
            this.pendingListeners.clear();
            for (var pending : activation) {
                pending.activate();
            }
            // (4) 旧 module session 释放
            NekoModulePipelineCache.clear(scriptType);
            NekoEsmVirtualModuleRegistry.clear(scriptType);
            // (5) 旧环境按所有权顺序释放：timer → Context → streams
            if (!oldEnvironment.isEmpty()) {
                closeRuntimeResources(oldEnvironment);
            }
        }

        /**
         * 丢弃候选 generation：挂起监听器直接弃置（从未上总线）、候选 Context/timer/streams
         * 按 timer → Context → streams 顺序关闭；active 的 runtime、scripts、监听器、
         * timer 与 generation 序号原样保留。
         */
        private void discardCandidate (RuntimeEnvironment candidateEnvironment) {
            this.pendingListeners.clear();
            this.candidateContext = null;
            this.candidateKilled = false;
            this.candidateKillSource = null;
            if (candidateEnvironment != null && !candidateEnvironment.isEmpty()) {
                closeRuntimeResources(candidateEnvironment);
            }
        }

        /** 候选脚本逐个执行；首个触发候选 kill 的脚本被记录用于失败结果的 source location。 */
        private void loadCandidateScripts (List<ScriptContainer> scriptsToLoad, Context context, NekoNodeRuntime nodeRuntime) {
            if (!prepareScriptsForLoad(scriptsToLoad)) {
                return;
            }
            for (ScriptContainer script : scriptsToLoad) {
                if (!script.shouldRun()) {
                    continue;
                }
                boolean killedBefore = this.candidateKilled;
                scriptExecutor.executeEntry(context, script, nodeRuntime);
                if (!killedBefore && this.candidateKilled && this.candidateKillSource == null) {
                    this.candidateKillSource = script;
                }
            }
        }

        private static String sourceOf (ScriptContainer script) {
            if (script == null) return null;
            try {
                return script.type.name + "/" + ScriptTypeEnv.scriptsDir(script.type)
                        .relativize(script.path).toString().replace('\\', '/');
            } catch (Exception ignored) {
                return script.path.toString();
            }
        }

        private NekoReloadException reloadFailure (long generation, ReloadPhase phase, String sourceLocation,
                String domain, Throwable cause) {
            return new NekoReloadException(new ReloadFailureReport(
                    scriptType, generation, phase, sourceLocation,
                    "ScriptManager[" + scriptType.name + "]", domain, cause));
        }

        public synchronized List<ScriptContainer> reloadScriptFile (String filePath) throws IOException {
            discoverScripts();
            Path target = resolveScriptPath(filePath);

            if (scriptType == ScriptType.STARTUP) {
                // STARTUP registrations are irreversible/non-transactional and ScriptEvents
                // definitions are recreated by a full load (resetEnvironment + discoverScripts
                // + loadScripts + ScriptEvents.post). Re-posting only the affected listeners
                // would wipe unaffected custom-event listener tokens, so a targeted STARTUP
                // reload degrades to a full STARTUP reload.
                List<ScriptContainer> matched = scripts.stream()
                        .filter(script -> script.path.normalize().toAbsolutePath().equals(target))
                        .toList();
                if (matched.isEmpty()) {
                    throw new IOException("No loaded STARTUP entry matches " + displayScriptPath(target)
                            + ". Reload the whole STARTUP environment first if this file has not been loaded yet.");
                }
                com.tkisor.nekojs.script.ScriptTypeEnv.logger(scriptType).info("正在重载 STARTUP 脚本文件 {}：STARTUP 注册不可逆，退化为完整 STARTUP 重载。", displayScriptPath(target));
                reloadScripts();
                List<ScriptContainer> reloadedMatches = scripts.stream()
                        .filter(script -> script.path.normalize().toAbsolutePath().equals(target))
                        .toList();
                return reloadedMatches.isEmpty() ? matched : reloadedMatches;
            }

            List<ScriptContainer> targets = reloadTargets(target);
            if (targets.isEmpty()) {
                throw new IOException("No loaded entry depends on " + displayScriptPath(target) + ". Reload the whole " + scriptType.name() + " environment first if this dependency has not been loaded yet.");
            }
            com.tkisor.nekojs.script.ScriptTypeEnv.logger(scriptType).info("正在重载 {} 脚本文件 {}，受影响入口 {} 个...", scriptType.name(), displayScriptPath(target), targets.size());

            NekoModulePipelineCache.invalidate(target);
            Context ctx = getOrCreateContext();
            String modulePath = "./" + paths.root().relativize(target).toString().replace('\\', '/');

            // 单文件重载统一走「失效受影响模块 → 重跑受影响入口」路径。曾经存在一个
            // ModuleSliceRelinker 捷径试图只 relink 不重跑入口，但 Graal 对每个虚拟
            // 模块 URI 缓存模块实例，不重跑入口就无法让 import 绑定看到新导出；且其
            // revision 查询实现不匹配（用新 revision 查旧 record）导致静默 no-op 并
            // 返回 success=true。捷径已删除，见 git 历史。
            //
            // 「失效 → 重跑」必须成对完成：try/finally 保证中途失败（如失效 eval 抛出、
            // Context 重建失败）时，未被重跑的入口也补一次 cleanupScriptEntry（幂等），
            // 不会停留在「模块树已失效但旧 listener / timer 仍在运行」的半失效状态；恢复需再次 reload。
            int rerunCount = 0;
            try {
                synchronized (ctx) {
                    for (ScriptContainer script : targets) {
                        String entryPath = "./" + paths.root().relativize(script.path).toString().replace('\\', '/');
                        ctx.eval("js", "globalThis.__nekoScriptLoader.invalidateModuleTree").execute(entryPath);
                    }
                    ctx.eval("js", "globalThis.__nekoScriptLoader.invalidateAffectedModules").execute(modulePath);
                }

                while (rerunCount < targets.size()) {
                    reloadEntryScript(getOrCreateContext(), targets.get(rerunCount));
                    rerunCount++;
                }
            } finally {
                for (int i = rerunCount; i < targets.size(); i++) {
                    cleanupScriptEntry(targets.get(i));
                }
            }

            com.tkisor.nekojs.script.ScriptTypeEnv.logger(scriptType).info("{} 脚本文件 {} 重载完毕。", scriptType.name(), displayScriptPath(target));
            return targets;
        }

        public Optional<ScriptContainer> resolveScriptFile (String filePath) throws IOException {
            discoverScripts();
            Path target = resolveScriptPath(filePath);
            return scripts.stream()
                    .filter(script -> script.path.normalize().toAbsolutePath().equals(target))
                    .findFirst();
        }

        private List<ScriptContainer> reloadTargets (Path target){
            Optional<ScriptContainer> directEntry = scripts.stream()
                    .filter(script -> script.path.normalize().toAbsolutePath().equals(target))
                    .findFirst();
            Context ctx = getOrCreateContext();
            String modulePath = "./" + paths.root().relativize(target).toString().replace('\\', '/');
            List<String> affectedIds = new ArrayList<>();
            synchronized (ctx) {
                Value affected = ctx.eval("js", "globalThis.__nekoScriptLoader.affectedEntries").execute(modulePath);
                if (affected.hasArrayElements()) {
                    for (long i = 0; i < affected.getArraySize(); i++) {
                        affectedIds.add(affected.getArrayElement(i).asString());
                    }
                }
            }
            // 依赖图中记录的所有受影响入口（含目标文件本身，若它是入口）都必须重跑：
            // Graal 按虚拟模块 URI 缓存模块实例，只重跑目标模块无法让已加载入口的
            // import 绑定看到新导出。
            List<ScriptContainer> affectedEntries = scripts.stream()
                    .filter(script -> affectedIds.contains(paths.root().relativize(script.path).toString().replace('\\', '/')))
                    .toList();
            if (!affectedEntries.isEmpty()) {
                return affectedEntries;
            }
            // 文件从未被加载（依赖图无记录）但确实是已发现脚本：直接重跑该入口
            return directEntry.map(List::of).orElseGet(List::of);
        }

        private void reloadEntryScript (Context ctx, ScriptContainer script){
            cleanupScriptEntry(script);
            script.preload();
            reportPreloadFailure(script);
            if (script.shouldRun()) {
                scriptExecutor.executeEntry(ctx, script, this.runtime.nodeRuntime());
            }
        }

        private void cleanupScriptEntry (ScriptContainer script){
            scriptEventBridge.clearListeners(scriptType, script.id.toString());
            NekoNodeRuntime nodeRuntime = this.runtime.nodeRuntime();
            if (nodeRuntime != null) {
                nodeRuntime.timers().cancelScript(script.id.toString());
            }
            errorTracker.clear(script.id);
            errorTracker.clearByScriptPath(scriptType, paths.root().relativize(script.path).toString().replace('\\', '/'));
        }

        private void fullReloadCleanup () {
            scriptEventBridge.clearListeners(scriptType);
            errorTracker.clearByType(scriptType);
            // 清空进程级静态缓存中本 scriptType 的条目：NekoModulePipelineCache.clear(ScriptType)
            // 同时按类型清理对应 SourceMapRegistry 条目；NekoEsmVirtualModuleRegistry 持有虚拟 ESM URI。
            // 局部清除避免单机单类型 reset/close 误清其它类型的编译产物（原全局 clear 会跨类型误伤）。
            NekoModulePipelineCache.clear(scriptType);
            NekoEsmVirtualModuleRegistry.clear(scriptType);
        }

        // ---- 路径解析 ----

        private Path resolveScriptPath (String filePath) throws IOException {
            if (ScriptTypeEnv.scriptsDir(scriptType) == null) {
                throw new IOException("Script type has no script directory: " + scriptType.name());
            }
            if (filePath == null || filePath.isBlank()) {
                throw new IOException("Script file path is empty.");
            }
            String normalizedText = filePath.replace('\\', '/');
            String rootPrefix = scriptType.name + "/";
            if (normalizedText.startsWith(rootPrefix)) {
                normalizedText = normalizedText.substring(rootPrefix.length());
            }
            Path relative = Path.of(normalizedText).normalize();
            if (relative.isAbsolute() || relative.startsWith("..")) {
                throw new IOException("Invalid script file path: " + filePath);
            }
            Path target = ScriptTypeEnv.scriptsDir(scriptType).resolve(relative).normalize().toAbsolutePath();
            Path root = ScriptTypeEnv.scriptsDir(scriptType).normalize().toAbsolutePath();
            if (!target.startsWith(root)) {
                throw new IOException("Script file is outside " + scriptType.name() + " scripts: " + filePath);
            }
            if (!Files.isRegularFile(target) || !ScriptFilePolicy.legacyRuntime().isSupportedScriptFile(target)) {
                throw new IOException("Unsupported or missing script file: " + filePath);
            }
            return target;
        }

        private String displayScriptPath (Path path){
            return scriptType.name + "/" + ScriptTypeEnv.scriptsDir(scriptType).relativize(path).toString().replace('\\', '/');
        }

        // ---- 测试脚本 ----

        // synchronized：runTestScripts 内部走 reloadScriptsTransactional（会创建候选 Context），
        // 与 loadScripts / reloadScripts / close 共用实例锁，防止命令线程并发触发 TEST 运行
        // 时输家候选 Context 被孤立泄漏（Context + timer 调度线程）。
        public synchronized void runTestScripts () {
            if (scriptType != ScriptType.TEST) {
                throw new IllegalStateException("runTestScripts() can only be called on TEST ScriptManager");
            }
            com.tkisor.nekojs.script.ScriptTypeEnv.logger(scriptType).info("正在运行 TEST 脚本...");

            // TEST 也走事务式 reload：失败时保留上一个 TEST Context，而不是销毁后再尝试加载。
            reloadScriptsTransactional();
            flushTestTimers();

            com.tkisor.nekojs.script.ScriptTypeEnv.logger(scriptType).info("TEST 脚本运行完毕。");
        }

        private void flushTestTimers () {
            // 一次快照读取 + 同一把锁对象：旧实现每轮重读 volatile context 当锁，reload 并发
            // 时会出现「这轮锁 A、下轮锁 B」甚至 NPE；快照保证整轮循环锁同一 Context
            RuntimeEnvironment env = this.runtime;
            NekoNodeRuntime nodeRuntime = env.nodeRuntime();
            Context context = env.context();
            if (nodeRuntime == null || context == null) return;
            // 上限 1000 轮（≈1s）：覆盖常见的 await setTimeout(...) 异步断言；
            // 失控 interval 也会在此截止，不会挂死 /nekojs test
            for (int i = 0; i < 1000 && nodeRuntime.hasPendingTimers(); i++) {
                synchronized (context) {
                    nodeRuntime.flushReadyTimers();
                }
                try {
                    Thread.sleep(1L);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
            synchronized (context) {
                nodeRuntime.flushReadyTimers();
            }
        }

        // ---- 环境重置 / 关闭 ----

        private void resetEnvironment () {
            fullReloadCleanup();
            for (var binding : pluginRuntime.bindings(scriptType).values()) {
                binding.close(scriptType);
            }

            RuntimeEnvironment oldEnvironment = this.runtime;
            this.runtime = RuntimeEnvironment.EMPTY;
            closeRuntimeResources(oldEnvironment);
        }

        private void closeRuntimeResources (RuntimeEnvironment environment){
            NekoNodeRuntime oldRuntime = environment.nodeRuntime();
            Context oldContext = environment.context();
            if (oldRuntime != null) {
                try {
                    oldRuntime.close();
                } catch (Exception e) {
                    com.tkisor.nekojs.script.ScriptTypeEnv.logger(scriptType).warn("关闭旧 Node runtime 时发生异常", e);
                }
            }
            if (oldContext != null) {
                ScriptContextRegistry.unbind(oldContext);
                CONTEXT_TO_MANAGER.remove(oldContext);
                try {
                    // 关闭前先取 Context 自己的 monitor：tick/分发线程可能正执行在
                    // synchronized(context) 里，无锁直接 close 会撞上 Graal 单线程约束
                    // （reload 后日志刷 "The Context is already closed"）
                    synchronized (oldContext) {
                        oldContext.close();
                    }
                } catch (Exception e) {
                    com.tkisor.nekojs.script.ScriptTypeEnv.logger(scriptType).warn("关闭旧上下文时发生异常", e);
                }
            }
            // Graal 关闭 Context 时只 detach out/err 流、不 flush 也不 close，脚本末尾未以
            // 换行结束的输出会滞留在 LoggerStream 行缓冲中丢失。这里在 context.close() 之后
            // 补一次 close()（幂等冲刷残留缓冲）；必须在 Context 关闭之后——先关流再关
            // Context 会把残余写入路由到已关闭的流。
            closeStreamQuietly(environment.outStream());
            closeStreamQuietly(environment.errStream());
        }

        private static void closeStreamQuietly (LoggerStream stream){
            if (stream == null) return;
            try {
                stream.close();
            } catch (Exception ignored) { // 冲刷失败不应中断销毁流程
            }
        }

        // synchronized：与 loadScripts / reloadScripts 共用实例锁，防止 shutdown 与并发
        // reload/test 交错时销毁半初始化的环境（可重入：closeRuntimeResources 无锁）。
        @Override
        public synchronized void close () {
            fullReloadCleanup();
            for (var binding : pluginRuntime.bindings(scriptType).values()) {
                binding.close(scriptType);
            }
            closeRuntimeResources(this.runtime);
            this.runtime = RuntimeEnvironment.EMPTY;
        }

        // ---- 查询 ----

        public boolean hasScripts () {
            return scripts != null && !scripts.isEmpty();
        }

        private ScriptEventRegistrar getScriptEventRegistrar () {
            return scriptEventBridge.scriptEventRegistrar();
        }

        public void flushReadyNodeTimers () {
            // 快照读取：context 与 nodeRuntime 必须来自同一成对发布，且整段用同一把锁。
            // 旧实现两次独立字段读 + 每次以「刚读到的 context」为锁，reload 并发时会把
            // 新 runtime 的 timer 打进旧 Context 的锁里（或反之），Graal 单线程约束直接报
            // Multi threaded access。
            RuntimeEnvironment env = this.runtime;
            Context context = env.context();
            NekoNodeRuntime nodeRuntime = env.nodeRuntime();
            if (context != null && nodeRuntime != null) {
                synchronized (context) {
                    nodeRuntime.flushReadyTimers();
                }
            }
        }

        // ---- Context 身份管理（委托 ScriptContextRegistry） ----

        /**
         * 从上下文获取对应的脚本类型
         */
        public static ScriptType getTypeFromContext (Context context){
            return ScriptContextRegistry.scriptTypeOf(context);
        }

        public static String switchCurrentScriptId (Context context, String scriptId){
            return ScriptContextRegistry.switchCurrentScriptId(context, scriptId);
        }

        public static void restoreCurrentScriptId (Context context, String scriptId){
            ScriptContextRegistry.restoreCurrentScriptId(context, scriptId);
        }

        public static String getCurrentScriptId (Context context){
            return ScriptContextRegistry.currentScriptIdOf(context);
        }
    }
