package com.tkisor.nekojs.api.ui;

import java.util.List;
import java.util.Objects;

/**
 * host primitive 节点：{@link UiPrimitive} 的受控标签 + props + 子节点。
 *
 * @param primitive host primitive（唯一登记表成员）
 * @param key       同一父节点下的稳定身份键，可为 {@code null}
 * @param props     不可变 props 包
 * @param children  不可变子 VNode 列表
 */
public record HostVNode(UiPrimitive primitive, String key, UiProps props, List<UiVNode> children)
        implements UiVNode {

    public HostVNode {
        Objects.requireNonNull(primitive, "primitive");
        props = props == null ? UiProps.empty() : props;
        children = List.copyOf(children == null ? List.of() : children);
    }

    /** 小写 JSX 标签（host capability 名）。 */
    public String tag() {
        return primitive.tag();
    }
}
