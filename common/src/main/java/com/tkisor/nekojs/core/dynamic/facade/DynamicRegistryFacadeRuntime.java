package com.tkisor.nekojs.core.dynamic.facade;

import com.tkisor.nekojs.NekoJS;
import com.tkisor.nekojs.api.ScriptType;
import com.tkisor.nekojs.core.dynamic.plan.DynamicCandidateRegistryPlan;
import com.tkisor.nekojs.core.dynamic.plan.DynamicRegistryPlanStore;
import com.tkisor.nekojs.core.dynamic.txn.DynamicRegistryAdapter;
import com.tkisor.nekojs.core.dynamic.txn.DynamicRegistryTransactionCoordinator;
import com.tkisor.nekojs.core.dynamic.txn.DynamicSyncMessage;
import com.tkisor.nekojs.core.dynamic.txn.DynamicSyncTransport;
import com.tkisor.nekojs.core.state.CandidateStatePlan;
import com.tkisor.nekojs.core.state.GlobalStateException;
import com.tkisor.nekojs.core.lifecycle.CandidateDomainCollector;
import com.tkisor.nekojs.script.ScriptContextRegistry;
import com.tkisor.nekojs.script.ScriptManager;
import graal.graalvm.polyglot.Context;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.function.LongSupplier;

/**
 * 动态注册 facade 的 Runtime（ticket 16）：持有本域的 {@link DynamicRegistryPlanStore}，
 * 承担两条收集入口——
 * <ul>
 *   <li><b>初次候选</b>（AC1 前半）：server registry ready 时由平台触发
 *       {@link #collectInitial(String)}——向 active 总线投递收集事件，成功 preflight
 *       才发布批次（失败只记录，不另起写入）；</li>
 *   <li><b>reload 候选收集</b>（AC1 后半）：实现 {@link CandidateDomainCollector}，
 *       由 reload 管线在候选期调用（EVENT_PLAN 后、STATE_PLAN 前），把候选监听器的
 *       声明收成 inert 计划挂入联合边界——commit 点联合发布，候选失败随整体丢弃。</li>
 * </ul>
 *
 * <p><b>本票只证明本地 inert 计划行为</b>（AC9）：发布的计划只更新 claim/stale/exposed
 * 账并产出 inert {@code DynamicAdapterRequest}——不激活多人同步、不执行 registry
 * mutation、不宣称动态热更新；公开激活由事务/同步 gate（票 21）裁决。生产实例由平台
 * Adapter 侧持有（NeoForge dynamic 包），测试/harness 自由构造隔离实例。
 *
 * <p><b>inert 的结构面（AC7）</b>：本类与整个 {@code com.tkisor.nekojs.core.dynamic.plan}
 * 包都住在 common——零 Minecraft/loader import（由 {@code :common:checkCommonIsolation}
 * 与 guardLint L1/L2 守护），因此「候选期修改 live registry」在本模块内不可表达：
 * 计划只持有不可变定义快照与 inert {@code DynamicAdapterRequest} 描述，没有 platform
 * 执行通道、不挂生产 callback、不提前发布对外 binding；候选失败时计划对象不再可达
 * （无临时资源需要释放），账本零写入，旧 active 定义继续服务。计划包的结构性验证见
 * {@code DynamicPlanInertnessTest}。
 *
 * <p><b>ticket 21 activation seam</b>: {@link #bindActivationEngine} optionally attaches
 * a batch-transaction engine; {@link #pumpActivation()} then hands every ledger-committed
 * batch to it (pull-based pending queue — the plan itself carries no hook). Unbound, this
 * runtime is exactly the ticket 16 inert-local-plan surface; production binding is not
 * wired yet (activation stays gated by the transaction/sync gates, see the ticket 21
 * baseline REPORT).
 */
public final class DynamicRegistryFacadeRuntime implements CandidateDomainCollector {

    /** 一轮收集的可观察结果（测试/诊断；不携带内部状态）。 */
    public record CollectionOutcome(
            String trigger,
            long generation,
            boolean published,
            String failureDomain,
            String failureDetail) {

        static CollectionOutcome success(String trigger, DynamicCandidateRegistryPlan plan) {
            return new CollectionOutcome(trigger, plan.generation(), true, null, null);
        }

        static CollectionOutcome failure(String trigger, DynamicCandidateRegistryPlan plan,
                GlobalStateException error) {
            return new CollectionOutcome(trigger, plan.generation(), false, error.domain(), error.getMessage());
        }
    }

