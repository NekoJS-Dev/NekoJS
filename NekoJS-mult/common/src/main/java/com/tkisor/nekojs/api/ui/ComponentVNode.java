package com.tkisor.nekojs.api.ui;

import java.util.List;
import java.util.Objects;

/**
 * 函数组件节点：大写 JSX 标签解析出的普通 JS/TS 函数组件。
 *
 * <p>组件必须返回 VNode、fragment 或空值（{@code null}/{@code undefined}）。
 * 不实现 class component、hook 调用顺序协议或 React effect 兼容层
 * （jsx-ui-spec「组件模型」）。
 *
 * @param component 脚本侧组件标识（自动 runtime 产出的组件名，用于诊断与注册表查找）
 * @param key       同一父节点下的稳定身份键，可为 {@code null}
 * @param props     不可变 props 包
 * @param children  不可变子 VNode 列表（作为 props.children 传给组件）
 */
public record ComponentVNode(String component, String key, UiProps props, List<UiVNode> children)
        implements UiVNode {

    public ComponentVNode {
        Objects.requireNonNull(component, "component");
        if (component.isBlank()) {
            throw new IllegalArgumentException("component name must not be blank");
        }
        props = props == null ? UiProps.empty() : props;
        children = List.copyOf(children == null ? List.of() : children);
    }
}
