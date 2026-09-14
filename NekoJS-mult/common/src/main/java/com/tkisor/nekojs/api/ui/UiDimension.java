package com.tkisor.nekojs.api.ui;

import java.util.Objects;

/**
 * 单轴尺寸约束：{@code auto} | {@code fill} | 逻辑像素 | 受控百分比。
 *
 * <p>解析失败（未知关键字、负数、非有限数、超限百分比）显式抛
 * {@link IllegalArgumentException}，不静默回退（AC2）。
 */
public record UiDimension(Kind kind, double value) {

    /** 尺寸种类。 */
    public enum Kind {
        /** 由内容测量决定。 */
        AUTO,
        /** 填满父节点可用空间。 */
        FILL,
        /** 固定逻辑像素。 */
        PIXELS,
        /** 相对包含块的受控百分比（0..100）。 */
        PERCENT
    }

    public UiDimension {
        Objects.requireNonNull(kind, "kind");
        switch (kind) {
            case AUTO, FILL -> {
                if (value != 0.0) {
                    throw new IllegalArgumentException(kind + " dimension must not carry a value");
                }
            }
            case PIXELS -> {
                if (!(value >= 0.0) || Double.isInfinite(value) || value > UiLayoutSpec.MAX_LOGICAL_PIXELS) {
                    throw new IllegalArgumentException(
                            "pixels dimension must be a finite value in [0, " + UiLayoutSpec.MAX_LOGICAL_PIXELS + "], got " + value);
                }
            }
            case PERCENT -> {
                if (!(value >= 0.0) || value > 100.0) {
                    throw new IllegalArgumentException("percent dimension must be in [0, 100], got " + value);
                }
            }
        }
    }

    public static UiDimension auto() { return new UiDimension(Kind.AUTO, 0); }
    public static UiDimension fill() { return new UiDimension(Kind.FILL, 0); }
    public static UiDimension pixels(double value) { return new UiDimension(Kind.PIXELS, value); }
    public static UiDimension percent(double value) { return new UiDimension(Kind.PERCENT, value); }

    /** 脚本侧写法（{@code "auto"} / {@code "fill"} / 数值 / {@code "50%"}）。 */
    public String literal() {
        return switch (kind) {
            case AUTO -> "auto";
            case FILL -> "fill";
            case PIXELS -> trim(value);
            case PERCENT -> trim(value) + "%";
        };
    }

    /** 解析脚本侧尺寸写法；非法值显式抛错。 */
    public static UiDimension parse(Object raw) {
        if (raw == null) return auto();
        if (raw instanceof Number number) return pixels(number.doubleValue());
        if (raw instanceof String text) {
            String trimmed = text.trim();
            if (trimmed.isEmpty()) {
                throw new IllegalArgumentException("dimension string must not be blank");
            }
            if ("auto".equals(trimmed)) return auto();
            if ("fill".equals(trimmed)) return fill();
            if (trimmed.endsWith("%")) {
                return percent(parseNumber(trimmed.substring(0, trimmed.length() - 1), trimmed));
            }
            return pixels(parseNumber(trimmed, trimmed));
        }
        throw new IllegalArgumentException(
                "dimension must be a number or one of auto|fill|<n>|<n>%, got " + raw.getClass().getName());
    }

    private static double parseNumber(String raw, String original) {
        try {
            double parsed = Double.parseDouble(raw.trim());
            if (!Double.isFinite(parsed)) {
                throw new IllegalArgumentException("dimension must be finite, got " + original);
            }
            return parsed;
        } catch (NumberFormatException failure) {
            throw new IllegalArgumentException("dimension is not a valid size: " + original, failure);
        }
    }

    private static String trim(double value) {
        return value == Math.rint(value) ? Long.toString((long) value) : Double.toString(value);
    }
}
