package com.tkisor.nekojs.api.ui;

/**
 * 只读 signal 视图：store 的字段投影。
 *
 * <p>store 不把可变 Java 对象作为共享快照（jsx-ui-spec「状态」）：字段读取经过
 * {@link UiSignal#get()}，写入只经 store 的显式写入方法。
 */
public interface UiReadonlySignal<T> {

    /** 读取当前值（在 render function 中读取会建立依赖）。 */
    T get();
}
