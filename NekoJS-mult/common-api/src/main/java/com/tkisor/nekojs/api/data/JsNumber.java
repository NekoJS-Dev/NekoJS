package com.tkisor.nekojs.api.data;

import java.util.Objects;

/**
 * 携带原始文本的 JS 数值包装，保留脚本侧数字的字面量表示。
 *
 * <p>用于在 JSON/NBT 等输出中还原 JS 数字的精确文本（如 {@code "1e21"}、{@code "0.1"}），
 * 同时提供标准 {@link Number} 数值访问。值必须为有限数；不可变。
 */
public final class JsNumber extends Number {
    private static final long serialVersionUID = 1L;

    private final double value;
    private final String canonicalText;

    /**
     * @param value         数值，必须为有限数（非 NaN/无穷）
     * @param canonicalText 该数值的规范文本表示，不能为空
     */
    public JsNumber(double value, String canonicalText) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException("value must be finite");
        }
        this.value = value;
        this.canonicalText = Objects.requireNonNull(canonicalText, "canonicalText");
        if (canonicalText.isBlank()) {
            throw new IllegalArgumentException("canonicalText must not be blank");
        }
    }

    /** 返回规范文本表示。 */
    public String canonicalText() {
        return canonicalText;
    }

    /** 若数值为整数且落在 long 范围内返回 {@link Long}，否则返回 {@link Double}。 */
    public Number nativeNumber() {
        if (value == Math.rint(value) && value >= -0x1.0p63 && value < 0x1.0p63) {
            return Long.valueOf((long) value);
        }
        return Double.valueOf(value);
    }

    @Override public int intValue() { return (int) value; }
    @Override public long longValue() { return (long) value; }
    @Override public float floatValue() { return (float) value; }
    @Override public double doubleValue() { return value; }

    /** 返回规范文本表示。 */
    @Override
    public String toString() {
        return canonicalText;
    }
}
