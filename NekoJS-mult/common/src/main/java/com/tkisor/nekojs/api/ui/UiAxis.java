package com.tkisor.nekojs.api.ui;

/**
 * 容器主轴方向（jsx-ui-spec「响应式重排」，票 43 AC3）。
 *
 * <p>它是布局策略，不是另一个 primitive：{@code row} / {@code column} primitive 各自给出缺省
 * 方向，profile 覆盖可以把它切到另一轴（窄屏把 row 重排成 column），不需要脚本为六个 profile
 * 重复填写整套属性，也不需要复制节点。
 *
 * <p>没有 profile 覆盖时方向为 {@code null}（继承 primitive 缺省），此时"继承"是显式语义，
 * 不等同于任何一个具体方向。
 */
public enum UiAxis {

    /** 主轴水平。 */
    ROW("row"),
    /** 主轴垂直。 */
    COLUMN("column");

    private final String code;

    UiAxis(String code) {
        this.code = code;
    }

    /** 稳定方向码（脚本侧写法、inspector 输出与 golden 用它）。 */
    public String code() {
        return code;
    }

    /** 解析方向码；未登记返回 {@code null}（非法值由解析层显式失败，不静默回退）。 */
    public static UiAxis fromCode(String code) {
        if (code == null) return null;
        for (UiAxis axis : values()) {
            if (axis.code.equals(code)) return axis;
        }
        return null;
    }

    /** 全部稳定方向码。 */
    public static java.util.List<String> codes() {
        java.util.List<String> codes = new java.util.ArrayList<>(2);
        for (UiAxis axis : values()) {
            codes.add(axis.code);
        }
        return java.util.List.copyOf(codes);
    }
}
