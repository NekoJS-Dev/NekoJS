package com.tkisor.nekojs.api.data;

/**
 * Thrown when a type adapter ({@link com.tkisor.nekojs.api.JSTypeAdapter}) fails to convert a
 * value. Carries the target type, the expected input shape and the original input so the script
 * can locate the problem. Every failing adapter {@code apply} throws this instead of returning
 * {@code null}, because a {@code null} return causes an NPE downstream. It extends
 * {@link RuntimeException}, which matches how GraalJS propagates exceptions from an adapter used
 * as a {@code Function} through {@code targetTypeMapping}.
 */
public class ValueConversionException extends RuntimeException {

    private static final long serialVersionUID = 1L;

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
        // Value types read better through a direct toString (for example "minecraft:stone")
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
