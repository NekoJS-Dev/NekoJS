package com.tkisor.nekojs.api.ui;

import java.util.List;

/**
 * JSX UI 公开契约的跨切面常量：capability id、事件组名与合法取值集合。
 *
 * <p>capability id 在这里是唯一常量源，由 {@code CoreManagedApiBootstrap} 写入
 * {@code NormativeApiContract.capabilities}，再经 {@code JsApiSurfaceResolver} /
 * manifest / Probe 派生——UI 不创建第二套 catalog 或独立声明事实源
 * （jsx-ui-spec「Managed Surface」）。
 */
public final class UiSpec {

    /** 事件组名（UI 事件对象进入的既有 CLIENT 诊断/事件词汇）。 */
    public static final String EVENT_GROUP = "UiEvents";

    /** UI facade 全局绑定名（managed contract {@code global:Ui}）。 */
    public static final String FACADE_BINDING = "Ui";

    /** controlled-resource 值的契约名。 */
    public static final String RESOURCE_TYPE = "UiResource";

    /** 事件句柄的契约名。 */
    public static final String EVENT_HANDLER_TYPE = "UiEventHandler";

    /**
     * UI capability id 清单。第一项是 common fake Adapter proof（本票即可 supported）；
     * 其余三项按节点/Adapter 完成后才可标 supported——本票一律声明为 {@code PARTIAL}
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
