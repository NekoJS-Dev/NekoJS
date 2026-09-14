package com.tkisor.nekojs.api.ui;

/**
 * 不可变脚本侧 VNode（jsx-ui-spec「VNode 语义」）。
 *
 * <p>三种形态：host primitive 节点（{@link HostVNode}）、函数组件节点
 * （{@link ComponentVNode}）与 Fragment（{@link FragmentVNode}）。三者都是 Java record，
 * 只持有字符串、{@link UiProps} 与子 VNode 列表——<b>不持有</b> Minecraft、loader、
 * {@code GuiGraphics}、Font、Screen、GL 或任何长期可变 host 对象（AC1）。
 *
 * <p>{@code key} 语义：同一父节点下同一 {@code type} 的 key 必须唯一，否则显式失败
 * （重复 key 不得静默复用 host node）。
 */
public sealed interface UiVNode permits HostVNode, ComponentVNode, FragmentVNode {

    /** 同一父节点下的稳定身份键；{@code null} 表示按位置匹配。 */
    String key();

    /** 直接子节点（不可变列表）。 */
    java.util.List<UiVNode> children();
}
