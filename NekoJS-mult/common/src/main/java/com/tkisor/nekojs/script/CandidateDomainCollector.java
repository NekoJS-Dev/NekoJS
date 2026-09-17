package com.tkisor.nekojs.script;

import com.tkisor.nekojs.api.event.EventBusJS;
import com.tkisor.nekojs.core.state.CandidateStatePlan;
import graal.graalvm.polyglot.Context;

import java.util.List;
import java.util.function.Consumer;

/**
 * 候选期领域事件收集器（ticket 16 引入的 reload 管线 seam，票 10
 * {@link CandidateStatePlan} 联合边界的收集侧配套）。
 *
 * <p>用途：事件化域（W6/W7——动态注册是第一个）需要在自己的收集事件里把候选监听器
 * 的声明收成 <b>inert 候选计划</b>。reload 管线在 EVENT_PLAN 之后、STATE_PLAN 之前
 * 调用已注册的收集器：收集器筛出属于自己总线的候选挂起监听器（{@code PendingListener}
 * ——候选期未上生产总线）、在候选 Context 里执行它们（同 owner thread），把产出的
 * 计划经 {@code attachPlan} 挂入该候选的联合边界（STATE_PLAN 统一预检、commit 点
 * 联合发布、候选失败随整体丢弃）。
 *
 * <p>契约：
 * <ul>
 *   <li>收集器<b>不得</b>修改 live 外部对象、挂生产 callback 或提前发布对外 binding
 *       （spec 09：candidate 只生成计划）；</li>
 *   <li>收集器抛出＝候选失败（reload 以 {@code ReloadPhase.STATE_PLAN} +
 *       {@code domain-plan-collection:<domain>} 归因，走 discardCandidate 路径）；
 *       领域内的「数据级」失败（非法定义、冲突）应记录进<b>自己的计划</b>并照常挂入
 *       （计划 preflight 会以精确 domain 拒绝整批），而不是在这里抛——这样失败归因
 *       落在领域 domain 而不是收集器崩坏；</li>
 *   <li>与 {@code ScriptManager} 同一 owner thread 调用；注册时机是 bootstrap（生产由
 *       领域插件一次性注册），不随 reload 重复注册。</li>
 * </ul>
 */
public interface CandidateDomainCollector {

    /** 领域标识（收集器自身失败时的归因后缀）。 */
    String domain();

    /**
     * 在候选期收集一次本领域的计划。
     *
     * @param candidateContext 候选 Graal Context（执行候选监听器回调用；同 owner thread）
     * @param candidatePending 本候选收集到的全部挂起监听器（收集器按所属总线自行筛选）
     * @param attachPlan 计划挂入点（转发到候选 {@code GenerationGlobals.addPlan}）
     */
    void collectForCandidate(
            Context candidateContext,
            List<EventBusJS.PendingListener> candidatePending,
            Consumer<CandidateStatePlan> attachPlan);
}
