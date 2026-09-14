package com.tkisor.nekojs.api.ui;

import java.util.Objects;

/**
 * 稳定脚本事件对象（jsx-ui-spec User Story 25）：只承载基本量、受控资源标识与节点路径，
 * <b>不承载</b> {@code GuiGraphics}、原生 {@code Screen} 或版本特有的输入对象。
 *
 * @param type            事件类型
 * @param mouseX          鼠标 x（逻辑像素；非鼠标事件为 0）
 * @param mouseY          鼠标 y（逻辑像素；非鼠标事件为 0）
 * @param button          鼠标按键；非鼠标事件为 -1
 * @param scrollDelta     滚轮增量；非滚轮事件为 0
 * @param keyCode         键码；非键盘事件为 -1
 * @param key             键名；非键盘事件为 {@code null}
 * @param character       文本输入字符；非文本事件为 {@code null}
 * @param shift           是否按下 Shift
 * @param control         是否按下 Control
 * @param alt             是否按下 Alt
 * @param targetPath      命中节点的稳定路径
 * @param rootId          UI root 标识
 * @param generation      UI root 绑定的 CLIENT generation
 * @param timestampMillis 事件时间戳（毫秒）
 */
@com.tkisor.nekojs.api.annotation.ContractReceiver("UiEvent")
public record UiEvent(
        UiEventType type,
        double mouseX,
        double mouseY,
        int button,
        double scrollDelta,
        int keyCode,
        String key,
        String character,
        boolean shift,
        boolean control,
        boolean alt,
        String targetPath,
        String rootId,
        int generation,
        double timestampMillis
) {

    public UiEvent {
        Objects.requireNonNull(type, "type");
    }

    /** 鼠标事件（click / mouseDown / mouseUp / mouseMove）。 */
    public static UiEvent mouse(UiEventType type, double x, double y, int button) {
        return new UiEvent(type, x, y, button, 0, -1, null, null,
                false, false, false, null, null, -1, 0);
    }

    /** 滚轮事件。 */
    public static UiEvent scroll(double x, double y, double delta) {
        return new UiEvent(UiEventType.SCROLL, x, y, -1, delta, -1, null, null,
                false, false, false, null, null, -1, 0);
    }

    /** 键盘事件。 */
    public static UiEvent key(UiEventType type, int keyCode, String key, boolean shift, boolean control, boolean alt) {
        return new UiEvent(type, 0, 0, -1, 0, keyCode, key, null,
                shift, control, alt, null, null, -1, 0);
    }

    /** 文本输入事件。 */
    public static UiEvent character(String character) {
        return new UiEvent(UiEventType.CHAR, 0, 0, -1, 0, -1, null,
                Objects.requireNonNull(character, "character"), false, false, false, null, null, -1, 0);
    }

    /** 无 payload 的聚焦/关闭类事件。 */
    public static UiEvent plain(UiEventType type) {
        return new UiEvent(type, 0, 0, -1, 0, -1, null, null,
                false, false, false, null, null, -1, 0);
    }

    /** 补全命中节点路径、root 与 generation（分发时由 root 写入）。 */
    public UiEvent withTarget(String targetPath, String rootId, int generation) {
        return new UiEvent(type, mouseX, mouseY, button, scrollDelta, keyCode, key, character,
                shift, control, alt, targetPath, rootId, generation, timestampMillis);
    }

    /** 是否携带鼠标位置。 */
    public boolean hasPointer() {
        return type == UiEventType.CLICK || type == UiEventType.MOUSE_DOWN
                || type == UiEventType.MOUSE_UP || type == UiEventType.MOUSE_MOVE
                || type == UiEventType.SCROLL;
    }
}
