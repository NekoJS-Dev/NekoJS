package com.tkisor.nekojs.api.ui;

import java.util.List;

/**
 * 客户端 UI facade（jsx-ui-spec「公开模型」）：root 的创建/打开、关闭、状态失效、
 * 响应式原语创建和生命周期查询的唯一脚本可见入口。
 *
 * <p>不把原生 {@code Screen} 作为脚本作者需要继承的基类；root 绑定创建它的 CLIENT
 * generation，并由唯一的 {@code NekoRuntimeRoot} 负责其生命周期
 * （jsx-ui-spec「Runtime owner」）。本 Interface 的默认实现是
 * {@code com.tkisor.nekojs.core.ui.RootUiFacade}，由 CLIENT generation 的装配点构造；
 * 没有客户端装配时使用显式拒绝实现，任何创建调用抛
 * {@link UiFailure#UNSUPPORTED_CAPABILITY}（不是静默 no-op）。
 *
 * <p>线程约束：facade 与它创建的 root 共享同一 client owner thread；非 owner thread
 * 的写入/打开/关闭显式失败（{@link UiFailure#OFF_OWNER_THREAD}），不依赖 GraalJS
 * Context 的隐式跨线程能力。
 */
public interface UiFacade {

    /** 当前 generation。 */
    int generation();

    /** 创建响应式原语 owner（signal/store），与本 facade 绑定同一 owner thread。 */
    UiStateOwner createStateOwner();

    /** 创建 root（尚未打开）。 */
    UiRootHandle create(UiScreen screen);

    /** 创建并打开 root（首次渲染与原子提交一起完成）。 */
    UiRootHandle open(UiScreen screen);

    /** 使某 generation 的全部 root 失效（CLIENT reload 的候选切换点）。 */
    void invalidateGeneration(int generation);

    /** 释放某 generation 的全部 root（幂等）。 */
    void closeGeneration(int generation);

    /** 当前仍存活的 root 句柄（诊断与测试用）。 */
    List<UiRootHandle> roots();

    /** 释放 facade：幂等释放全部 root。 */
    void dispose();
}
