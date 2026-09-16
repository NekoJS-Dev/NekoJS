package com.tkisor.nekojs.core.lifecycle;

/**
 * reload 阶段枚举（工单 06 阶段结果契约）。
 *
 * <p>事务式 reload 依次经过 {@link #PREPARATION} → {@link #BINDING} → {@link #EXECUTION}
 * → {@link #EVENT_PLAN} → {@link #STATE_PLAN} → {@link #COMMIT}；任一阶段失败时失败结果
 * 携带失败发生处。{@link #STARTUP} 与 {@link #FILE} 是非候选路径的显式标记（见工单 06
 * 的 STARTUP restart/unsupported 边界与单文件重载路径）。
 *
 * <p>审查 A2：原先的 {@code UNKNOWN}（"未归类失败"）随 {@code ReloadResult.failure(type, error)}
 * 删除而去掉——该常量此后没有任何生产者，留着就是死码。
 */
public enum ReloadPhase {
    /** 候选 Context / node runtime 创建。 */
    PREPARATION,
    /** 事件组、插件 binding、managed global、binding schema 安装进候选 Context。 */
    BINDING,
    /** 候选脚本发现与入口执行（listener 收集、timer 收集随执行发生）。 */
    EXECUTION,
    /** 候选挂起监听器的完整性/可挂载性预备（dispatch key 转换等 commit 期工作前移）。 */
    EVENT_PLAN,
    /**
     * 候选受管状态的联合预检（工单 10）：global/shared 顶层写集 + 外部候选计划
     * （{@code CandidateStatePlan}）的联合冲突校验；冲突在此失败（domain=
     * {@code global-write-conflict} 等），不丢其他 writer 的已提交写。
     */
    STATE_PLAN,
    /** 单一 commit 点：切换生产路由并释放旧 generation。 */
    COMMIT,
    /** STARTUP 非事务重载（reset+load，显式 restart/unsupported 边界，非候选路径）。 */
    STARTUP,
    /** 单文件目标重载（沿用 active 环境，非候选路径）。 */
    FILE
}
