package com.tkisor.nekojs.api.ui;

/**
 * 函数组件复用边界：把脚本侧组件标识解析为渲染器，再展开为候选 VNode。
 *
 * <p>组件返回 VNode、fragment 或空值；返回其它值显式失败
 * （{@link UiFailure#INVALID_COMPONENT_RESULT}）。不实现 class component、hook 调用顺序
 * 协议或 React effect 兼容层（jsx-ui-spec「组件模型」）。
 */
@FunctionalInterface
public interface UiComponentRegistry {

    /** 解析组件标识；未登记返回 {@code null}（reconciler 随后显式失败）。 */
    UiRenderer resolve(String component);

    /** 组件渲染器。 */
    @FunctionalInterface
    interface UiRenderer {
        /** 以 props 与子节点展开组件；返回 VNode、fragment 或 {@code null}。 */
        UiVNode render(UiProps props, java.util.List<UiVNode> children);
    }

    /** 空注册表：任何组件标识都解析不到。 */
    UiComponentRegistry EMPTY = component -> null;
}
