package com.tkisor.nekojs.api.ui;

/**
 * 响应式原语 owner：signal/store 在此创建，并统一收口 owner-thread 与批量失效语义
 * （jsx-ui-spec「状态」「状态线程」）。
 *
 * <p>owner 由 {@code Ui.createUiStateOwner} 产出；实现不构成 UI 专用 global singleton
 * 或静态跨 generation registry（AC7）。
 */
public interface UiStateOwner {

    /** owner 的 client owner thread。 */
    Thread ownerThread();

    /** 当前线程是否是 owner thread。 */
    boolean onOwnerThread();

    /** 创建 signal。 */
    <T> UiSignal<T> createSignal(T initial);

    /** 创建 store，初始字段来自 {@code initial}（拷贝）。 */
    UiStore createStore(java.util.Map<String, ?> initial);

    /** 失效汇：一个 root 的待处理 reconcile 入口。 */
    @FunctionalInterface
    interface Invalidator {
        /** 请求下一次 reconcile（同一 owner tick 内合并）。 */
        void requestReconcile();
    }

    /** 把 root 挂到本 owner；返回可注销句柄。 */
    Runnable register(UiRootHandle root, Invalidator invalidator);

    /**
     * 执行一次 owner tick：把本 owner 上累积的失效合并后，向每个受影响的 root
     * 请求一次 reconcile。从非 owner thread 调用显式失败（{@link UiFailure#OFF_OWNER_THREAD}）。
     */
    void runTick();

    /** 本 owner 是否已有待处理失效。 */
    boolean hasPendingInvalidation();

    /** 释放 owner：后续 signal 写入不再触发任何 root reconcile。 */
    void dispose();

    /** 是否已释放。 */
    boolean isDisposed();
}
