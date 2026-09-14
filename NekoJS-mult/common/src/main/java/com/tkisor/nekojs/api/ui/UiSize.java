package com.tkisor.nekojs.api.ui;

/**
 * 测量结果尺寸（逻辑像素），由 host Adapter 的文本测量与 common 布局共同产出。
 *
 * @param width  宽度（非负）
 * @param height 高度（非负）
 */
public record UiSize(double width, double height) {

    public UiSize {
        if (!Double.isFinite(width) || !Double.isFinite(height)) {
            throw new IllegalArgumentException("size fields must be finite");
        }
        if (width < 0 || height < 0) {
            throw new IllegalArgumentException("size must not be negative");
        }
    }

    /** 零尺寸。 */
    public static UiSize zero() {
        return new UiSize(0, 0);
    }

    /** 逐轴最大值，用于内容测量合并。 */
    public UiSize max(UiSize other) {
        return new UiSize(Math.max(width, other.width), Math.max(height, other.height));
    }
}
