package com.tkisor.nekojs.api.ui;

import java.util.List;

/**
 * Fragment 节点：只做子节点展开，自身不产生 host node。
 *
 * @param key      同一父节点下的稳定身份键，可为 {@code null}
 * @param children 不可变子 VNode 列表
 */
public record FragmentVNode(String key, List<UiVNode> children) implements UiVNode {

    public FragmentVNode {
        children = List.copyOf(children == null ? List.of() : children);
    }
}