    /**
     * 一轮<b>候选期</b>收集的可观察记录（AC1「script/data reload 在候选阶段重新收集」的
     * 直接观察点；与初次收集的 {@link CollectionOutcome} 分开——候选期只收集并挂入联合
     * 边界，发布与否由 commit 点决定）。同样不携带内部状态、不持有计划引用：
     * 失败候选的计划在此之后不可达（失败清理＝无临时资源残留）。
     */
    public record CandidateCollectionRecord(
            String note,
            long planGeneration,
            int collectedDefinitions,
            boolean poisoned,
            String failureDetail) {

        /** 本域未参与该候选（非 SERVER 类型 / 未使用且账本为空）。 */
        static CandidateCollectionRecord skipped(String note) {
            return new CandidateCollectionRecord(note, -1L, 0, false, null);
        }

        static CandidateCollectionRecord collected(DynamicCandidateRegistryPlan plan) {
            return new CandidateCollectionRecord("collected", plan.generation(), plan.definitions().size(),
                    plan.hasCollectionErrors(),
                    plan.collectionErrors().isEmpty() ? null : String.join("; ", plan.collectionErrors()));
        }

        /** 本域是否在该候选期实际收集并挂入计划。 */
        public boolean participated() {
            return planGeneration >= 0L;
        }
    }

    private final DynamicRegistryPlanStore store = new DynamicRegistryPlanStore();
    private volatile CollectionOutcome lastOutcome;
    private volatile CandidateCollectionRecord lastCandidateCollection;
    private volatile DynamicRegistryTransactionCoordinator activationEngine;
    /**
     * Plans begun by this runtime, waiting for their ledger commit to be observed by
     * {@link #pumpActivation()} (pull-based staging: the plan itself carries no hook —
     * the ticket 16 plan surface stays pure inert data with no execution channel).
     * A failed/discarded candidate is dropped at the next pump; a plan is pure data
     * and holds no resources, so the brief reference is not a cleanup concern.
     */
    private final ArrayDeque<DynamicCandidateRegistryPlan> pendingCandidates = new ArrayDeque<>();
    /** Newest plan generation ever handed to the activation engine. */
    private long lastStagedPlanGeneration;

    /** 本域计划账本（Registry Runtime 侧观察点）。 */
    public DynamicRegistryPlanStore store() {
        return store;
    }

    /**
     * Binds the activation engine (ticket 21): with an engine bound, every committed
     * declaration batch is also staged for the batch transaction
     * (preflight → server prepare → client prepare/ack → controlled commit through
     * the Adapter/Transport seams). Without one the runtime stays exactly the
     * ticket 16 inert-local-plan surface. Production binding happens in the platform
     * assembly (currently not wired — activation stays gated); tests bind fakes.
     */
    public synchronized void bindActivationEngine(
            DynamicRegistryAdapter adapter, DynamicSyncTransport transport,
            long ackTimeoutMillis, LongSupplier clock) {
        this.activationEngine = new DynamicRegistryTransactionCoordinator(
                adapter, transport, ackTimeoutMillis, clock);
    }

    /** Drops the activation engine (server stopped); aborts and discards in-flight work first. */
    public synchronized void clearActivationEngine(String cause) {
        if (activationEngine != null) {
            activationEngine.abortInFlight(cause);
        }
        this.activationEngine = null;
    }

    /** Bound activation engine, or null while unbound (inert local plans only). */
    public DynamicRegistryTransactionCoordinator activationEngine() {
        return activationEngine;
    }

    /**
     * Owner-thread activation pump (ticket 21): hands every ledger-committed batch to
     * the bound engine and starts the next transaction when none is in flight.
     * Pull-based by design — {@link #collectInitial} calls it after its own commit,
     * and the production wiring (server tick / post-reload event, owner: network
     * phase) calls it for reload-driven commits. Never runs inside a reload's joint
     * commit boundary: staging is an enqueue, phases are event-driven.
     */
    public synchronized void pumpActivation() {
        DynamicRegistryTransactionCoordinator engine = activationEngine;
        if (engine == null) {
            return;
        }
        while (!pendingCandidates.isEmpty()) {
            DynamicCandidateRegistryPlan candidate = pendingCandidates.poll();
            if (!candidate.isPublished()) {
                continue; // failed/discarded candidate: its declarations never committed
            }
            if (candidate.generation() <= lastStagedPlanGeneration) {
                continue; // already staged (or superseded by a newer staged batch)
            }
            if (!candidate.adapterRequests().isEmpty()) {
                engine.stage(candidate, targetState());
            }
            lastStagedPlanGeneration = candidate.generation();
        }
        engine.pump();
    }

    /** Full target state of the ledger as wire entries (full state, never a delta). */
    private List<DynamicSyncMessage.Entry> targetState() {
        List<DynamicSyncMessage.Entry> entries = new ArrayList<>();
        store.exposedSnapshot().values()
                .forEach(entry -> entries.add(
                        new DynamicSyncMessage.Entry(entry.definition(), entry.ownerScriptId())));
        return entries;
    }

    /** 最近一轮收集的可观察结果（诊断/测试；不携带内部状态）。 */
    public CollectionOutcome lastOutcome() {
        return lastOutcome;
    }

    /** 最近一轮<b>候选期</b>收集的可观察记录（诊断/测试；无候选期收集时为 null）。 */
    public CandidateCollectionRecord lastCandidateCollection() {
        return lastCandidateCollection;
    }

    // ---- 初次候选（server registry ready 触发） ----

