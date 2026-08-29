package com.tkisor.nekojs.api.data;

import java.util.Collection;

/**
 * JS 类型适配器注册表（新 API）。
 *
 * <p>插件可通过 {@link #register(JsTypeAdapter)} 注册自定义转换器；实现内部会把新式
 * {@link JsTypeAdapter} 桥接为底层旧式 {@link com.tkisor.nekojs.api.JSTypeAdapter}。
 *
 * @see JsTypeAdapter
 */
public interface JsAdapterRegistry {
    /** 注册一个类型适配器；adapter 不能为 {@code null}。 */
    <T> void register(JsTypeAdapter<T> adapter);

    /** 返回已注册适配器的只读视图。 */
    Collection<JsTypeAdapter<?>> view();
}
