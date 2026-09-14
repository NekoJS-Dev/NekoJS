package com.tkisor.nekojs.api.ui;

import java.util.List;
import java.util.Set;

/**
 * host Adapter 公开契约：唯一允许接触原生 widget / 绘制 / 输入的边界
 * （jsx-ui-spec「平台边界」「reconciler」）。
 *
 * <p>common 的 reconciler 只通过本 Interface 创建、更新、排序、绑定事件、布局、绘制与释放
 * host node；Adapter 实现（fake 或 NeoForge 26.2）持有原生对象，common 侧看不到。
 *
 * <p>所有方法都在 root 的 client owner thread 上调用（jsx-ui-spec「状态线程」）。
 * 实现抛出的异常由 reconciler 归入 {@link UiDiagnosticStage#HOST_UPDATE} 阶段，
 * 不提交半成品树。
 */
public interface UiHostAdapter {

    /** Adapter 标识（诊断与 capability 报告用，如 {@code "fake"}）。 */
    String adapterId();

    /** 本 Adapter 支持的 primitive 集合；未列出的 primitive 在提交前显式失败。 */
    Set<UiPrimitive> primitives();

    /** 创建 host node；返回节点必须是未释放状态。 */
    UiHostNode create(UiHostCreate request);

    /** 更新节点属性（不改变子树结构与顺序）。 */
    void update(UiHostNode node, UiProps props);

    /** 以给定顺序重排父节点的子节点（keyed diff 的排序结果）。 */
    void reorder(UiHostNode parent, List<UiHostNode> children);

    /** 替换节点的事件绑定集合：旧闭包必须在返回后不再被调用。 */
    void bindEvents(UiHostNode node, Set<UiEventType> events);

    /** 释放节点及其整棵子树；必须幂等。 */
    void dispose(UiHostNode node);

    /** 把节点放到最终矩形。 */
    void arrange(UiHostNode node, UiRect rect);

    /** 绘制节点（裁剪区域为最终矩形与祖先裁剪的交集）。 */
    void paint(UiHostNode node, UiRect clip);
}
