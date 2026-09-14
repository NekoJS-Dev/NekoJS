package com.tkisor.nekojs.api.ui;

/**
 * 节点的溢出诊断（jsx-ui-spec「转换产物可检查」，票 43 AC5）。
 *
 * <p>只报告 measure/arrange 已经得到的数值：节点的最终矩形超出其容器可用布局区的量。
 * 溢出<b>不</b>被静默夹取——布局按 min/max 与容器约束裁剪，剩下的差额在这里如实报出，
 * 供 inspector 与 AI 迭代定位。
 *
 * @param horizontal 水平方向超出量（逻辑像素，非负）
 * @param vertical   垂直方向超出量（逻辑像素，非负）
 * @param area       溢出面积（逻辑像素²，非负；两轴都无溢出时为 0）
 */
public record UiOverflow(double horizontal, double vertical, double area) {

    /** 无溢出。 */
    public static final UiOverflow NONE = new UiOverflow(0, 0, 0);

    public UiOverflow {
        if (!Double.isFinite(horizontal) || !Double.isFinite(vertical) || !Double.isFinite(area)
                || horizontal < 0 || vertical < 0 || area < 0) {
            throw new IllegalArgumentException("overflow fields must be finite and non-negative");
        }
    }

    /** 由两个方向的超出量构造。 */
    public static UiOverflow of(double horizontal, double vertical) {
        double h = Math.max(0, horizontal);
        double v = Math.max(0, vertical);
        return h == 0 && v == 0 ? NONE : new UiOverflow(h, v, h * v);
    }

    /** 是否任一方向溢出。 */
    public boolean any() {
        return horizontal > 0 || vertical > 0;
    }
}
