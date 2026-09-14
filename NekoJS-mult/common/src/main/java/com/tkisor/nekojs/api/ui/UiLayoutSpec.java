package com.tkisor.nekojs.api.ui;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
 * @param contentScale 设计坐标缩放系数（票 43 AC4）：{@link UiDimension.Kind#DESIGN} 尺寸乘它
 *                     得到逻辑像素；其它种类不受影响。缺省 1.0。
 * @param direction    容器主轴方向覆盖（票 43 AC3）；{@code null} 表示继承 primitive 缺省。
 *                     它改变的是布局策略（row/column 重排），不是新的 primitive。
 * @param scrollX      水平滚动偏移（逻辑像素，票 43 AC3 的 scroll 策略）；自动夹到可滚动范围。
 * @param scrollY      垂直滚动偏移（逻辑像素）
 * @param font         文本字体标识覆盖（票 43 AC3 的文本规格）；{@code null} 表示用基础属性
 * @param wrap         文本换行覆盖（票 43 AC3 的文本规格）；{@code null} 表示用基础属性
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
        boolean visible,
        double contentScale,
        UiAxis direction,
        double scrollX,
        double scrollY,
        String font,
        Boolean wrap,
        Double textFontSize
) {

    /** 逻辑像素上限：远超任何 Minecraft 逻辑视口的值属于非法布局值。 */
    public static final double MAX_LOGICAL_PIXELS = 8192.0;

    /** 缺省设计坐标缩放：不做任何缩放。 */
    public static final double DEFAULT_CONTENT_SCALE = 1.0;

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
        if (!Double.isFinite(contentScale) || contentScale < 0) {
            throw new IllegalArgumentException(
                    "contentScale must be a finite non-negative value, got " + contentScale);
        }
        scrollX = requireSize(scrollX, "scrollX");
        scrollY = requireSize(scrollY, "scrollY");
    }

    /**
     * 文本规格覆盖后的字号；{@code null} 表示未覆盖（沿用 props 的基础 fontSize）。
     *
     * <p>与 {@link #font()}/{@link #wrap()} 一样，它们是<b>解析后的覆盖值</b>，
     * 只用于 measure 阶段，不改变 {@link #width()}/{@link #height()} 的语义。
     */
    public Double fontSize() {
        return textFontSize;
    }

    /** 文本规格覆盖后的字体标识；{@code null} 表示未覆盖。 */
    public String font() {
        return font;
    }

    /** 文本规格覆盖后的换行开关；{@code null} 表示未覆盖。 */
    public Boolean wrap() {
        return wrap;
    }

    /** 是否有滚动偏移（用于 scroll 裁剪/偏移语义）。 */
    public boolean scrolls() {
        return scrollX > 0 || scrollY > 0;
    }

    /** 滚动偏移（逻辑像素，独立于重排结果）。 */
    public UiSize scrollOffset() {
        return new UiSize(scrollX, scrollY);
    }

    /** 默认约束（auto 尺寸、无间距、可见、不缩放、继承 primitive 方向）。 */
    public static UiLayoutSpec defaults() {
        return new UiLayoutSpec(null, null, 0, 0, 0, 0, 0, 0, 0,
                UiAlignment.START, UiAlignment.START, UiAnchor.TOP_LEFT, 0, true,
                DEFAULT_CONTENT_SCALE, null, 0, 0, null, null, null);
    }

    /**
     * 从脚本侧 props 解析布局约束（非法值显式抛错）。
     *
     * <p>不应用任何 profile 覆盖，设计坐标按 {@link #DEFAULT_CONTENT_SCALE} 解析——这是
     * 「没有视口事实」时的确定缺省。
     */
    public static UiLayoutSpec fromProps(UiProps props) {
        return fromProps(props, UiProfileOverride.NONE, DEFAULT_CONTENT_SCALE);
    }

    /** 从脚本侧 props 解析布局约束，并按已解析的设计缩放系数展开设计坐标。 */
    public static UiLayoutSpec fromProps(UiProps props, double contentScale) {
        return fromProps(props, UiProfileOverride.NONE, contentScale);
    }

    /**
     * 从脚本侧 props 解析布局约束，并按当前视口事实应用 profile 覆盖与设计坐标缩放
     * （jsx-ui-spec「响应式解析」「比例与设计坐标」，票 43 AC2/AC3/AC4）。
     *
     * <p>解析顺序固定：基础属性 → 本档的 profile 覆盖（缺省回退见
     * {@link UiProfileOverride#forProfile}）→ 取值校验 → min/max 裁剪。
     */
    public static UiLayoutSpec fromProps(UiProps props, ViewportContext context) {
        Map<ViewportProfile, UiProfileOverride> declared =
                UiProfileOverride.parseAll(props.raw(UiProp.PROFILES), null);
        UiProfileOverride override = UiProfileOverride.forProfile(context.profile(), declared);
        return fromProps(props, override, UiResponsiveRules.contentScale(context));
    }

    /**
     * 按一个已解析的 profile 覆盖构建布局约束（不做设计坐标缩放）。
     *
     * <p>供候选择阶段校验「每一条 profile 覆盖自身都合法」——非法覆盖必须在创建任何 host node
     * 之前失败（票 43 AC2），而不是等到某个档位恰好命中才暴露。
     */
    public static UiLayoutSpec fromProps(UiProps props, UiProfileOverride override) {
        return fromProps(props, override, DEFAULT_CONTENT_SCALE);
    }

    private static UiLayoutSpec fromProps(UiProps props, UiProfileOverride override, double contentScale) {
        Map<String, Object> values = override.applyTo(props.asMap());
        return new Builder()
                .width(dimension(values, UiProp.WIDTH))
                .height(dimension(values, UiProp.HEIGHT))
                .minWidth(number(values, UiProp.MIN_WIDTH, 0))
                .minHeight(number(values, UiProp.MIN_HEIGHT, 0))
                .maxWidth(number(values, UiProp.MAX_WIDTH, 0))
                .maxHeight(number(values, UiProp.MAX_HEIGHT, 0))
                .margin(number(values, UiProp.MARGIN, 0))
                .padding(number(values, UiProp.PADDING, 0))
                .gap(number(values, UiProp.GAP, 0))
                .align(UiAlignment.parse(string(values, UiProp.ALIGN)))
                .justify(UiAlignment.parse(string(values, UiProp.JUSTIFY)))
                .anchor(UiAnchor.parse(string(values, UiProp.ANCHOR)))
                .grow(number(values, UiProp.GROW, 0))
                .visible(bool(values, UiProp.VISIBLE, true))
                .contentScale(contentScale)
                .direction(axis(values))
                .scrollX(number(values, UiProp.SCROLL_X, 0))
                .scrollY(number(values, UiProp.SCROLL_Y, 0))
                .font(string(values, UiProp.FONT))
                .wrap(has(values, UiProp.WRAP) ? bool(values, UiProp.WRAP, false) : null)
                .textFontSize(has(values, UiProp.FONT_SIZE) ? Double.valueOf(number(values, UiProp.FONT_SIZE, 0)) : null)
                .build();
    }

    private static boolean has(Map<String, Object> values, UiProp prop) {
        return values.containsKey(prop.jsName());
    }

    /** 单轴尺寸：缺省为 auto；已解析的 {@link UiDimension} 直接使用（profile 覆盖路径）。 */
    private static UiDimension dimension(Map<String, Object> values, UiProp prop) {
        Object value = values.get(prop.jsName());
        if (value == null) return null;
        if (value instanceof UiDimension dimension) return dimension;
        return UiDimension.parse(value);
    }

    private static double number(Map<String, Object> values, UiProp prop, double fallback) {
        Object value = values.get(prop.jsName());
        return value instanceof Number number ? number.doubleValue() : fallback;
    }

    private static String string(Map<String, Object> values, UiProp prop) {
        Object value = values.get(prop.jsName());
        return value instanceof String text ? text : null;
    }

    private static boolean bool(Map<String, Object> values, UiProp prop, boolean fallback) {
        Object value = values.get(prop.jsName());
        return value instanceof Boolean bool ? bool : fallback;
    }

    private static UiAxis axis(Map<String, Object> values) {
        Object value = values.get(UiProp.DIRECTION.jsName());
        if (value == null) return null;
        if (value instanceof UiAxis axis) return axis;
        UiAxis axis = UiAxis.fromCode(String.valueOf(value));
        if (axis == null) {
            throw new IllegalArgumentException("direction must be row|column, got " + value);
        }
        return axis;
    }

    /** 布局约束清单（供契约文档与漂移测试对比属性登记表）。 */
    public static List<UiProp> props() {
        return List.of(UiProp.WIDTH, UiProp.HEIGHT, UiProp.MIN_WIDTH, UiProp.MIN_HEIGHT,
                UiProp.MAX_WIDTH, UiProp.MAX_HEIGHT, UiProp.MARGIN, UiProp.PADDING, UiProp.GAP,
                UiProp.ALIGN, UiProp.JUSTIFY, UiProp.ANCHOR, UiProp.GROW, UiProp.VISIBLE,
                UiProp.DIRECTION, UiProp.PROFILES);
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
        private double contentScale = DEFAULT_CONTENT_SCALE;
        private UiAxis direction;
        private double scrollX;
        private double scrollY;
        private String font;
        private Boolean wrap;
        private Double textFontSize;

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
        public Builder contentScale(double value) { this.contentScale = value; return this; }
        public Builder direction(UiAxis value) { this.direction = value; return this; }
        public Builder scrollX(double value) { this.scrollX = value; return this; }
        public Builder scrollY(double value) { this.scrollY = value; return this; }
        public Builder font(String value) { this.font = value; return this; }
        public Builder wrap(Boolean value) { this.wrap = value; return this; }
        /** 文本规格的字号覆盖；基础 props 的 fontSize 也在这里统一进入解析结果。 */
        public Builder textFontSize(Double value) { this.textFontSize = value; return this; }

        public UiLayoutSpec build() {
            return new UiLayoutSpec(width, height, minWidth, minHeight, maxWidth, maxHeight,
                    margin, padding, gap, align, justify, anchor, grow, visible,
                    contentScale, direction, scrollX, scrollY, font, wrap, textFontSize);
        }
    }
}