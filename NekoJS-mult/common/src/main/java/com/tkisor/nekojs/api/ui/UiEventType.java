package com.tkisor.nekojs.api.ui;

/**
 * 稳定脚本事件类型（jsx-ui-spec「输入与焦点」「键盘与 narration」）。
 *
 * <p>原生鼠标、滚轮、键盘与文本输入由 Client UI Adapter 转换成本枚举；
 * Minecraft 原生事件类身份不得进入 common contract。
 */
public enum UiEventType {
    CLICK("click"),
    MOUSE_DOWN("mouseDown"),
    MOUSE_UP("mouseUp"),
    MOUSE_MOVE("mouseMove"),
    SCROLL("scroll"),
    KEY_DOWN("keyDown"),
    KEY_UP("keyUp"),
    CHAR("char"),
    FOCUS("focus"),
    BLUR("blur"),
    SUBMIT("submit"),
    CLOSE("close");

    private final String jsName;

    UiEventType(String jsName) {
        this.jsName = jsName;
    }

    /** 脚本侧事件名（与事件 props 的 lowerCamelCase 一致）。 */
    public String jsName() {
        return jsName;
    }

    /** 该事件对应的受控事件属性；无对应属性返回 {@code null}。 */
    public UiProp prop() {
        return switch (this) {
            case CLICK -> UiProp.ON_CLICK;
            case MOUSE_DOWN -> UiProp.ON_MOUSE_DOWN;
            case MOUSE_UP -> UiProp.ON_MOUSE_UP;
            case MOUSE_MOVE -> UiProp.ON_MOUSE_MOVE;
            case SCROLL -> UiProp.ON_SCROLL;
            case KEY_DOWN -> UiProp.ON_KEY_DOWN;
            case KEY_UP -> UiProp.ON_KEY_UP;
            case CHAR -> UiProp.ON_CHAR;
            case FOCUS -> UiProp.ON_FOCUS;
            case BLUR -> UiProp.ON_BLUR;
            case SUBMIT -> UiProp.ON_SUBMIT;
            case CLOSE -> UiProp.ON_CLOSE;
        };
    }

    /** 按脚本侧事件名解析；未登记返回 {@code null}。 */
    public static UiEventType fromJsName(String name) {
        if (name == null) return null;
        for (UiEventType type : values()) {
            if (type.jsName.equals(name)) return type;
        }
        return null;
    }
}
