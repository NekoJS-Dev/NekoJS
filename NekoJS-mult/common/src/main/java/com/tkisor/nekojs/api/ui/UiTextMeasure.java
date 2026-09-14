package com.tkisor.nekojs.api.ui;

import java.util.Objects;

/**
 * 文本测量输入（jsx-ui-spec「文本与资源」）：label 的文本测量、换行和字体访问由
 * Client UI Adapter 完成，common 只消费结果尺寸。
 *
 * @param text           待测文本
 * @param font           受控字体标识（{@code namespace:path}），可为 {@code null} 表示默认字体
 * @param fontSize       字号（逻辑像素）
 * @param wrap           是否允许换行
 * @param availableWidth 可用宽度（逻辑像素）
 */
public record UiTextMeasure(String text, String font, double fontSize, boolean wrap, double availableWidth) {

    public UiTextMeasure {
        Objects.requireNonNull(text, "text");
        if (!Double.isFinite(fontSize) || fontSize <= 0) {
            throw new IllegalArgumentException("fontSize must be a positive finite number");
        }
        if (!Double.isFinite(availableWidth) || availableWidth < 0) {
            throw new IllegalArgumentException("availableWidth must be a non-negative finite number");
        }
    }
}