    /**
     * 触发一次收集（active 总线投递）：监听器异常由总线捕获记录，收集失败经 payload
     * 的毒化标记进入 preflight 拒绝；preflight 通过才 publish。失败只返回结果并记日志，
     * <b>不另起写入</b>——旧 active 定义（若有）继续服务。
     */
    public CollectionOutcome collectInitial(String trigger) {
        DynamicCandidateRegistryPlan plan = store.beginBatch();
        pendingCandidates.add(plan);
        DynamicRegistryEventJS payload = new DynamicRegistryEventJS(plan);
        DynamicRegistryEvents.DYNAMIC_REGISTRY.post(payload);
        try {
            plan.preflight();
        } catch (GlobalStateException e) {
            NekoJS.LOGGER.error(
                    "DynamicRegistry candidate batch {} (trigger '{}') rejected: [{}] {} — nothing published,"
                            + " previous active definitions (if any) keep serving",
                    plan.generation(), trigger, e.domain(), e.getMessage());
            lastOutcome = CollectionOutcome.failure(trigger, plan, e);
            return lastOutcome;
        }
        plan.publish();
        NekoJS.LOGGER.info(
                "DynamicRegistry inert candidate batch {} committed (trigger '{}', {} definition(s));"
                        + " inert local plan only — activation is gated by the transaction/sync gate",
                plan.generation(), trigger, plan.definitions().size());
        lastOutcome = CollectionOutcome.success(trigger, plan);
        // Initial collection runs on the owner thread outside any reload commit lock,
        // so the activation transaction may start immediately (zero participants at
        // server start in production; reload-driven staging is pumped by the platform).
        pumpActivation();
        return lastOutcome;
    }

    // ---- reload 候选收集（CandidateDomainCollector） ----

    @Override
    public String domain() {
        return DynamicCandidateRegistryPlan.DOMAIN;
    }

    /** SERVER-only 域：收集器只参与 SERVER 候选（CLIENT/STARTUP/TEST 不触碰账本）。 */
    @Override
    public ScriptType scriptType() {
        return ScriptType.SERVER;
    }

    /**
     * 候选期收集（SERVER 域限定）：对候选挂起监听器中属于
     * {@link DynamicRegistryEvents#DYNAMIC_REGISTRY} 的回调逐个执行（同 owner thread，
     * 经 {@link Handle#execute} 的 scriptId 切换/回调深度标记/异常上抛——与生产分发同款）；
     * 单监听器异常记为 collection error（毒化整批）后继续其余监听器（EventBus 语义），
     * 整批在 STATE_PLAN 以精确 domain 拒绝。Error 按分发同款语义直抛（由 reload 管线按
     * 收集器崩溃以 {@code domain-collect:<domain>} 归因），中断标志恢复。
     *
     * <p>空候选：候选没有任何本域监听器时，仅当账本已有 exposed 定义才挂入空计划
     * （把不再声明的项标记 stale/retired）；账本为空时跳过——未使用本域的 reload 零参与。
     */
    @Override
    public void collect(Handle handle) {
        if (handle.scriptType() != ScriptType.SERVER) {
            lastCandidateCollection = CandidateCollectionRecord.skipped("skipped-non-server");
            return; // SERVER-only 域：绝不因其它类型 reload 触碰账本
        }
        List<com.tkisor.nekojs.api.event.EventBusJS.PendingListener> ofBus =
                handle.listenersOf(DynamicRegistryEvents.DYNAMIC_REGISTRY);
        if (ofBus.isEmpty() && store.isEmpty()) {
            lastCandidateCollection = CandidateCollectionRecord.skipped("skipped-unused-domain");
            return; // 本域从未被使用且候选未声明：不挂空计划
        }
        DynamicCandidateRegistryPlan plan = store.beginBatch();
        pendingCandidates.add(plan);
        DynamicRegistryEventJS payload = new DynamicRegistryEventJS(plan);
        for (com.tkisor.nekojs.api.event.EventBusJS.PendingListener pending : ofBus) {
            try {
                handle.execute(pending, payload);
            } catch (Throwable e) {
                // catch 形态与生产分发闭包同款（EventBusJS.register* 的 catch(Throwable)）：
                // 中断标志恢复、Error 直抛给上层（reload 管线按收集器崩溃归因）、其余
                // （guest PolyglotException 与领域数据错误）记为收集错误毒化整批后继续其余
                // 监听器——一个坏声明不掩盖其余收集错误。kill 上报同生产路径（候选 Context
                // 被资源上限终止时按候选失败记账，而不是当普通收集错误吞掉）。
                if (e instanceof InterruptedException) {
                    Thread.currentThread().interrupt();
                }
                if (e instanceof Error error) {
                    throw error;
                }
                ScriptManager.reportContextKilled(handle.candidateContext(), e);
                plan.noteCollectionError(pending.scriptId(), e);
                NekoJS.LOGGER.error("DynamicRegistry candidate collection failed in script '{}': {}",
                        pending.scriptId(), e.getMessage());
            }
        }
        lastCandidateCollection = CandidateCollectionRecord.collected(plan);
        handle.registerPlan(plan);
    }
}
