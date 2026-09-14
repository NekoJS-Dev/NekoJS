package com.tkisor.nekojs.api.ui;

import java.util.List;

/**
 * 已提交 host tree 中的一个节点（fake 与真实 Adapter 共同实现）。
 *
 * <p>脚本与 common 逻辑只通过本 Interface 观察节点：不暴露原生 widget 身份、
 * Minecraft 对象身份或 reconciler 内部结构（jsx-ui-spec User Story 46）。
 */
public interface UiHostNode {

    /** 进程内唯一节点标识（诊断与排序用；不承诺跨 generation 稳定）。 */
    String nodeId();

    /** 节点 primitive。 */
    UiPrimitive primitive();

    /** 稳定节点路径（如 {@code screen/row[0]/button}）。 */
    String path();

    /** 同一父节点下的稳定身份键，可为 {@code null}。 */
    String key();

    /** 最近一次布局结果矩形。 */
    UiRect layout();

    /** 最近一次提交的 props。 */
    UiProps props();

    /** 已提交子节点（按最终顺序）。 */
    List<UiHostNode> children();

    /** 当前绑定的事件类型集合（事件替换的可观察结果）。 */
    java.util.Set<UiEventType> boundEvents();

    /** 是否已释放。 */
    boolean disposed();
}
