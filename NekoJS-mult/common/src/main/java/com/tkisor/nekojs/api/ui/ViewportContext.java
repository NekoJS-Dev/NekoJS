package com.tkisor.nekojs.api.ui;

/**
 * Profile 判定的输入事实（jsx-ui-spec「响应式模型」，票 43 AC1）。
 *
 * <p>四个输入各自独立、都进入公开 contract：
 * <ul>
 *   <li>{@code logicalWidth}/{@code logicalHeight}：root 的实际逻辑视口（Minecraft 逻辑像素），
 *       由装配点在 resize 时更新——这是 profile 的<b>主</b>输入。</li>
 *   <li>{@code safeArea}：常驻 chrome（HUD 边、窗口留白）占用的逻辑像素；判定使用的是
 *       「逻辑视口 - 安全区域」的可用布局区，不是原始视口。</li>
 *   <li>{@code guiScale}：原生 GUI scale。<b>只是输入之一</b>，不参与档位阈值，也不能与
 *       profile 编号互相推导（规格 Out of Scope：不把 scale 1–6 定义成 profile）。</li>
 *   <li>{@code capabilities}：host 布局能力；当前登记的 {@link Capability#FIXED_VIEWPORT}
 *       表示 host 的逻辑视口由平台固定（不可 resize），此时判定退化为「按当前视口判档」，
 *       不再考虑窗口变化。</li>
 * </ul>
 *
 * <p>非法输入显式失败：负尺寸或非有限值抛 {@link UiFailure}（{@code INVALID_LAYOUT}），
 * {@code guiScale < 1} 抛 {@link IllegalArgumentException}。不静默夹取。
 */
public record ViewportContext(
        double logicalWidth,
        double logicalHeight,
        SafeArea safeArea,
        int guiScale,
        java.util.Set<Capability> capabilities
) {

    /** 常驻 chrome 在逻辑视口四周占用的安全区域。 */
    public record SafeArea(double left, double top, double right, double bottom) {

        private static final SafeArea NONE = new SafeArea(0, 0, 0, 0);

        public SafeArea {
            left = requireInset(left, "safe area left");
            top = requireInset(top, "safe area top");
            right = requireInset(right, "safe area right");
            bottom = requireInset(bottom, "safe area bottom");
        }

        /** 无安全区域。 */
        public static SafeArea none() {
            return NONE;
        }

        /** 四边等值内缩。 */
        public static SafeArea of(double inset) {
            return new SafeArea(inset, inset, inset, inset);
        }

        /** 水平方向占用的逻辑像素。 */
        public double horizontal() {
            return left + right;
        }

        /** 垂直方向占用的逻辑像素。 */
        public double vertical() {
            return top + bottom;
        }

        private static double requireInset(double value, String name) {
            if (!Double.isFinite(value) || value < 0) {
                throw new IllegalArgumentException(name + " must be a finite non-negative logical-pixel value, got " + value);
            }
            return value;
        }
    }

    /**
     * host 布局能力（进入判定与装配校验的第三类输入）。
     *
     * <p>只登记真正被生产路径消费的能力——不预留没有读取者的枚举值。
     */
    public enum Capability {
        /**
         * host 视口由平台固定：窗口 resize 不会改变逻辑视口。
         *
         * <p><b>真实消费点</b>：{@code RootUiFacade.resize} 在装配点提交与固定视口矛盾的
         * 逻辑尺寸时显式失败（{@code INVALID_LAYOUT}），而不是静默接受一个永远不会生效的 resize。
         */
        FIXED_VIEWPORT("fixed-viewport");

        private final String code;

        Capability(String code) {
            this.code = code;
        }

        /** 稳定能力码。 */
        public String code() {
            return code;
        }
    }

    public ViewportContext {
        logicalWidth = requireLogical(logicalWidth, "logicalWidth");
        logicalHeight = requireLogical(logicalHeight, "logicalHeight");
        safeArea = safeArea == null ? SafeArea.none() : safeArea;
        if (guiScale < 1) {
            throw new IllegalArgumentException("guiScale must be >= 1, got " + guiScale);
        }
        capabilities = capabilities == null
                ? java.util.Set.of()
                : java.util.Set.copyOf(capabilities);
    }

    /** 逻辑视口（无安全区域、scale 1、默认能力）。 */
    public static ViewportContext of(double logicalWidth, double logicalHeight) {
        return new ViewportContext(logicalWidth, logicalHeight, SafeArea.none(), 1, java.util.Set.of());
    }

    /** 可用布局宽（逻辑视口宽 - 安全区域水平占用，下限 0）。 */
    public double contentWidth() {
        return Math.max(0, logicalWidth - safeArea.horizontal());
    }

    /** 可用布局高（逻辑视口高 - 安全区域垂直占用，下限 0）。 */
    public double contentHeight() {
        return Math.max(0, logicalHeight - safeArea.vertical());
    }

    /** 是否声明了某项 host 布局能力。 */
    public boolean has(Capability capability) {
        return capabilities.contains(capability);
    }

    /** 按「可用布局区 + 能力」判定 profile；本方法是 AC1 的唯一判定入口。 */
    public ViewportProfile profile() {
        return UiResponsiveRules.profileOf(this);
    }

    private static double requireLogical(double value, String name) {
        if (!Double.isFinite(value) || value < 0) {
            throw new UiFailure(UiDiagnosticStage.LAYOUT, UiFailure.INVALID_LAYOUT,
                    name + " must be a finite non-negative logical-pixel value, got " + value, null);
        }
        return value;
    }
}
