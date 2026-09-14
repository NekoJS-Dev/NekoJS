package com.tkisor.nekojs.api.ui;

import java.util.List;

/**
 * 单节点解析后的布局约束（jsx-ui-spec「布局」「比例与设计坐标」）。
 *
 * <p>布局只支持明确的 Minecraft UI 约束：逻辑像素、fill、auto、受控百分比、min/max、
 * 主轴/交叉轴对齐、间距与内边距。不实现 CSS selector、继承、浏览器 box model 或任意
 * CSS 字符串。非法值由 {@link Builder#build()} 显式拒绝（AC2）。
 *
 * @param width        主轴尺寸约束
 * @param height       交叉轴尺寸约束
 * @param minWidth     最小宽度（逻辑像素）
 * @param minHeight    最小高度（逻辑像素）
 * @param maxWidth     最大宽度（逻辑像素）
 * @param maxHeight    最大高度（逻辑像素）
 * @param margin       外边距（逻辑像素）
 * @param padding      内边距（逻辑像素）
 * @param gap          主轴子节点间距（逻辑像素）
 * @param align        交叉轴对齐
 * @param justify      主轴对齐
 * @param anchor       锚定
 * @param grow         剩余空间分配权重
 * @param visible      是否参与布局与绘制
 */
public record UiLayoutSpec(
        UiDimension width,
        UiDimension height,
        double minWidth,
        double minHeight,
        double maxWidth,
        double maxHeight,
        double margin,
        double padding,
        double gap,
        UiAlignment align,
        UiAlignment justify,
        UiAnchor anchor,
        double grow,
        boolean visible
) {

    /** 逻辑像素上限：远超任何 Minecraft 逻辑视口的值属于非法布局值。 */
    public static final double MAX_LOGICAL_PIXELS = 8192.0;

    public UiLayoutSpec {
        width = width == null ? UiDimension.auto() : width;
        height = height == null ? UiDimension.auto() : height;
        minWidth = requireSize(minWidth, "minWidth");
        minHeight = requireSize(minHeight, "minHeight");
        maxWidth = requireSize(maxWidth, "maxWidth");
        maxHeight = requireSize(maxHeight, "maxHeight");
        margin = requireSize(margin, "margin");
        padding = requireSize(padding, "padding");
        gap = requireSize(gap, "gap");
        if (maxWidth < minWidth) {
            throw new IllegalArgumentException("maxWidth must be >= minWidth");
        }
        if (maxHeight < minHeight) {
            throw new IllegalArgumentException("maxHeight must be >= minHeight");
        }
        if (!(grow >= 0.0) || Double.isInfinite(grow) || grow > 1024.0) {
            throw new IllegalArgumentException("grow must be a finite number in [0, 1024], got " + grow);
        }
        align = align == null ? UiAlignment.START : align;
        justify = justify == null ? UiAlignment.START : justify;
        anchor = anchor == null ? UiAnchor.TOP_LEFT : anchor;
    }

    /** 默认约束（auto 尺寸、无间距、可见）。 */
    public static UiLayoutSpec defaults() {
        return new UiLayoutSpec(null, null, 0, 0, 0, 0, 0, 0, 0,
                UiAlignment.START, UiAlignment.START, UiAnchor.TOP_LEFT, 0, true);
    }

    /** 从脚本侧 props 解析布局约束（非法值显式抛错）。 */
    public static UiLayoutSpec fromProps(UiProps props) {
        return new Builder()
                .width(props.has(UiProp.WIDTH) ? UiDimension.parse(props.raw(UiProp.WIDTH)) : null)
                .height(props.has(UiProp.HEIGHT) ? UiDimension.parse(props.raw(UiProp.HEIGHT)) : null)
                .minWidth(props.number(UiProp.MIN_WIDTH, 0))
                .minHeight(props.number(UiProp.MIN_HEIGHT, 0))
                .maxWidth(props.number(UiProp.MAX_WIDTH, 0))
                .maxHeight(props.number(UiProp.MAX_HEIGHT, 0))
                .margin(props.number(UiProp.MARGIN, 0))
                .padding(props.number(UiProp.PADDING, 0))
                .gap(props.number(UiProp.GAP, 0))
                .align(UiAlignment.parse(props.string(UiProp.ALIGN)))
                .justify(UiAlignment.parse(props.string(UiProp.JUSTIFY)))
                .anchor(UiAnchor.parse(props.string(UiProp.ANCHOR)))
                .grow(props.number(UiProp.GROW, 0))
                .visible(props.bool(UiProp.VISIBLE, true))
                .build();
    }

    /** 布局约束清单（供契约文档与漂移测试对比属性登记表）。 */
    public static List<UiProp> props() {
        return List.of(UiProp.WIDTH, UiProp.HEIGHT, UiProp.MIN_WIDTH, UiProp.MIN_HEIGHT,
                UiProp.MAX_WIDTH, UiProp.MAX_HEIGHT, UiProp.MARGIN, UiProp.PADDING, UiProp.GAP,
                UiProp.ALIGN, UiProp.JUSTIFY, UiProp.ANCHOR, UiProp.GROW, UiProp.VISIBLE);
    }

    private static double requireSize(double value, String name) {
        if (!(value >= 0.0) || Double.isInfinite(value) || value > MAX_LOGICAL_PIXELS) {
            throw new IllegalArgumentException(
                    name + " must be a finite logical-pixel value in [0, " + MAX_LOGICAL_PIXELS + "], got " + value);
        }
        return value;
    }

    /** 布局约束构建器；非法值在 {@link #build()} 抛出。 */
    public static final class Builder {
        private UiDimension width;
        private UiDimension height;
        private double minWidth;
        private double minHeight;
        private double maxWidth;
        private double maxHeight;
        private double margin;
        private double padding;
        private double gap;
        private UiAlignment align = UiAlignment.START;
        private UiAlignment justify = UiAlignment.START;
        private UiAnchor anchor = UiAnchor.TOP_LEFT;
        private double grow;
        private boolean visible = true;

        public Builder width(UiDimension value) { this.width = value; return this; }
        public Builder height(UiDimension value) { this.height = value; return this; }
        public Builder minWidth(double value) { this.minWidth = value; return this; }
        public Builder minHeight(double value) { this.minHeight = value; return this; }
        public Builder maxWidth(double value) { this.maxWidth = value; return this; }
        public Builder maxHeight(double value) { this.maxHeight = value; return this; }
        public Builder margin(double value) { this.margin = value; return this; }
        public Builder padding(double value) { this.padding = value; return this; }
        public Builder gap(double value) { this.gap = value; return this; }
        public Builder align(UiAlignment value) { this.align = value; return this; }
        public Builder justify(UiAlignment value) { this.justify = value; return this; }
        public Builder anchor(UiAnchor value) { this.anchor = value; return this; }
        public Builder grow(double value) { this.grow = value; return this; }
        public Builder visible(boolean value) { this.visible = value; return this; }

        public UiLayoutSpec build() {
            return new UiLayoutSpec(width, height, minWidth, minHeight, maxWidth, maxHeight,
                    margin, padding, gap, align, justify, anchor, grow, visible);
        }
    }
}