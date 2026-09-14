package com.tkisor.nekojs.api.ui;

import java.util.List;

/**
 * JSX UI 公开契约的 capability id 常量。
 *
 * <p>这里是 capability id 的唯一常量源，由 {@code CoreManagedApiBootstrap} 写入
 * {@code NormativeApiContract.capabilities}，再经 {@code JsApiSurfaceResolver} /
 * manifest / Probe 派生——UI 不创建第二套 catalog 或独立声明事实源
 * （jsx-ui-spec「Managed Surface」）。
 */
public final class UiSpec {

    /**
     * UI capability id 清单。第一项是 common fake Adapter proof（本票 supported）；
     * 其余三项的完整实现由客户端平台 Adapter 承担，本票声明为 {@code PARTIAL}
     * 并在 docs 中写明差距，不得静默 no-op。
     */
    public static final List<String> CAPABILITY_IDS = List.of(
            "ui.fake-host-adapter",
            "ui.viewport-profiles",
            "ui.client-screen",
            "ui.native-input",
            "ui.text-measurement");

    /**
     * Viewport Profile 判定规则的规范文本（票 43 AC1）。
     *
     * <p>它不是第二份规则表——阈值本身由 {@link ViewportProfile} 单点持有，本文本只把
     * 「边界、优先级、tie-break 与可观察输出」写进公开 contract 的能力声明，供 inspector、
     * 文档与漂移测试读取。任何一处阈值漂移都会让契约能力描述与枚举不一致的漂移测试变红。
     */
    public static String viewportProfileRule() {
        return "NekoJS Viewport Profile 1-6（使用可用布局区 = 逻辑视口 - 安全区域，不按 GUI scale）："
                + "自宽到窄取首个两轴都满足的档位；宽高落在不同档时取更窄档（tie-break）。"
                + "阈值 " + ruleTable() + "；可观察输出为稳定档位码 "
                + String.join("/", ViewportProfile.codes()) + "。";
    }

    private static String ruleTable() {
        StringBuilder sb = new StringBuilder();
        for (ViewportProfile profile : ViewportProfile.values()) {
            if (sb.length() > 0) sb.append("，");
            sb.append(profile.code()).append(" >= ").append((long) profile.minContentWidth())
                    .append('x').append((long) profile.minContentHeight());
        }
        return sb.toString();
    }

    private UiSpec() {
    }
}
