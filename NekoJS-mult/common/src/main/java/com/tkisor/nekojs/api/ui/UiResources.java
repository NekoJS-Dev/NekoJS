package com.tkisor.nekojs.api.ui;

/**
 * UI root 的宿主资源装配（jsx-ui-spec「平台边界」「Runtime owner」）。
 *
 * <p>由 CLIENT generation 的装配点传入：host Adapter、文本测量、组件注册表、诊断 seam、
 * owner thread 与 client 调度边界。本包只引用这些窄 Interface——host Adapter 与测量实现
 * 的实例由平台侧构造，因此 UI 核心不依赖 Minecraft/loader（AC7）。
 *
 * <p>{@code invalidator} 是"把一次失效请求送进 client 调度边界"的唯一入口：
 * 候选 generation 在 commit 前不得对生产 Screen 可见（jsx-ui-spec「reload 事务」）。
 */
public interface UiResources {

    /** 平台/版本 host Adapter（fake 或真实实现）。 */
    UiHostAdapter adapter();

    /** 文本测量边界。 */
    UiTextMeasurer textMeasurer();

    /** 函数组件注册表。 */
    UiComponentRegistry components();

    /** UI root 绑定的 CLIENT generation。 */
    int generation();

    /** 诊断汇。 */
    UiDiagnosticSink diagnostics();

    /** root 的 client owner thread。 */
    Thread ownerThread();

    /**
     * 把一次失效请求送进 client 调度边界。
     *
     * <p>返回 null 表示「本环境没有异步调度」：此时失效只在 owner thread 直通处理；
     * 非 owner thread 的写入不会被静默丢弃，而是显式失败
     * （{@link UiFailure#OFF_OWNER_THREAD}）。
     */
    Invalidator invalidator();

    /**
     * 事件分发入口：root 命中节点后经此调用已绑定的事件回调（由 CLIENT 装配点提供）。
     * 返回 null 表示没有事件桥（纯 fake 测试环境），此时事件分发只做命中/焦点记录。
     */
    UiEventSink eventSink();

    /**
     * 当前逻辑视口（Minecraft 逻辑像素）。窗口 resize 后由装配点更新，并触发一次
     * 布局失效（jsx-ui-spec「布局」：布局结果在 resize 时失效，不在每帧重新运行脚本）。
     */
    default com.tkisor.nekojs.api.ui.UiSize viewport() {
        return new com.tkisor.nekojs.api.ui.UiSize(320, 240);
    }

    /**
     * 原生 Screen 投影边界（jsx-ui-spec「Screen 行为」「暂停与替换」）。
     *
     * <p>返回 {@code null} 表示本平台没有客户端 Screen 宿主：root 的 create/open/paint
     * 仍作为纯逻辑路径可用（fake 测试环境），但不会有原生 Screen 出现——不是静默 no-op
     * 的能力降级，因为 {@code IPlatform.uiResources()} 在无客户端 host 时本就返回 null，
     * 脚本会看到显式的 {@link UiFailure#UNSUPPORTED_CAPABILITY}。
     */
    default UiScreenHost screenHost() {
        return null;
    }

    /** 是否在登记表之外额外允许某个 primitive（平台能力差异用）。 */
    default boolean supports(UiPrimitive primitive) {
        return adapter().primitives().contains(primitive);
    }

    /** 一次失效请求的调度边界。 */
    @FunctionalInterface
    interface Invalidator {
        /** 请求在 client owner thread 上执行 {@code action}。 */
        void request(Runnable action);
    }

    /** 事件回调分发桥。 */
    @FunctionalInterface
    interface UiEventSink {
        /**
         * 把事件分发给目标节点已绑定的事件回调。
         *
         * @throws RuntimeException 脚本回调抛出的异常；root 捕获后归入 EVENT 阶段诊断。
         */
        void dispatch(UiHostNode node, UiEvent event);
    }
}
