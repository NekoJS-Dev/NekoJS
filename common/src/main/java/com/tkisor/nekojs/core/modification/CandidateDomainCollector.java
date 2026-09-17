package com.tkisor.nekojs.core.modification;

import com.tkisor.nekojs.api.ScriptType;
import com.tkisor.nekojs.api.event.EventBusJS;
import com.tkisor.nekojs.core.state.CandidateStatePlan;
import graal.graalvm.polyglot.Context;

/**
 * 候选域收集器（票 39）：由 {@code NekoRuntimeRoot} 持有的领域收集挂载点——reload 事务
 * 在 EVENT_PLAN 之后、STATE_PLAN 之前对构建中的候选逐收集器调用一次
 * （{@code ReloadPhase.DOMAIN_PLAN}），收集器把领域收集事件派发进<b>候选的挂起监听器</b>，
 * 产出 inert 领域计划并经 {@link Handle#registerPlan(CandidateStatePlan)} 挂入同一候选的
 * 联合预检/联合发布边界（票 10 {@code CandidateStatePlan}）。
 *
 * <p>本接口在 common 不含领域语义：Item/Block modification 的收集器实现住在版本树
 * 共享 MC-facing 层（及其 1.21.1 成对文件），由平台装配（loader entry）注册进 root。
 * 不新增第二 runtime owner、第二事件 bus 或公开 Modification Runtime——收集器只是
 * root 拥有的一个 reload 阶段挂载点。
 *
 * <p>契约：
 * <ul>
 *   <li>{@link #collect(Handle)} 在 owner thread（reload 持实例锁）同步调用；抛出即
 *       候选失败（domain = {@code "domain-collect:" + domain()}），保留旧 active；</li>
 *   <li>监听器回调内的错误语义由收集器决定：领域要求整批失败的，把错误记进计划
 *       （preflight 必失败）或直接抛出；</li>
 *   <li>无监听器时也应注册计划（空计划）——声明移除的「恢复基线」语义依赖空计划参与
 *       commit（AC7）。</li>
 * </ul>
 */
public interface CandidateDomainCollector {

    /** 收集器标识（进入失败结果的 domain 消歧，如 {@code "item-block-modification"}）。 */
    String domain();

    /** 参与的 ScriptType（收集器只在该类型的 reload 中被调用，如 SERVER）。 */
    ScriptType scriptType();

    /** 候选收集：把收集事件派发进候选挂起监听器并注册领域计划；抛出即候选失败。 */
    void collect(Handle handle);

    /**
     * 收集期把手：候选 Context + 面向候选挂起监听器的收集派发原语 + 计划注册。
     * 生命周期只在 {@link #collect(Handle)} 调用内有效。
     */
    interface Handle {

        /** 正在构建的候选 Context（联合计划注册的来源判定）。 */
        Context candidateContext();

        ScriptType scriptType();

        /**
         * 收集派发：把事件按总线分发序（priority 高优先、同优先级保持注册序——与
         * {@code EventBusBase} 的编译快照同序）派发给<b>本候选</b>在该总线上收集的
         * 挂起监听器（commit 前不上生产总线）。监听器回调抛出的异常<b>向上传播</b>
         * （候选失败）——与生产分发「错误记录不失败」不同，这是候选收集语义；
         * 收集器要求整批失败时也可以自行捕获并记进计划。
         *
         * <p><b>仅支持非 dispatch 总线</b>：按 key 定向分发的总线（
         * {@code EventBusJS#canDispatch()}）需要 key 才能判定投递子集，收集派发没有
         * key 上下文——对这类总线的调用被显式拒绝（{@code UnsupportedOperationException}），
         * 不做「忽略 key 全量派发」的静默降级。
         */
        void dispatch(EventBusJS<?, ?> bus, Object event);

        /** 领域计划挂入候选联合边界（等价 {@code ScriptManager.registerCandidatePlan}）。 */
        void registerPlan(CandidateStatePlan plan);
    }
}
