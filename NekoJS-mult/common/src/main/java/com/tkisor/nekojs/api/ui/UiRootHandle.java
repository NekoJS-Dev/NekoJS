package com.tkisor.nekojs.api.ui;

/**
 * UI root 的生命周期句柄（jsx-ui-spec「公开模型」「generation 生命周期」）。
 *
 * <p>句柄只属于创建它的 CLIENT generation：{@code dispose()} 之后所有操作显式失败
 * （{@link UiFailure#ROOT_DISPOSED}），不静默操作新 generation 或另一个 Screen。
 *
 * <p>线程约束（jsx-ui-spec「状态线程」）：{@code open}/{@code invalidate}/{@code dispatchEvent}/
 * {@code dispose}/{@code paint} 只能在 root 的 client owner thread 上调用；非 owner thread
 * 的调用显式失败并记录可定位错误（{@link UiFailure#OFF_OWNER_THREAD}）。
 * {@code status()} 与 {@code inspection()} 是只读查询，可从任意线程调用。
 */
public interface UiRootHandle {

    /** root 标识。 */
    String rootId();

    /** root 创建时绑定的 CLIENT generation。 */
    int generation();

    /** 当前生命周期状态（任意线程可读）。 */
    UiRootStatus status();

    /** 是否仍可接受打开/失效/事件分发。 */
    boolean isActive();

    /** Screen 选项。 */
    UiScreenOptions options();

    /** 首次渲染并原子提交 host tree；{@code CREATED} → {@code OPEN}。 */
    void open();

    /** 使 root 失效：停止接受新事件、取消待处理 reconcile；保留最后一次有效树。 */
    void invalidate();

    /** 候选渲染与应用状态失效（合并同一 owner tick 内的重复失效）。 */
    void invalidateAndReconcile();

    /** 事件分发：命中、焦点移动与回调替换都由 root 维护。 */
    void dispatchEvent(UiEvent event);

    /** 绘制已提交 host tree，产出可观察的绘制顺序与裁剪矩形。 */
    UiHostInspection paint();

    /** 释放 root：幂等；释放宿主节点、事件句柄与订阅。 */
    void dispose();

    /** 只读检查（任意线程可读）。 */
    UiHostInspection inspection();

    /**
     * 当前响应式视口事实（jsx-ui-spec「响应式模型」，票 43 AC1）：判定得到的 Viewport
     * Profile、逻辑视口、可用布局区与安全区域。任意线程可读；不属于脚本可见的写操作。
     *
     * <p><b>脚本 getter 语义</b>：脚本在 render 中读取本方法会被记录为「该 root 的布局依赖
     * 视口」，此后 resize 必须重跑脚本 render（结构可能随视口改变）。Java 侧与 inspector 的
     * 只读查询请用 {@link #viewportState()}，它不产生该副作用。
     */
    ProjectedViewport viewport();

    /**
     * 响应式布局契约的只读观察者（票 43 AC1/AC5）：当前档位、可用布局区与每个可见节点的
     * 最终解析样式。只读事实视图，不是第二份布局真相源，也不改变布局依赖记录。
     */
    UiViewportState viewportState();

    /** 最近一次成功提交的候选树根 VNode，可为 {@code null}。 */
    UiVNode committedTree();

    /** 当前焦点节点路径，可为 {@code null}。 */
    String focusedPath();
}
