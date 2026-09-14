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
            "ui.client-screen",
            "ui.native-input",
            "ui.text-measurement");

    private UiSpec() {
    }
}
