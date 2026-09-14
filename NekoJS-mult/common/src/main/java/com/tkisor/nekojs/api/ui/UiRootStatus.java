package com.tkisor.nekojs.api.ui;

/** UI root 生命周期状态（jsx-ui-spec「公开模型」「generation 生命周期」）。 */
public enum UiRootStatus {
    /** 已创建、尚未提交首次树。 */
    CREATED("created"),
    /** 已有可显示的有效树。 */
    OPEN("open"),
    /** 已失效：不再接收事件，但保留最后一次有效树供显示。 */
    INVALIDATED("invalidated"),
    /** 已释放：所有宿主节点、事件句柄与订阅已清理。 */
    DISPOSED("disposed");

    private final String code;

    UiRootStatus(String code) {
        this.code = code;
    }

    /** 稳定状态码。 */
    public String code() {
        return code;
    }
}
