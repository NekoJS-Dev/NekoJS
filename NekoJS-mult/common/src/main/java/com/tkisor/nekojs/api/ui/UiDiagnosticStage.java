package com.tkisor.nekojs.api.ui;

/**
 * UI 失败阶段（jsx-ui-spec「提交语义」「异常处理」）：错误必须可区分归属阶段，
 * 以便错误面板定位问题（AC5）。
 */
public enum UiDiagnosticStage {
    /** 组件 render function 执行失败。 */
    RENDER("render"),
    /** 函数组件展开失败（组件自身抛错或返回非法值）。 */
    COMPONENT("component"),
    /** 布局 measure/arrange 失败。 */
    LAYOUT("layout"),
    /** 事件回调执行失败。 */
    EVENT("event"),
    /** host Adapter 操作失败（create/update/reorder/dispose/paint）。 */
    HOST_UPDATE("host-update");

    private final String code;

    UiDiagnosticStage(String code) {
        this.code = code;
    }

    /** 稳定阶段码（诊断字段，跨平台一致）。 */
    public String code() {
        return code;
    }
}
