package com.tkisor.nekojs.api.ui;

/**
 * 布局结果的最终矩形，单位为 Minecraft 逻辑像素（jsx-ui-spec「布局」）。
 *
 * <p>宿主 Adapter 与 inspector 只读该矩形；common 不假设任何原生坐标系。
 *
 * @param x      左上角 x
 * @param y      左上角 y
 * @param width  宽度（非负）
 * @param height 高度（非负）
 */
public record UiRect(double x, double y, double width, double height) {

    public UiRect {
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(width) || !Double.isFinite(height)) {
            throw new IllegalArgumentException("rect fields must be finite");
        }
        if (width < 0 || height < 0) {
            throw new IllegalArgumentException("rect size must not be negative");
        }
    }

    /** 空矩形。 */
    public static UiRect empty() {
        return new UiRect(0, 0, 0, 0);
    }

    /** 是否包含逻辑点。 */
    public boolean contains(double px, double py) {
        return px >= x && py >= y && px < x + width && py < y + height;
    }

    /** 裁剪视图：与 {@code other} 求交。 */
    public UiRect intersect(UiRect other) {
        double left = Math.max(x, other.x);
        double top = Math.max(y, other.y);
        double right = Math.min(x + width, other.x + other.width);
        double bottom = Math.min(y + height, other.y + other.height);
        if (right <= left || bottom <= top) return empty();
        return new UiRect(left, top, right - left, bottom - top);
    }

    /** 是否为空矩形（零面积）。 */
    public boolean isEmpty() {
        return width <= 0 || height <= 0;
    }
}
