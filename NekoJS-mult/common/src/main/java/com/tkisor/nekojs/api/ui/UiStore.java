package com.tkisor.nekojs.api.ui;

/**
 * 显式响应式原语：store（jsx-ui-spec「状态」）。
 *
 * <p>字段读写按顶层键；每次写入只让读到该键的 root 失效（依赖重建由
 * {@link #read(String)} 在 render function 内建立）。
 */
@com.tkisor.nekojs.api.annotation.ContractReceiver("UiStore")
public interface UiStore {

    /** 读取字段；未设置的键返回 {@code null}。 */
    Object read(String key);

    /** 写入字段；值相等时不触发失效。 */
    void write(String key, Object value);

    /** 合并写入若干字段，作为一次失效。 */
    void merge(java.util.Map<String, ?> values);

    /** 字段的只读 signal 视图。 */
    UiReadonlySignal<Object> field(String key);

    /** 当前全部字段的不可变快照。 */
    java.util.Map<String, Object> snapshot();
}