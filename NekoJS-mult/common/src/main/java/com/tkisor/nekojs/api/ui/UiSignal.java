package com.tkisor.nekojs.api.ui;

/**
 * 显式响应式原语：signal（jsx-ui-spec「状态」「状态线程」）。
 *
 * <p>读取在 render function 执行时建立 root 依赖；写入合并同一 owner tick 内的重复失效，
 * 并在客户端 owner thread 触发下一次 reconcile。不检查私有依赖集合——调用者只从
 * 读取/写入结果观察语义。
 */
@com.tkisor.nekojs.api.annotation.ContractReceiver("UiSignal")
public interface UiSignal<T> {

    /** 读取当前值（在 render function 中读取会建立依赖）。 */
    T get();

    /** 直接设置新值；值相等时不触发失效。 */
    void set(T value);

    /** 基于旧值更新；{@code updater} 抛错时不改变值。 */
    void update(java.util.function.UnaryOperator<T> updater);
}
