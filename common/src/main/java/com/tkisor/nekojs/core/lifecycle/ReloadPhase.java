package com.tkisor.nekojs.core.lifecycle;

/**
 * reload 阶段枚举（工单 06 阶段结果契约）。
 *
 * <p>事务式 reload 依次经过 {@link #PREPARATION} → {@link #BINDING} → {@link #EXECUTION}
 * （含事件计划收集）→ {@link #COMMIT}；任一阶段失败时失败结果携带失败发生处。
 * {@link #STARTUP} 与 {@link #FILE} 是非候选路径的显式标记（见工单 06 的 STARTUP
 * restart/unsupported 边界与单文件重载路径）。
 */
public enum ReloadPhase {
    /** 候选 Context / node runtime 创建。 */
    PREPARATION,
    /** 事件组、插件 binding、managed global、binding schema 安装进候选 Context。 */
    BINDING,
    /** 候选脚本发现与入口执行（listener 收集、timer 收集随执行发生）。 */
    EXECUTION,
    /** 事件计划校验（候选挂起监听器完整性）。 */
    EVENT_PLAN,
    /** 单一 commit 点：切换生产路由并释放旧 generation。 */
    COMMIT,
    /** STARTUP 非事务重载（reset+load，显式 restart/unsupported 边界，非候选路径）。 */
    STARTUP,
    /** 单文件目标重载（沿用 active 环境，非候选路径）。 */
    FILE,
    /** 未归类失败（root 入口捕获的意外异常）。 */
    UNKNOWN
}
