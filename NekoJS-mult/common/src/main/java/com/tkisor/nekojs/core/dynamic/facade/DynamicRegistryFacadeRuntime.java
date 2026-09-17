package com.tkisor.nekojs.core.dynamic.facade;

import com.tkisor.nekojs.NekoJS;
import com.tkisor.nekojs.api.ScriptType;
import com.tkisor.nekojs.core.dynamic.plan.DynamicCandidateRegistryPlan;
import com.tkisor.nekojs.core.dynamic.plan.DynamicRegistryPlanStore;
import com.tkisor.nekojs.core.state.CandidateStatePlan;
import com.tkisor.nekojs.core.state.GlobalStateException;
import com.tkisor.nekojs.script.CandidateDomainCollector;
import com.tkisor.nekojs.script.ScriptContextRegistry;
import com.tkisor.nekojs.script.ScriptManager;
import graal.graalvm.polyglot.Context;

import java.util.function.Consumer;
import java.util.List;

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

    /** 本域计划账本（Registry Runtime 侧观察点）。 */
    public DynamicRegistryPlanStore store() {
        return store;
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
        return lastOutcome;
    }

    // ---- reload 候选收集（CandidateDomainCollector） ----

    @Override
    public String domain() {
        return DynamicCandidateRegistryPlan.DOMAIN;
    }

    /**
     * 候选期收集（SERVER 域限定）：对候选挂起监听器中属于
     * {@link DynamicRegistryEvents#DYNAMIC_REGISTRY} 的回调逐个执行（同 owner thread，
     * 切换 currentScriptId、标记回调深度、catch/上报形态——都与生产分发闭包同款）；单监听器
     * 异常记为 collection error（毒化整批）后继续其余监听器（EventBus 语义），整批在
     * STATE_PLAN 以精确 domain 拒绝。Error 按分发同款语义直抛（由 reload 管线按收集器
     * 崩溃归因），中断标志恢复。
     *
     * <p>空候选：候选没有任何本域监听器时，仅当账本已有 exposed 定义才挂入空计划
     * （把不再声明的项标记 stale/retired）；账本为空时跳过——未使用本域的 reload 零参与。
     */
    @Override
    public void collectForCandidate(
            Context candidateContext,
            List<com.tkisor.nekojs.api.event.EventBusJS.PendingListener> candidatePending,
            Consumer<CandidateStatePlan> attachPlan) {
        if (ScriptContextRegistry.scriptTypeOf(candidateContext) != ScriptType.SERVER) {
            lastCandidateCollection = CandidateCollectionRecord.skipped("skipped-non-server");
            return; // SERVER-only 域：CLIENT/STARTUP/TEST 候选不参与，绝不因其它类型 reload 触碰账本
        }
        boolean anyDomainListeners = false;
        for (com.tkisor.nekojs.api.event.EventBusJS.PendingListener pending : candidatePending) {
            if (pending.ownerBus() == DynamicRegistryEvents.DYNAMIC_REGISTRY) {
                anyDomainListeners = true;
                break;
            }
        }
        if (!anyDomainListeners && store.isEmpty()) {
            lastCandidateCollection = CandidateCollectionRecord.skipped("skipped-unused-domain");
            return; // 本域从未被使用且候选未声明：不挂空计划
        }
        DynamicCandidateRegistryPlan plan = store.beginBatch();
        DynamicRegistryEventJS payload = new DynamicRegistryEventJS(plan);
        for (com.tkisor.nekojs.api.event.EventBusJS.PendingListener pending : candidatePending) {
            if (pending.ownerBus() != DynamicRegistryEvents.DYNAMIC_REGISTRY) {
                continue;
            }
            String previousScriptId =
                    ScriptContextRegistry.switchCurrentScriptId(candidateContext, pending.scriptId());
            ScriptManager.noteCallbackEnter();
            try {
                pending.listenerValue().execute(payload);
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
                ScriptManager.reportContextKilled(candidateContext, e);
                plan.noteCollectionError(pending.scriptId(), e);
                NekoJS.LOGGER.error("DynamicRegistry candidate collection failed in script '{}': {}",
                        pending.scriptId(), e.getMessage());
            } finally {
                ScriptManager.noteCallbackExit();
                ScriptContextRegistry.restoreCurrentScriptId(candidateContext, previousScriptId);
            }
        }
        lastCandidateCollection = CandidateCollectionRecord.collected(plan);
        attachPlan.accept(plan);
    }
}
