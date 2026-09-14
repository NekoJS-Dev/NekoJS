package com.tkisor.nekojs.api.ui;

/**
 * 文本测量边界（jsx-ui-spec「文本与资源」）：label 的文本测量、换行和字体访问由
 * Client UI Adapter 完成，common 只消费结果尺寸。
 */
@FunctionalInterface
public interface UiTextMeasurer {

    /** 测量文本在给定字体规格下的逻辑像素尺寸。 */
    UiSize measure(UiTextMeasure request);
}
