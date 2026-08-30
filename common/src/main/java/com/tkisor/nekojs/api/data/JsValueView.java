package com.tkisor.nekojs.api.data;

import java.util.Collection;

/**
 * JS 值的抽象视图，屏蔽底层 GraalVM {@code Value}。
 *
 * <p>把脚本侧值分成 null / 字符串 / 数字 / 布尔 / host object / 数组 / proxy object
 * 等类别，供 {@link JsTypeAdapter} 在不依赖引擎实现的前提下检查与读取值。
 */
public interface JsValueView {
    /** 是否为 {@code null}。 */
    boolean isNull();
    /** 是否为字符串。 */
    boolean isString();
    /** 是否为数字。 */
    boolean isNumber();
    /** 是否为布尔。 */
    boolean isBoolean();
    /** 是否为 Java host object。 */
    boolean isHostObject();
    /** 是否为数组。 */
    boolean isArray();

    /** 是否为 JS proxy object；默认 {@code false}。 */
    default boolean isProxyObject() {
        return false;
    }

    /** 返回底层 proxy object；非 proxy 时抛 {@link UnsupportedOperationException}。 */
    default Object asProxyObject() {
        throw new UnsupportedOperationException("value is not a proxy object");
    }

    /** 返回字符串值；非字符串时抛异常。 */
    String asString();
    /** 返回整数值（截断小数）。 */
    int asInt();
    /** 返回 double 值。 */
    double asDouble();
    /** 返回布尔值。 */
    boolean asBoolean();

    /** 返回 host object，并强制转换为指定类型；无法转换时返回 {@code null}。 */
    <T> T asHostObject(Class<T> type);

    /** 是否包含指定成员。 */
    boolean hasMember(String key);
    /** 读取指定成员。 */
    JsValueView getMember(String key);
    /** 读取指定下标元素。 */
    JsValueView getArrayElement(long index);
    /** 数组长度。 */
    long getArraySize();
    /** 所有成员名。 */
    Collection<String> getMemberKeys();
}
