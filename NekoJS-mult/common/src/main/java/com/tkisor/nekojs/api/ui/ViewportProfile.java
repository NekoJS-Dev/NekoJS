package com.tkisor.nekojs.api.ui;

/**
 * NekoJS Viewport Profile 1–6（jsx-ui-spec「响应式模型」，票 43 AC1）。
 *
 * <p>Profile 是<b>布局能力档位</b>，不是 Minecraft GUI scale 数字，也不是固定分辨率：
 * 判定输入只有实际逻辑视口宽高、常驻 chrome 占用的安全区域和 host 布局能力
 * （{@link ViewportContext}）。{@code profile} 与 GUI scale 没有映射关系，脚本不得用
 * {@code profile == scale} 推导布局；原生 scale 只能作为 {@link ViewportContext} 的输入之一。
 *
 * <p>六档按「可用布局空间」自宽到窄排列，{@link #code()} 是对外可观察输出：
 * <ol>
 *   <li>{@link #DESKTOP_WIDE}：宽屏桌面，可用宽 ≥ 640 逻辑像素。</li>
 *   <li>{@link #DESKTOP}：桌面窗口，可用宽 ≥ 480。</li>
 *   <li>{@link #TABLET}：中等窗口，可用宽 ≥ 360。</li>
 *   <li>{@link #HANDHELD}：窄窗口，可用宽 ≥ 260。</li>
 *   <li>{@link #COMPACT}：紧凑布局，可用宽 ≥ 180。</li>
 *   <li>{@link #MINIMAL}：可用布局能力不足，低于 {@link #COMPACT} 的阈值或高/宽都过小。</li>
 * </ol>
 *
 * <p>判定是<b>保守</b>的：可用宽与可用高都必须达到该档阈值。当宽度落在高档、高度只够低档时，
 * 取<b>更窄</b>的档位（更高的编号）。这就是 profile 判定固定下来的 tie-break——宽高不一致时
 * 不回退到宽档，保证同样的逻辑视口在任何装配顺序下得到同一个 profile。
 */
public enum ViewportProfile {

    /** 档位 1：宽屏桌面。 */
    DESKTOP_WIDE(1, "desktop-wide", 640.0, 360.0),
    /** 档位 2：桌面窗口。 */
    DESKTOP(2, "desktop", 480.0, 300.0),
    /** 档位 3：中等窗口 / 平板。 */
    TABLET(3, "tablet", 360.0, 240.0),
    /** 档位 4：窄窗口 / 手持。 */
    HANDHELD(4, "handheld", 260.0, 180.0),
    /** 档位 5：紧凑布局。 */
    COMPACT(5, "compact", 180.0, 120.0),
    /** 档位 6：可用布局能力不足。 */
    MINIMAL(6, "minimal", 0.0, 0.0);

    /** 档位数（1–6 判定规则的一部分，禁止改成其它基数）。 */
    public static final int COUNT = 6;

    private final int number;
    private final String code;
    private final double minContentWidth;
    private final double minContentHeight;

    ViewportProfile(int number, String code, double minContentWidth, double minContentHeight) {
        this.number = number;
        this.code = code;
        this.minContentWidth = minContentWidth;
        this.minContentHeight = minContentHeight;
    }

    /** 档位编号 1–6（稳定可观察输出）。 */
    public int number() {
        return number;
    }

    /** 稳定档位码（诊断、inspector 与 golden 的事实源）。 */
    public String code() {
        return code;
    }

    /** 进入本档所需的可用布局宽（安全区域之外）。 */
    public double minContentWidth() {
        return minContentWidth;
    }

    /** 进入本档所需的可用布局高（安全区域之外）。 */
    public double minContentHeight() {
        return minContentHeight;
    }

    /** 更宽的相邻档位；已是 1 档返回空。 */
    public java.util.Optional<ViewportProfile> wider() {
        return number <= 1 ? java.util.Optional.empty() : java.util.Optional.of(byNumber(number - 1));
    }

    /** 更窄的相邻档位；已是 6 档返回空。 */
    public java.util.Optional<ViewportProfile> narrower() {
        return number >= COUNT ? java.util.Optional.empty() : java.util.Optional.of(byNumber(number + 1));
    }

    /** 按档位编号解析；未登记编号抛 {@link IllegalArgumentException}（非法 profile 显式失败）。 */
    public static ViewportProfile byNumber(int number) {
        for (ViewportProfile profile : values()) {
            if (profile.number == number) return profile;
        }
        throw new IllegalArgumentException("viewport profile number must be in [1, " + COUNT + "], got " + number);
    }

    /** 按稳定档位码解析（脚本侧覆盖键、inspector 输出与 golden 用它）；未登记返回 null。 */
    public static ViewportProfile fromCode(String code) {
        if (code == null) return null;
        for (ViewportProfile profile : values()) {
            if (profile.code.equals(code)) return profile;
        }
        return null;
    }

    /** 全部稳定档位码（1..6 顺序，供声明与漂移测试使用）。 */
    public static java.util.List<String> codes() {
        java.util.List<String> codes = new java.util.ArrayList<>(COUNT);
        for (ViewportProfile profile : values()) {
            codes.add(profile.code);
        }
        return java.util.List.copyOf(codes);
    }
}
