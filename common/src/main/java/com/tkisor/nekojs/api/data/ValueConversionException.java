package com.tkisor.nekojs.api.data;

/**
 * 类型适配器（{@link com.tkisor.nekojs.api.JSTypeAdapter}）转换失败时统一抛出。
 *
 * <p>承载目标类型、期望的输入形状、原始输入等信息，便于脚本端定位问题。所有 adapter
 * 的 {@code apply} 失败均应抛此异常（而非返回 {@code null}，返回 null 会导致下游 NPE）。
 * 继承 {@link RuntimeException}，与 GraalJS {@code targetTypeMapping} 把 adapter 当作
 * {@code Function} 使用的异常传播行为兼容。
 */
public class ValueConversionException extends RuntimeException {

    private final Class<?> targetClass;
    private final String expectedShape;

    public ValueConversionException(Class<?> targetClass, String expectedShape, Object actualValue, String message) {
        super(format(targetClass, expectedShape, actualValue, message));
        this.targetClass = targetClass;
        this.expectedShape = expectedShape;
    }

    public ValueConversionException(Class<?> targetClass, String expectedShape, Object actualValue, String message, Throwable cause) {
        super(format(targetClass, expectedShape, actualValue, message), cause);
        this.targetClass = targetClass;
        this.expectedShape = expectedShape;
    }

    private static String format(Class<?> targetClass, String expectedShape, Object actualValue, String message) {
        StringBuilder sb = new StringBuilder("[NekoJS] Cannot convert ");
        sb.append(describe(actualValue));
        sb.append(" to ");
        sb.append(targetClass != null ? targetClass.getName() : "?");
        if (expectedShape != null && !expectedShape.isEmpty()) {
            sb.append(" (expected ").append(expectedShape).append(")");
        }
        sb.append(": ");
        sb.append(message != null ? message : "");
        return sb.toString();
    }

    private static String describe(Object v) {
        if (v == null) return "null";
        Class<?> c = v.getClass();
        // 值类型直接 toString 更直观（如 "minecraft:stone"）
        if (c == String.class) return "string\"" + v + "\"";
        if (v instanceof Number || v instanceof Boolean) return c.getSimpleName() + "(" + v + ")";
        return c.getSimpleName();
    }

    public Class<?> targetClass() {
        return targetClass;
    }

    public String expectedShape() {
        return expectedShape;
    }
}
