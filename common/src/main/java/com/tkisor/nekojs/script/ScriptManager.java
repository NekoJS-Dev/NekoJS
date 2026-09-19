package com.tkisor.nekojs.script;

import com.tkisor.nekojs.script.ScriptTypeEnv;
import com.tkisor.nekojs.api.ScriptType;
import com.tkisor.nekojs.core.lifecycle.CandidateDomainCollector;
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
import com.tkisor.nekojs.core.error.DefaultErrorTracker;
import com.tkisor.nekojs.core.error.ScriptError;
import com.tkisor.nekojs.core.fs.ClassFilter;
import com.tkisor.nekojs.core.fs.NekoJSPaths;
import com.tkisor.nekojs.core.lifecycle.NekoReloadException;
import com.tkisor.nekojs.core.lifecycle.ReloadFailureReport;
import com.tkisor.nekojs.core.lifecycle.ReloadPhase;
import com.tkisor.nekojs.core.lifecycle.ReloadProgressTracker;
import com.tkisor.nekojs.core.lifecycle.ScriptLifecycleGate;
import com.tkisor.nekojs.core.log.LoggerStream;
import com.tkisor.nekojs.core.module.NekoModulePipelineCache;
import com.tkisor.nekojs.core.node.NekoNodeRuntime;
import com.tkisor.nekojs.api.plugin.IPluginRuntime;
import com.tkisor.nekojs.script.ScriptContextRegistry;
import com.tkisor.nekojs.script.prop.ScriptPropertyRegistry;
import graal.graalvm.polyglot.Context;
import graal.graalvm.polyglot.Value;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Callable;
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
    /** root 拥有的候选域收集器（票 39 DOMAIN_PLAN 阶段消费；按引用与 root 共享）。 */
    private final List<com.tkisor.nekojs.core.lifecycle.CandidateDomainCollector> domainCollectors;
    /**
     * prepared 模块缓存（票 11 W3 显式注入）：生产经 {@code NekoRuntimeRoot} 传入
     * root 拥有的实例（模块 session 生命周期归属）；旧构造器自建隔离实例。
     */
    /** Root-owned cache/session owner; mutable module entries live only in generation sessions. */
    private final NekoModulePipelineCache preparationCache;

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
                                      LoggerStream outStream, LoggerStream errStream,
                                      com.tkisor.nekojs.core.state.GenerationGlobals globals,
                                      NekoModulePipelineCache moduleSession) {
        static final RuntimeEnvironment EMPTY = new RuntimeEnvironment(null, null, null, null, null, null);

        boolean isEmpty() {
            return context == null;
        }
    }

    private volatile RuntimeEnvironment runtime = RuntimeEnvironment.EMPTY;
    private List<ScriptContainer> scripts;

    /**
     * 已提交的 generation 序号（单调递增）。
     *
     * <p>写入全部发生在 owner thread 的实例锁临界区内（initial load / kill 重建 / 事务
     * commit）；但 {@link #generationId()} 是公开读点，命令面与平台调用方可能在
     * 其它线程读它（审查 A3）——因此与同组发布的其它状态一样取 volatile，代价可忽略
     * （每次 reload 写一次），换取「读点无需自带锁」的明确语义。
     */
    private volatile long generation;

    /**
     * 构建中的候选 generation Context；null 表示当前没有候选在构建。
     * 候选阶段（preparation/execution/binding）内该 Context 是「临时存活」：
     * timer 回调可在候选执行路径（ScriptExecutor.waitForEvaluation 的候选 flush）中运行，
     * 但候选监听器只收集（{@link #pendingListeners}）、不挂生产总线，commit 前不可见。
     */
    private volatile Context candidateContext;

    /**
     * 构建中的候选完整环境（与 {@link #candidateContext} 成对发布）：close 侧
     * 抢占在途 candidate 时据此中断候选求值、并在确定性 teardown 中丢弃候选全部
     * 资源。只在实例锁临界区内写入/清理；close 在拿锁前读到旧值只影响中断加速，
     * 不影响正确性（正确性由 commit 前的 closeRequested 检查保证）。
     */
    private volatile RuntimeEnvironment candidateEnvironment;

    /** 候选环境被语句上限杀死（与 active 的 {@link #contextKilled} 分开记账）。 */
    private volatile boolean candidateKilled;

    /**
     * 候选加载中首个触发语句上限 kill 的<strong>脚本</strong>（不是 source 文本）：
     * 仅用于候选失败结果的 source location 归因（见 {@link #sourceOf}）。
     * 命名刻意避开 "Source"，以免被误读为 source 字符串。
     */
    private ScriptContainer candidateKillScript;

    /** Active error state saved while candidate execution is allowed to reuse script ids. */

    /**
     * 候选 generation 收集的挂起监听器注册（EventBusJS.PendingListener）。
     * 只在 owner thread（reload 持实例锁）上读写；commit 点统一激活，失败随候选丢弃。
     */
    private final List<com.tkisor.nekojs.api.event.EventBusJS.PendingListener> pendingListeners = new ArrayList<>();

    /** 一次性标记：STARTUP reload 的非事务语义只警告一次（每个 ScriptType 一个实例）。 */
    private boolean warnedStartupReloadNonTransactional;

    /** Graal 因语句上限（scriptStatementLimit）关闭了当前 Context；下次取用时重建。 */
    private volatile boolean contextKilled;

    /**
     * 同一 ScriptType 的 owner-thread 生命周期调度门（票 07）：reload 不重入、
     * 回调内请求拒绝、close 优先与关闭后拒绝、active watchdog 隔离失败标记。
     * 跨线程串行仍由实例锁（monitor 队列）承担，本门只做实例锁表达不了的判决；
     * 除 volatile 标志外所有方法必须在持有实例锁的前提下调用。
     */
    private final ScriptLifecycleGate lifecycleGate = new ScriptLifecycleGate();

    // ---- 构造函数 ----

    /**
     * 票 39：带候选域收集器集合的构造形态——{@code NekoRuntimeRoot.createScriptManager}
     * 传入 root 拥有的收集器注册表（reload 的 DOMAIN_PLAN 阶段消费；见
     * {@link com.tkisor.nekojs.core.lifecycle.CandidateDomainCollector}）。
     * 收集器按引用共享（root 注册晚于 manager 创建也可见），读取只发生在 reload
     * 的 owner thread 临界区内。
     */
    /**
     * 票 11 W3：带 root 拥有的 prepared 缓存的构造形态——{@code NekoRuntimeRoot.createScriptManager}
     * 传入与执行环境侧（sandbox factory / module host / filesystem）共享的同一实例。
     */
    public ScriptManager(ScriptType scriptType, ScriptEventBridge scriptEventBridge, IPluginRuntime pluginRuntime, ScriptPropertyRegistry scriptProperties, ErrorTracker errorTracker, NekoJSPaths paths, SandboxConfig sandboxConfig, ScriptEnvironmentFactory environmentFactory, List<com.tkisor.nekojs.core.lifecycle.CandidateDomainCollector> domainCollectors, NekoModulePipelineCache preparationCache) {
        this.scriptType = scriptType;
        this.scriptEventBridge = scriptEventBridge;
        this.pluginRuntime = pluginRuntime;
        this.scriptProperties = scriptProperties;
        this.errorTracker = errorTracker;
        this.paths = paths;
        this.sandboxConfig = sandboxConfig;
        this.scriptExecutor = new ScriptExecutor(errorTracker, paths, sandboxConfig, this::markContextKilled);
        this.environmentFactory = environmentFactory;
        this.domainCollectors = domainCollectors;
        this.preparationCache = preparationCache;
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

    // ---- 票 10：候选域计划联合边界 seam ----

    /**
     * 候选执行期间的联合计划注册 seam（票 10 AC5）：来源 Context 属于构建中的候选
     * generation 时，把计划挂进该候选的 {@code GenerationGlobals}（STATE_PLAN 阶段统一
     * 联合预检、commit 点联合发布或随失败全部不发布），返回 true；非候选 Context 返回
     * false（调用方自行决定是否拒绝）。Java 侧绑定/测试计划由此进入联合边界，领域语义
     * 不进入 global owner。
     */
    public static boolean registerCandidatePlan(Context context,
            com.tkisor.nekojs.core.state.CandidateStatePlan plan) {
        if (context == null || plan == null) return false;
        ScriptManager manager = CONTEXT_TO_MANAGER.get(context);
        if (manager == null) return false;
        Context candidate = manager.candidateContext;
        if (candidate == null || !candidate.equals(context)) return false;
        RuntimeEnvironment candidateEnvironment = manager.candidateEnvironment;
        if (candidateEnvironment == null || candidateEnvironment.globals() == null) return false;
        candidateEnvironment.globals().addPlan(plan);
        return true;
    }

    // ---- 候选收集把手（DOMAIN_PLAN 阶段构造，生命周期只在 collect 调用内） ----

    /**
     * {@link CandidateDomainCollector.Handle} 的实现：收集派发只读本候选
     * {@link #pendingListeners} 中属于目标总线的子集（commit 前不上生产总线），按与
     * {@code EventBusBase} 编译快照相同的顺序（priority 降序、同优先级保持注册序）稳定
     * 排序；监听器回调异常按收集语义向上传播（候选失败或由收集器自行处置）。
     */
    private final class CandidateCollectionHandleImpl implements CandidateDomainCollector.Handle {

        private final Context candidate;

        CandidateCollectionHandleImpl(Context candidate) {
            this.candidate = candidate;
        }

        @Override
        public Context candidateContext() {
            return candidate;
        }

        @Override
        public ScriptType scriptType() {
            return ScriptManager.this.scriptType;
        }

        @Override
        public List<com.tkisor.nekojs.api.event.EventBusJS.PendingListener> listenersOf(
                com.tkisor.nekojs.api.event.EventBusJS<?, ?> bus) {
            if (bus.canDispatch()) {
                // 按 key 定向分发的总线需要 key 才能判定投递子集；收集派发没有 key 上下文，
                // 显式拒绝而不是「忽略 key 全量派发」静默降级（见 Handle#listenersOf 契约）。
                throw new UnsupportedOperationException(
                        "domain collection dispatch does not support key-dispatched buses: " + bus);
            }
            List<com.tkisor.nekojs.api.event.EventBusJS.PendingListener> ofBus = new ArrayList<>();
            for (com.tkisor.nekojs.api.event.EventBusJS.PendingListener pending : pendingListeners) {
                if (pending.owner() == bus) {
                    ofBus.add(pending);
                }
            }
            // priority 降序（HIGHEST=Byte.MAX_VALUE 在前）；List.sort 稳定 → 同优先级保持注册序
            ofBus.sort((a, b) -> -Byte.compare(a.priority(), b.priority()));
            return List.copyOf(ofBus);
        }

        @Override
        public void execute(com.tkisor.nekojs.api.event.EventBusJS.PendingListener listener, Object event) {
            listener.executeForCollection(event);
        }

        @Override
        public void registerPlan(com.tkisor.nekojs.core.state.CandidateStatePlan plan) {
            if (!registerCandidatePlan(candidate, plan)) {
                throw new IllegalStateException("candidate plan registration failed for " + plan.domain()
                        + ": source context is not the candidate being built");
            }
        }
    }

    // ---- 票 07：回调标记、公开观察点与显式调度入口 ----

    /**
     * managed 回调进入（票 07 回调内 reload 契约）：EventBusJS 四个分发点与
     * NekoNodeTimers 回调执行体在执行 guest 回调前后配对调用。只做调用线程的
     * 本地计数，不加锁、不触碰 Context；同线程的 lifecycle 请求看到计数即返回
     * 明确拒绝，不递归、不同步等待自身队列。
     */
    public static void noteCallbackEnter() {
        ScriptLifecycleGate.enterCallback();
    }

    /** 与 {@link #noteCallbackEnter()} 配对的回调退出（finally 内调用）。 */
    public static void noteCallbackExit() {
        ScriptLifecycleGate.exitCallback();
    }

    /**
     * watchdog 终止 active 后的隔离失败是否生效：true 表示 active 已死、已停止
     * 向被杀 Context 分发（isContextDead）、等待显式 reload/load 恢复，期间不会
     * 自动创建第二个 active。公开可观察点（命令面/测试断言用），不是内部锁状态。
     */
    public boolean isActiveFailed() {
        return lifecycleGate.isActiveFailed();
    }

    /** 终端关闭是否完成：true 后一切新 lifecycle 请求拒绝。 */
    public boolean isClosed() {
        return lifecycleGate.isClosed();
    }

    /** close 是否已被请求但尚未完成（尚未开始的 reload/load 会因此被拒绝）。 */
    public boolean isCloseRequested() {
        return lifecycleGate.isCloseRequested();
    }

    /**
     * 回调/任意线程侧的显式 reload 入口（票 07 AC1/AC2）：与 {@link #reloadScripts()}
     * 同一调度门。门拒绝（重入/回调内/关闭中/已关闭）时返回判决、不执行任何工作；
     * 门通过时同步执行（跨线程调用先在实例锁 monitor 队列排队——即「排队成功」的
     * 可观察结果），执行体失败仍抛 {@link NekoReloadException}（结构化阶段结果），
     * 与直接调用一致。
     */
    public synchronized ScriptLifecycleGate.Decision requestReload() {
        ScriptLifecycleGate.Decision decision =
                lifecycleGate.tryEnter(ScriptLifecycleGate.Operation.RELOAD);
        if (decision != ScriptLifecycleGate.Decision.EXECUTED) {
            return decision;
        }
        try {
            doReloadScripts();
            return ScriptLifecycleGate.Decision.EXECUTED;
        } finally {
            lifecycleGate.exit(ScriptLifecycleGate.Operation.RELOAD);
        }
    }

    /**
     * 非 owner / guest-created thread 访问 managed lifecycle 的显式调度入口
     * （票 07 AC4/AC7）：work 在实例锁的 monitor 队列里与 evaluate/reload/close
     * 单一序列执行，不与其它 lifecycle 并发触碰 Context、binding、listener 或
     * timer。本方法本身不进入 lifecycle 门，work 内的 lifecycle 调用各自过门；
     * guest 的高级 Java/线程能力（allowThreads 等）不受影响，只约束 managed
     * lifecycle 的进入点。已关闭时明确拒绝。
     */
    public synchronized <T> T scheduleOnOwner(Callable<T> work) throws Exception {
        if (lifecycleGate.isClosed()) {
            throw new IllegalStateException(
                    "ScriptManager[" + scriptType.name + "] is closed; scheduleOnOwner rejected");
        }
        if (work == null) {
            throw new NullPointerException("scheduleOnOwner work");
        }
        return work.call();
    }

    // ---- 配置 ----

    public void setJavaClassLoadTelemetrySink(JavaClassLoadTelemetrySink sink) {
        JavaClassLoadTelemetry.setSink(sink);
    }

    // ---- Context 访问（懒初始化） ----

    /**
     * ScriptExecutor 回调：Graal 因语句上限/watchdog 关闭了求值所属的 Context（active 或候选）。
     * 包可见：同包回归测试直接注入旧 generation Context 的 kill 上报（commit 边界竞态窗口）。
     */
    void markContextKilled(Context context) {
        Context candidate = this.candidateContext;
        if (candidate != null && candidate.equals(context)) {
            this.candidateKilled = true;
        } else {
            Context active = this.runtime.context();
            if (active == null || !active.equals(context)) {
                // 旧 generation 残留闭包在 commit 清扫与总线激活之间被并发 dispatch 时的
                // kill 上报：此类 Context 既非 active 也非候选，isContextDead 判 dead 且无
                // 副作用，这里同样忽略——否则健康的新 active 会被误标隔离失败，且下次
                // getOrCreateEnvironment 自动重建，违反「不自动创建第二个 active」（AC6）。
                return;
            }
            this.contextKilled = true;
            // 票 07 隔离失败（AC6）：active 被 watchdog/资源上限终止后停止向其分发
            // （isContextDead 判 contextKilled）、不自动创建第二个 active，只由显式
            // reload/load 成功或 close 清除。timer/event 分发路径从不重建（它们不调
            // getOrCreateEnvironment），恢复入口只有显式 lifecycle。
            this.lifecycleGate.markActiveFailed();
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
            NekoModulePipelineCache moduleSession = preparationCache.openSession();
            ScriptEnvironmentFactory.Environment env;
            try {
                env = environmentFactory.createContext(scriptType,
                        environmentFactory.newGeneration(scriptType, false), moduleSession);
                environmentFactory.installEnvironmentBindings(env.context(), scriptType, env.globals());
            } catch (RuntimeException | Error failure) {
                moduleSession.closeSession();
                throw failure;
            }
            RuntimeEnvironment created = new RuntimeEnvironment(
                    env.context(), env.nodeRuntime(), env.outStream(), env.errStream(), env.globals(), moduleSession);
            activateModuleViews(created.context(), moduleSession);
            this.runtime = created;
            CONTEXT_TO_MANAGER.put(created.context(), this);
            ScriptContextRegistry.bind(created.context(), scriptType);
            contextKilled = false;
            // 显式 lifecycle 入口上的重建即恢复（AC6）：清除隔离失败。本方法只被显式
            // lifecycle（loadScripts / reloadScriptFile 的取用点）调用，timer/event
            // 分发路径不经过这里，不会悄悄清除隔离失败。
            lifecycleGate.clearActiveFailed();
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
        // 票 07：同一 ScriptType 的 evaluate/reload/close 单一序列由实例锁承担；
        // 门只判决重入（STARTUP 的 RELOAD 内嵌套 LOAD 放行）、回调内请求与
        // close 优先/已关闭。拒绝时抛结构化失败，不递归、不同步等待自身队列。
        ScriptLifecycleGate.Decision decision =
                lifecycleGate.tryEnter(ScriptLifecycleGate.Operation.LOAD);
        if (decision != ScriptLifecycleGate.Decision.EXECUTED) {
            throw reloadRejected(decision, "load");
        }
        try {
            doLoadScripts();
            // 显式 load 入口上的成功即恢复（AC6）；但本轮内 active 若刚被 kill
            // （contextKilled，如失控入口），隔离失败必须保留而不是刚记上就清除。
            if (!contextKilled) {
                lifecycleGate.clearActiveFailed();
            }
        } finally {
            lifecycleGate.exit(ScriptLifecycleGate.Operation.LOAD);
        }
    }

    private void doLoadScripts() {
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
     * 在 active 环境中执行给定脚本列表：preload、按 priority 与 after 依赖排序、逐个执行入口。
     *
     * <p>只被首次 {@link #loadScripts()}（平台 init / STARTUP reset+load / kill 重建）使用：
     * 每个脚本单独取一次 {@link #getOrCreateEnvironment()}，因此某脚本被语句上限杀死后，
     * 后续脚本仍能在自动重建的环境里继续跑。事务式 reload 的候选执行走
     * {@link #loadCandidateScripts}（固定候选 Context + 候选 kill 归因），不复用本方法。
     * 空列表只记录日志，不创建副作用。
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

    private boolean prepareScriptsForLoad(List<ScriptContainer> scriptsToLoad) {
        return prepareScriptsForLoad(scriptsToLoad, null);
    }

    private boolean prepareScriptsForLoad(List<ScriptContainer> scriptsToLoad, Context context) {
        if (scriptsToLoad == null || scriptsToLoad.isEmpty()) {
            com.tkisor.nekojs.script.ScriptTypeEnv.logger(scriptType).info("没有需要加载的 {} 脚本。", scriptType.name());
            return false;
        }
        for (var script : scriptsToLoad) {
            script.preload();
            reportPreloadFailure(script, context);
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
        reportPreloadFailure(script, null);
    }

    private void reportPreloadFailure(ScriptContainer script, Context context) {
        if (script.disabled && script.lastError != null) {
            errorTracker.record(context, script, script.lastError);
            com.tkisor.nekojs.script.ScriptTypeEnv.logger(scriptType).error("无法读取脚本 {}，已跳过：{}", script.path, script.lastError.toString());
        }
    }

        // ---- 重载 ----

        public synchronized void reloadScripts () {
            // 票 07 调度门：并发 reload 在实例锁 monitor 队列排队（每轮恰好一个
            // candidate）；同线程重入/回调内请求明确拒绝；closeRequested/closed 拒绝。
            ScriptLifecycleGate.Decision decision =
                    lifecycleGate.tryEnter(ScriptLifecycleGate.Operation.RELOAD);
            if (decision != ScriptLifecycleGate.Decision.EXECUTED) {
                throw reloadRejected(decision, "reload");
            }
            try {
                doReloadScripts();
            } finally {
                lifecycleGate.exit(ScriptLifecycleGate.Operation.RELOAD);
            }
        }

        private void doReloadScripts () {
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
         * <p>commit 点不可失败（审查 A1）：激活监听器前，EVENT_PLAN 阶段已把 dispatch key
         * 转换等在 commit 期才会抛的工作前移完成（{@link EventBusJS.PendingListener#prepareForActivation()}）。
         * 因此候选期的任何失败都发生在 commit 之前，必然走 {@link #discardCandidate} 路径，
         * 不会出现「旧监听器已清扫 + 候选 runtime 已发布 + 激活中途抛」的半激活 generation。
         *
         * <p>线程约定：候选构建/执行在当前调用线程（owner thread）同步完成，reload 由
         * 实例锁串行；owner-thread 队列、重入与 watchdog 调度归工单 07。
         * binding.close(type)（进程级注册账本重置，域 Adapter 持有）保持候选构建前调用：
         * 其「先清账本再注册」的顺序是领域契约，账本快照/回滚归 W6/W7 域 Adapter。
         */
        private void reloadScriptsTransactional () {
            ReloadProgressTracker.begin(scriptType.name, 7);
            boolean progressSuccess = false;
            try {
                com.tkisor.nekojs.script.ScriptTypeEnv.logger(scriptType).info("正在重载 {} 脚本...", scriptType.name());
                long candidateGeneration = this.generation + 1;
                RuntimeEnvironment oldEnvironment = this.runtime;
                RuntimeEnvironment candidateEnvironment = null;
                try {
                    // Candidate errors are staged by Context. Keep active errors live so an
                    // active callback observed during candidate execution is not lost on failure.
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

                    // ---- Phase BINDING：事件组/插件 binding/schema/global+shared 视图安装进候选 Context ----
                    try {
                        environmentFactory.installEnvironmentBindings(
                                candidateEnvironment.context(), scriptType, candidateEnvironment.globals());
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
                        this.candidateKillScript = null;
                        pluginRuntime.fireBeforeScriptsLoaded(scriptType);
                        try {
                            loadCandidateScripts(candidateScripts, candidateEnvironment.context(), candidateEnvironment.nodeRuntime());
                        } finally {
                            pluginRuntime.fireAfterScriptsLoaded(scriptType);
                        }
                        ReloadProgressTracker.step(scriptType.name, "candidate scripts executed");

                        if (this.candidateKilled) {
                            // 候选加载期间被脚本资源上限/watchdog 终止（scriptStatementLimit 总量、
                            // 墙钟守卫或 close 抢占的中断），Graal 已按终止语义处置候选 Context：
                            // 按失败处理，不把死掉的候选提交为 live（票 07 watchdog 面）。
                            // close 抢占的中断以 close-preempted domain 归因（AC3）。
                            boolean preempted = lifecycleGate.isCloseRequested() || lifecycleGate.isClosed();
                            throw reloadFailure(candidateGeneration, ReloadPhase.EXECUTION,
                                    sourceOf(this.candidateKillScript),
                                    preempted ? "close-preempted" : "candidate-killed",
                                    new RuntimeException(scriptType.name()
                                            + (preempted
                                                    ? " reload preempted by close during candidate execution"
                                                    : " candidate context was terminated by script resource limits"
                                                            + " (runaway watchdog / statement limit) during reload")));
                        }
                    } catch (NekoReloadException f) {
                        throw f;
                    } catch (Throwable t) {
                        throw reloadFailure(candidateGeneration, ReloadPhase.EXECUTION, null, "script-execution", t);
                    }

                    // ---- Phase EVENT_PLAN：候选挂起监听器的完整性/可挂载性预备 ----
                    // 审查 A1（commit 点不可回滚）：把 commit 期会抛的工作——EventBusJS
                    // 的 dispatch key 转换（Value.as(keyType)）——前移到这里完成。候选监听器
                    // 在提交前仍不上生产总线；本阶段只解析 key，不产生任何 bus/mirror 副作用。
                    // 失败因此发生在 commit 之前，走 discardCandidate 路径（候选资源全关、
                    // active 原样），而不是「旧监听器已清扫 + 已发布候选 runtime + 激活中途抛」
                    // 的半激活状态；commitGeneration 的激活步骤至此只剩不会抛的注册操作。
                    try {
                        for (var pending : List.copyOf(this.pendingListeners)) {
                            try {
                                pending.prepareForActivation();
                            } catch (Throwable t) {
                                throw reloadFailure(candidateGeneration, ReloadPhase.EVENT_PLAN,
                                        sourceOfScriptId(candidateScripts, pending.scriptId()),
                                        "candidate-listener-plan", t);
                            }
                        }
                    } catch (NekoReloadException f) {
                        throw f;
                    } catch (Throwable t) {
                        throw reloadFailure(candidateGeneration, ReloadPhase.EVENT_PLAN, null,
                                "candidate-listener-plan", t);
                    }
                    ReloadProgressTracker.step(scriptType.name, "candidate event plan prepared");

                    // ---- Phase DOMAIN_PLAN：领域收集（票 39）----
                    // root 拥有的领域收集器把收集事件派发进候选挂起监听器（只读本候选的
                    // pendingListeners 子集，不上生产总线），产出 inert 领域计划（如 Item/Block
                    // modification）挂入候选联合边界——与 global/shared 顶层写集一起在
                    // STATE_PLAN 联合预检、commit 点联合发布（AC9：联合成功或失败，不半提交）。
                    // 收集器自身异常与监听器回调异常在此冒泡 → 候选失败，保留旧 active
                    //（AC4）；收集顺序 = 收集器注册序。
                    for (com.tkisor.nekojs.core.lifecycle.CandidateDomainCollector collector : List.copyOf(domainCollectors)) {
                        if (collector.scriptType() != scriptType) continue;
                        try {
                            collector.collect(new CandidateCollectionHandleImpl(candidateEnvironment.context()));
                        } catch (NekoReloadException f) {
                            throw f;
                        } catch (Throwable t) {
                            throw reloadFailure(candidateGeneration, ReloadPhase.DOMAIN_PLAN, null,
                                    "domain-collect:" + collector.domain(),
                                    new IllegalStateException(scriptType.name + " candidate domain collection failed for '"
                                            + collector.domain() + "'", t));
                        }
                    }
                    ReloadProgressTracker.step(scriptType.name, "candidate domain plans collected");

                    // ---- Phase STATE_PLAN：受管状态联合预检（票 10）----
                    // global 私有写集 + shared 写集 + 外部候选计划（CandidateStatePlan）联合
                    // 预检：其他 writer 在候选期间提交过同一受管顶层 key（或 clear 之后提交过
                    // 该 store）即冲突——candidate 失败、写集全部不发布、其他 writer 的已提交
                    // 值保留（不丢写）。权威复验仍在 commit 点的 publishJoint 内（锁内），此处
                    // 提前失败只为把冲突归因到候选阶段并避免无谓的 commit 期工作。
                    try {
                        candidateEnvironment.globals().preflightJoint();
                    } catch (com.tkisor.nekojs.core.state.GlobalStateException e) {
                        throw reloadFailure(candidateGeneration, ReloadPhase.STATE_PLAN, null, e.domain(), e);
                    } catch (Throwable t) {
                        throw reloadFailure(candidateGeneration, ReloadPhase.STATE_PLAN, null,
                                "state-plan-unknown", t);
                    }
                    ReloadProgressTracker.step(scriptType.name, "candidate state plan validated");

                    // ---- COMMIT 前 close 抢占检查（票 07 AC3）：close 优先于尚未
                    // 提交的 candidate——closeRequested 已置位时（无论在途候选是被
                    // 中断加速失败还是恰好走到这里），候选在此丢弃，永不出现
                    // 「半激活 generation」或「close 之后又 commit」。
                    if (lifecycleGate.isCloseRequested() || lifecycleGate.isClosed()) {
                        discardCandidate(candidateEnvironment);
                        throw reloadFailure(candidateGeneration, ReloadPhase.COMMIT, null,
                                "close-preempted",
                                new IllegalStateException(scriptType.name()
                                        + " reload preempted by close before commit"));
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
         * 创建候选 generation 环境：Context + node runtime + <b>事务 generation 的
         * global/shared 视图</b>（票 10：候选顶层写进写集，commit 才发布），并登记
         * Context → manager 映射与候选标记（候选 timer 回调的存活判定依赖该登记）。
         * BINDING 阶段由 reloadScriptsTransactional 单独执行（保持票 06 的阶段拆分）。
         */
        private RuntimeEnvironment createCandidateEnvironment () {
            com.tkisor.nekojs.core.state.GenerationGlobals candidateGlobals =
                    environmentFactory.newGeneration(scriptType, true);
            NekoModulePipelineCache moduleSession = preparationCache.openSession();
            ScriptEnvironmentFactory.Environment candidate;
            try {
                candidate = environmentFactory.createContext(scriptType, candidateGlobals, moduleSession);
            } catch (RuntimeException | Error failure) {
                moduleSession.closeSession();
                throw failure;
            }
            RuntimeEnvironment candidateEnvironment = new RuntimeEnvironment(
                    candidate.context(), candidate.nodeRuntime(), candidate.outStream(),
                    candidate.errStream(), candidate.globals(), moduleSession);
            activateCandidateModuleViews(candidate.context(), moduleSession);
            CONTEXT_TO_MANAGER.put(candidate.context(), this);
            ScriptContextRegistry.bind(candidate.context(), scriptType);
            this.candidateContext = candidate.context();
            this.candidateEnvironment = candidateEnvironment;
            this.candidateKilled = false;
            this.candidateKillScript = null;
            this.pendingListeners.clear();
            return candidateEnvironment;
        }

        /**
         * 单一 commit 点（工单 06）：candidate 全部阶段通过后的原子切换。
         *
         * <p>顺序（owner thread 临界区内完成）：
         * <ol>
         *   <li>受管状态联合发布（票 10，步骤 0）：global 私有 + shared 写集与外部候选计划
         *       在同一持锁内联合发布——无半提交。只可能在未发生任何 commit 期变更时抛出
         *       （复验冲突/计划违反 publish 契约），抛出即走 discardCandidate、active 完整；</li>
         *   <li>清扫旧 generation 监听器——此刻总线 type 桶里只有旧 generation 的 token
         *       （候选监听器是挂起收集、从未上总线），整类型清空即旧 generation 清扫；
         *       共享静态总线无法按 generation 分桶，此步与下一步合起来等价于「切换生产
         *       路由 + 旧 generation 停止接收」，跨线程 dispatch 的临界区安全由 07 的
         *       owner-thread 序列化补齐；</li>
         *   <li>发布新 runtime：生产 timer flush 目标与 isContextDead 判定同步切换，
         *       同一事件自此只由新 generation 接收（旧闭包经 isContextDead 判 dead 双保险）；</li>
         *   <li>激活候选挂起监听器：新 generation 成为唯一新 callback 接收者。挂起注册的
         *       key 转换已在候选 EVENT_PLAN 阶段完成，此步只剩不会抛的注册操作（审查 A1）；</li>
         *   <li>释放旧 module session（编译模块缓存 / 虚拟 ESM URI 按类型清除）；</li>
         *   <li>按 timer、Context 所有权顺序释放旧环境（closeRuntimeResources：
         *       node runtime/timer → Context → streams；期间旧 generation 的 global/shared
         *       guest 值按 generation 失效，非 guest 值保留在 root 级 store）。</li>
         * </ol>
         */
        private void commitGeneration (long candidateGeneration, RuntimeEnvironment candidateEnvironment,
                List<ScriptContainer> candidateScripts, RuntimeEnvironment oldEnvironment) {
            // (0) 受管状态联合发布（票 10，commit 点第一步）：global 私有 + shared 写集与外部
            // 候选计划在同一持锁内联合发布（无半提交）。此步只可能在「尚未发生任何 commit 期
            // 变更」时抛出（STATE_PLAN 与 commit 之间有其他 writer 提交的复验冲突、或外部计划
            // 违反 publish 契约）——异常向上传播走 reloadScriptsTransactional 的失败路径：
            // discardCandidate 丢弃候选，active 的监听器/runtime/generation 完整保留。
            // 归因口径：失败发生在 commit 点但报 ReloadPhase.STATE_PLAN——按「候选期状态计划
            // 冲突」而非「COMMIT 失败」归类，由 GlobalStateException.domain 消歧（审查 F4）。
            try {
                candidateEnvironment.globals().publishJoint();
            } catch (com.tkisor.nekojs.core.state.GlobalStateException e) {
                throw reloadFailure(candidateGeneration, ReloadPhase.STATE_PLAN, null, e.domain(), e);
            } catch (Throwable t) {
                throw reloadFailure(candidateGeneration, ReloadPhase.STATE_PLAN, null,
                        "state-plan-unknown", t);
            }
            // (1) 旧 generation 监听器清扫
            scriptEventBridge.clearListeners(scriptType);
            // (2) 生产路由切换：新 generation 成为 live 环境
            this.runtime = candidateEnvironment;
            DefaultErrorTracker defaultTracker = defaultErrorTracker();
            if (defaultTracker != null) {
                defaultTracker.publishCandidateErrors(scriptType, candidateEnvironment.context());
            }
            activateModuleViews(candidateEnvironment.context(), candidateEnvironment.moduleSession());
            this.scripts = candidateScripts;
            this.generation = candidateGeneration;
            this.contextKilled = false;
            this.candidateContext = null;
            this.candidateEnvironment = null;
            this.candidateKilled = false;
            // 成功提交即显式恢复（AC6）：清除隔离失败（若此前 active 曾被 watchdog 终止）。
            lifecycleGate.clearActiveFailed();
            // (3) 激活候选挂起监听器（新 generation 唯一 callback 接收者）
            List<com.tkisor.nekojs.api.event.EventBusJS.PendingListener> activation =
                    List.copyOf(this.pendingListeners);
            this.pendingListeners.clear();
            for (var pending : activation) {
                pending.activate();
            }
            // (4) 旧环境按所有权顺序释放：timer → Context → streams → module session
            if (!oldEnvironment.isEmpty()) {
                closeRuntimeResources(oldEnvironment);
            }
        }

        /**
         * 丢弃候选 generation：挂起监听器直接弃置（从未上总线）、候选 global/shared 写集
         * 丢弃（票 10：顶层 set/delete/clear 全部不发布）、候选 Context/timer/streams 按
         * timer → Context → streams 顺序关闭；active 的 runtime、scripts、监听器、timer 与
         * generation 序号原样保留。
         */
        private void discardCandidate (RuntimeEnvironment candidateEnvironment) {
            RuntimeEnvironment candidate = candidateEnvironment != null
                    ? candidateEnvironment : this.candidateEnvironment;
            this.pendingListeners.clear();
            this.candidateContext = null;
            this.candidateEnvironment = null;
            this.candidateKilled = false;
            this.candidateKillScript = null;
            discardCandidateModuleViews(candidate == null ? null : candidate.context());
            if (!runtime.isEmpty() && runtime.moduleSession() != null) {
                activateModuleViews(runtime.context(), runtime.moduleSession());
            }
            if (candidate != null && candidate.globals() != null) {
                candidate.globals().discard();
            }
            if (candidate != null && !candidate.isEmpty()) {
                closeRuntimeResources(candidate);
            }
        }

        /** 候选脚本逐个执行；首个触发候选 kill 的脚本被记录用于失败结果的 source location。 */
        private void loadCandidateScripts (List<ScriptContainer> scriptsToLoad, Context context, NekoNodeRuntime nodeRuntime) {
            if (!prepareScriptsForLoad(scriptsToLoad, context)) {
                return;
            }
            for (ScriptContainer script : scriptsToLoad) {
                // 脚本间取消点（票 07 AC3）：close 已请求时不再启动新的候选脚本工作
                if (lifecycleGate.isCloseRequested() || lifecycleGate.isClosed()) {
                    throw reloadFailure(this.generation + 1, ReloadPhase.EXECUTION,
                            sourceOf(script), "close-preempted",
                            new IllegalStateException(scriptType.name()
                                    + " candidate execution preempted by close before script "
                                    + script.id));
                }
                if (!script.shouldRun()) {
                    continue;
                }
                boolean killedBefore = this.candidateKilled;
                scriptExecutor.executeEntry(context, script, nodeRuntime);
                if (!killedBefore && this.candidateKilled && this.candidateKillScript == null) {
                    this.candidateKillScript = script;
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

        /**
         * 按注册脚本 id（{@code ScriptContextRegistry.currentScriptIdOf} == {@code ScriptContainer.id}
         * 的文本形式）在本次候选批次里定位脚本，用于候选挂起监听器失败结果的 source location。
         * 找不到（脚本已被移除等）时返回 null——失败结果如实留空而不是编造位置。
         */
        private static String sourceOfScriptId (List<ScriptContainer> candidateScripts, String scriptId) {
            if (candidateScripts == null || scriptId == null || scriptId.isBlank()) return null;
            for (ScriptContainer script : candidateScripts) {
                if (scriptId.equals(String.valueOf(script.id))) {
                    return sourceOf(script);
                }
            }
            return null;
        }

        private NekoReloadException reloadFailure (long generation, ReloadPhase phase, String sourceLocation,
                String domain, Throwable cause) {
            return new NekoReloadException(new ReloadFailureReport(
                    scriptType, generation, phase, sourceLocation,
                    "ScriptManager[" + scriptType.name + "]", domain, cause));
        }

        /**
         * 调度门拒绝的结构化失败（票 07 AC1/AC2/AC3 可观察面）：重入/回调内、
         * 关闭中、已关闭各自有明确 domain，不递归、不同步等待自身。
         */
        private NekoReloadException reloadRejected (ScriptLifecycleGate.Decision decision, String action) {
            return reloadFailure(this.generation, ReloadPhase.PREPARATION, null,
                    action + "-rejected:" + decision.name(),
                    new IllegalStateException("ScriptManager[" + scriptType.name + "] " + action
                            + " rejected (" + decision.name() + "): " + rejectReason(decision)));
        }

        private static String rejectReason (ScriptLifecycleGate.Decision decision) {
            return switch (decision) {
                case REJECTED_REENTRANT -> "reentrant lifecycle or managed-callback request; recursion refused";
                case REJECTED_CLOSING -> "close in progress; close is prioritized over not-started work";
                case REJECTED_CLOSED -> "manager is closed";
                default -> "unexpected decision " + decision;
            };
        }

        public synchronized List<ScriptContainer> reloadScriptFile (String filePath) throws IOException {
            // 票 07 调度门：单文件 reload 与整批 reload 同一互斥面（同类型单一序列）。
            ScriptLifecycleGate.Decision decision =
                    lifecycleGate.tryEnter(ScriptLifecycleGate.Operation.RELOAD);
            if (decision != ScriptLifecycleGate.Decision.EXECUTED) {
                throw new IOException("ScriptManager[" + scriptType.name + "] reload file " + filePath
                        + " rejected (" + decision.name() + "): " + rejectReason(decision));
            }
            try {
                return doReloadScriptFile(filePath);
            } finally {
                lifecycleGate.exit(ScriptLifecycleGate.Operation.RELOAD);
            }
        }

        private List<ScriptContainer> doReloadScriptFile (String filePath) throws IOException {
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
                // 已在 RELOAD 门内：直接走门内体，不重过门（嵌套 RELOAD 会被门拒绝）
                doReloadScripts();
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

            currentModuleSession().invalidate(target);
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
            // 按类型清理 root-owned prepared 条目、source maps 和 virtual ESM sources。
            // 局部清除避免一个脚本类型的 reset 误清同 owner 的其他类型产物。
            if (!runtime.isEmpty()) {
                runtime.moduleSession().clear(scriptType);
            }
        }

        private DefaultErrorTracker defaultErrorTracker() {
            return errorTracker instanceof DefaultErrorTracker defaultTracker ? defaultTracker : null;
        }

        private void activateModuleViews(Context context, NekoModulePipelineCache moduleSession) {
            DefaultErrorTracker defaultTracker = defaultErrorTracker();
            if (defaultTracker != null) {
                defaultTracker.activateModuleViews(scriptType, context, moduleSession);
            }
        }

        private void activateCandidateModuleViews(Context context, NekoModulePipelineCache moduleSession) {
            DefaultErrorTracker defaultTracker = defaultErrorTracker();
            if (defaultTracker != null) {
                defaultTracker.activateCandidateModuleViews(scriptType, context, moduleSession);
            }
        }

        private void discardCandidateModuleViews(Context context) {
            DefaultErrorTracker defaultTracker = defaultErrorTracker();
            if (defaultTracker != null) {
                defaultTracker.discardCandidateModuleViews(context);
            }
        }

        private NekoModulePipelineCache currentModuleSession() {
            if (runtime.isEmpty() || runtime.moduleSession() == null) {
                throw new IllegalStateException("ScriptManager[" + scriptType.name + "] has no active module session");
            }
            return runtime.moduleSession();
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
            // TEST 运行走事务式 reload：与其它 lifecycle 同一调度门（TEST owner =
            // test runner / 命令所在平台 owner 线程，票 05/07 owner 入口复用）
            ScriptLifecycleGate.Decision decision =
                    lifecycleGate.tryEnter(ScriptLifecycleGate.Operation.RELOAD);
            if (decision != ScriptLifecycleGate.Decision.EXECUTED) {
                throw reloadRejected(decision, "test-run");
            }
            try {
                doRunTestScripts();
            } finally {
                lifecycleGate.exit(ScriptLifecycleGate.Operation.RELOAD);
            }
        }

        private void doRunTestScripts () {
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
            // 票 10：先失效该 generation 写入的 guest 值（已销毁 Context 的 guest 函数/Value
            // 不因存入 store 获得永久保活；非 guest 值保留跨 reload）。generation close 只做
            // guest 失效，不清空 root 级 store（跨 reload/server stop/切世界保留由 root 负责）。
            if (environment.globals() != null) {
                try {
                    environment.globals().close();
                } catch (Exception e) {
                    com.tkisor.nekojs.script.ScriptTypeEnv.logger(scriptType).warn(
                            "关闭 generation global 视图时发生异常", e);
                }
            }
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
                DefaultErrorTracker defaultTracker = defaultErrorTracker();
                if (defaultTracker != null) {
                    defaultTracker.removeModuleViews(oldContext);
                }
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
            if (environment.moduleSession() != null) {
                environment.moduleSession().closeSession();
            }
        }

        private static void closeStreamQuietly (LoggerStream stream){
            if (stream == null) return;
            try {
                stream.close();
            } catch (Exception ignored) { // 冲刷失败不应中断销毁流程
            }
        }

        // 票 07 close 优先（AC3）：先在拿锁<i>之前</i>设 volatile 关闭标志（尚未开始的
        // 新 lifecycle 随后拿到锁即被门拒绝），再尽力中断在途 candidate 的同步求值
        // （在途 reload 的 owner 线程抛中断后走候选丢弃/失败路径，不提交半成品——
        // close 在实例锁上等它退出的时间由取消点界定），最后拿锁做确定性 teardown：
        // 先取消并关闭候选（未开始的候选工作已被标志挡下），再关闭 active session
        // 与 root 资源。幂等：重复 close 只做标志与空清理，不抛。
        @Override
        public void close () {
            lifecycleGate.requestClose();
            interruptCandidateBestEffort();
            synchronized (this) {
                ScriptLifecycleGate.Decision decision =
                        lifecycleGate.tryEnter(ScriptLifecycleGate.Operation.CLOSE);
                if (decision != ScriptLifecycleGate.Decision.EXECUTED) {
                    // 同线程在 lifecycle 体内嵌套 close：标志已留下（在途操作会在取消点
                    // 失败），这里不内联拆除；调用方可在该操作结束后重新 close。
                    com.tkisor.nekojs.script.ScriptTypeEnv.logger(scriptType).warn(
                            "{} close deferred: another lifecycle operation is running on this thread; "
                                    + "it will fail at its cancellation point", scriptType.name);
                    return;
                }
                try {
                    // 先取消并关闭在途/未开始的候选（若 close 中断晚于候选构建）
                    discardCandidate(this.candidateEnvironment);
                    fullReloadCleanup();
                    for (var binding : pluginRuntime.bindings(scriptType).values()) {
                        binding.close(scriptType);
                    }
                    closeRuntimeResources(this.runtime);
                    this.runtime = RuntimeEnvironment.EMPTY;
                    lifecycleGate.markClosed();
                } finally {
                    lifecycleGate.exit(ScriptLifecycleGate.Operation.CLOSE);
                }
            }
        }

        /**
         * 尽力中断在途 candidate 的同步求值（票 07 close 抢占加速）：中断只让求值
         * 抛错，真正的丢弃与失败归因仍在 owner 线程的 reload 路径完成；中断失败
         * （无在途 candidate、Context 已关闭等）静默忽略，正确性由 commit 前检查与
         * 脚本间取消点兜底。
         */
        private void interruptCandidateBestEffort () {
            Context candidate = this.candidateContext;
            if (candidate == null) {
                return;
            }
            try {
                candidate.interrupt(Duration.ofSeconds(5));
            } catch (Throwable ignored) {
            }
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
