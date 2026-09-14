package com.tkisor.nekojs.api.ui;

import java.util.List;

/**
 * 脚本侧 JSX 值与 VNode 之间的转换边界（AC1 的公开 contract fixture 入口）。
 *
 * <p>automatic runtime 的 jsx / jsxs / Fragment 产出脚本侧元素；本 Interface 把它们
 * （含函数组件、条件、数组、spread、key 与 children）转换为候选 {@link UiVNode} 列表。
 * 默认实现 com.tkisor.nekojs.core.ui.ScriptUiVNodeFactory 直接消费 Graal 值，
 * 因此 api 包保持零 Graal import（guardLint L1）。
 *
 * <p>转换失败（未知标签、非法 props、重复 key、组件返回非法值）必须显式抛出
 * {@link UiFailure}，不静默丢弃节点。
 */
public interface UiVNodeFactory {

    /**
     * 把一个脚本侧值转换为候选 VNode 列表。
     *
     * @param scriptValue 脚本侧值：JSX 元素 / Fragment / 数组 / 可迭代 / null
     * @param contextPath 转换上下文路径（诊断用）
     * @return 候选 VNode 列表（null 与空值产生空列表）
     */
    List<UiVNode> toVNodes(Object scriptValue, String contextPath);
}
