package com.tkisor.nekojs.api;

import graal.graalvm.polyglot.Value;

/**
 * JS 类型适配器接口。<p>
 * 插件类可以实现此接口，以提供自定义的类型转换。
 */
public interface JSTypeAdapter<T> {
    /**
     * 获取目标类型
     */
    Class<T> getTargetClass();
    /**
     * 判断 JS 值是否可以转换为目标类型
     */
    boolean canConvert(Value value);
    /**
     * 将 JS 值转换为目标类型，如果无法转换应直接抛出异常更为稳妥，而非返回null
     */
    T convert(Value value);
}
