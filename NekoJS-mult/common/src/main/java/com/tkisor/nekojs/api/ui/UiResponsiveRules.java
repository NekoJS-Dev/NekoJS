package com.tkisor.nekojs.api.ui;

/**
 * Viewport Profile 判定规则的公开入口（jsx-ui-spec「响应式模型」，票 43 AC1）。
 *
 * <p>本类是 profile 判定的<b>唯一事实源</b>：{@link ViewportContext#profile()} 与
 * {@link ProjectedViewport#profile()} 都委托到这里，不存在第二份阈值表。
 *
 * <p>规则（自宽到窄取<b>首个</b>满足的档位，是确定的优先级）：
 * <pre>
 *   DESKTOP_WIDE  可用宽 >= 640 且 可用高 >= 360
 *   DESKTOP       可用宽 >= 480 且 可用高 >= 300
 *   TABLET        可用宽 >= 360 且 可用高 >= 240
 *   HANDHELD      可用宽 >= 260 且 可用高 >= 180
 *   COMPACT       可用宽 >= 180 且 可用高 >= 120
 *   MINIMAL       其余情况
 * </pre>
 *
 * <p>tie-break：宽与高落在不同档时取<b>更窄</b>（编号更大）的档，即两轴都必须满足。
 * 判定只看可用布局区（逻辑视口减去安全区域）与 host 布局能力，<b>不看</b> GUI scale；
 * 声明 {@link ViewportContext.Capability#FIXED_VIEWPORT} 时视口由平台固定，
 * 判定退化为「按当前视口判档」，不产生窗口变化语义。
 *
 * <p><b>受控覆盖键</b>（AC1 要求可观察输出进入 contract）：每档的覆盖只接受
 * {@link UiProfileOverride#KEYS} 中的键——尺寸（width/height 支持逻辑像素/百分比/fill/auto/
 * {@code <n>dp} 设计坐标）、min/max、margin/padding/gap、align/justify/anchor、方向
 * （direction: row|column）、可见性（visible）、文本规格（font/fontSize/wrap）与滚动策略
 * （scrollX/scrollY）。未知键与不适用于该 primitive 的键都在候选阶段显式失败。
 */
public final class UiResponsiveRules {

    private UiResponsiveRules() {
    }

    /**
     * 按可用布局区判定 profile。
     *
     * @param logicalWidth  root 的逻辑视口宽
     * @param logicalHeight root 的逻辑视口高
     * @param safeArea      常驻 chrome 占用的安全区域，可为 {@code null}
     */
    public static ViewportProfile profileOf(double logicalWidth, double logicalHeight, ViewportContext.SafeArea safeArea) {
        return new ViewportContext(logicalWidth, logicalHeight,
                safeArea == null ? ViewportContext.SafeArea.none() : safeArea,
                1, java.util.Set.of()).profile();
    }

    /** 按可用布局区判定 profile（无安全区域）。 */
    public static ViewportProfile profileOf(double logicalWidth, double logicalHeight) {
        return profileOf(logicalWidth, logicalHeight, ViewportContext.SafeArea.none());
    }

    /** 判定入口：可用宽与可用高都必须达到档位阈值，自宽到窄取首个匹配。 */
    static ViewportProfile profileOf(ViewportContext context) {
        double width = context.contentWidth();
        double height = context.contentHeight();
        for (ViewportProfile profile : ViewportProfile.values()) {
            if (profile == ViewportProfile.MINIMAL) continue;
            if (width >= profile.minContentWidth() && height >= profile.minContentHeight()) {
                return profile;
            }
        }
        return ViewportProfile.MINIMAL;
    }

    // ---- 设计坐标与字号（AC4）----

    /** 设计坐标的参照可用宽：等于 1 档（宽屏桌面）的阈值。 */
    public static final double DESIGN_REFERENCE_WIDTH = 640.0;

    /**
     * 设计坐标缩放系数：可用布局宽相对 {@link #DESIGN_REFERENCE_WIDTH} 的连续比例。
     *
     * <p>连续、单调、不需要断点即可适配窗口；落在 {@code [0, 1]}。视口比参照更宽时不放大
     * （避免在超宽窗口上把设计稿整体撑开），更窄时按比例缩小。
     */
    public static double contentScale(ViewportContext context) {
        return contentScale(context.contentWidth());
    }

    /** 设计坐标缩放系数（按可用布局宽）。 */
    public static double contentScale(double contentWidth) {
        if (!Double.isFinite(contentWidth) || contentWidth < 0) {
            throw new UiFailure(UiDiagnosticStage.LAYOUT, UiFailure.INVALID_LAYOUT,
                    "content width must be a finite non-negative logical-pixel value, got " + contentWidth, null);
        }
        return Math.min(1.0, contentWidth / DESIGN_REFERENCE_WIDTH);
    }

    /** 字号读性下限（逻辑像素）。 */
    public static final double MIN_FONT_SIZE = 6.0;

    /** 字号读性上限（逻辑像素）。 */
    public static final double MAX_FONT_SIZE = 24.0;

    /**
     * 受控字号解析（AC4/规格 105）：字号<b>不</b>按设计坐标比例无限等比缩放。
     *
     * <p>规则固定：{@code clamp(声明字号 × sqrt(contentScale), 6, 24)}。用平方根而不是线性
     * 比例，是为了让窄屏上文本尺寸的收缩显著慢于容器——文本可读性不随窗口一起线性塌缩；
     * 再夹到 {@link #MIN_FONT_SIZE}..{@link #MAX_FONT_SIZE}，保证任何 profile 下都落在可读区间。
     *
     * @param declaredSize 声明的字号（逻辑像素，必须为正的有限值）
     * @param context      当前响应式视口事实
     */
    public static double resolveFontSize(double declaredSize, ViewportContext context) {
        return resolveFontSize(declaredSize, contentScale(context));
    }

    /** 受控字号解析（按已解析出的设计缩放系数）。 */
    public static double resolveFontSize(double declaredSize, double contentScale) {
        if (!Double.isFinite(declaredSize) || declaredSize <= 0) {
            throw new UiFailure(UiDiagnosticStage.LAYOUT, UiFailure.INVALID_LAYOUT,
                    "font size must be a positive finite logical-pixel value, got " + declaredSize, null);
        }
        if (!Double.isFinite(contentScale) || contentScale < 0) {
            throw new UiFailure(UiDiagnosticStage.LAYOUT, UiFailure.INVALID_LAYOUT,
                    "content scale must be a finite non-negative value, got " + contentScale, null);
        }
        double scaled = declaredSize * Math.sqrt(contentScale);
        return Math.max(MIN_FONT_SIZE, Math.min(MAX_FONT_SIZE, scaled));
    }
}
