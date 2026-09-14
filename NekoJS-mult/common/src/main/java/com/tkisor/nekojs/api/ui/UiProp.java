package com.tkisor.nekojs.api.ui;

/**
 * JSX UI 的受控属性词汇表。
 *
 * <p>属性名唯一事实源：本枚举的 {@link #jsName()}。primitive 到允许属性集合的映射由
 * {@code com.tkisor.nekojs.core.ui.UiPrimitiveRegistry} 给出；两者共同构成 AC2 要求的
 * 「唯一登记表」。未登记的属性名、未登记的事件名一律显式失败。
 *
 * <p>事件属性使用 lowerCamelCase，事件值必须是脚本函数或明确的空值
 * （jsx-ui-spec「props 规则」）。
 */
@com.tkisor.nekojs.api.annotation.ContractReceiver("UiProp")
public enum UiProp {
    // ---- 内容 ----
    TEXT("text", UiPropCategory.CONTENT),
    VALUE("value", UiPropCategory.CONTENT),
    PLACEHOLDER("placeholder", UiPropCategory.CONTENT),
    TITLE("title", UiPropCategory.CONTENT),
    SOURCE("src", UiPropCategory.CONTENT),
    FIT("fit", UiPropCategory.CONTENT),
    WRAP("wrap", UiPropCategory.CONTENT),

    // ---- 布局 ----
    WIDTH("width", UiPropCategory.LAYOUT),
    HEIGHT("height", UiPropCategory.LAYOUT),
    MIN_WIDTH("minWidth", UiPropCategory.LAYOUT),
    MIN_HEIGHT("minHeight", UiPropCategory.LAYOUT),
    MAX_WIDTH("maxWidth", UiPropCategory.LAYOUT),
    MAX_HEIGHT("maxHeight", UiPropCategory.LAYOUT),
    MARGIN("margin", UiPropCategory.LAYOUT),
    PADDING("padding", UiPropCategory.LAYOUT),
    GAP("gap", UiPropCategory.LAYOUT),
    ALIGN("align", UiPropCategory.LAYOUT),
    JUSTIFY("justify", UiPropCategory.LAYOUT),
    ANCHOR("anchor", UiPropCategory.LAYOUT),
    GROW("grow", UiPropCategory.LAYOUT),
    VISIBLE("visible", UiPropCategory.LAYOUT),
    SCROLL_X("scrollX", UiPropCategory.LAYOUT),
    SCROLL_Y("scrollY", UiPropCategory.LAYOUT),
    SCROLLABLE("scrollable", UiPropCategory.LAYOUT),

    // ---- 视觉 ----
    BACKGROUND("background", UiPropCategory.VISUAL),
    BORDER("border", UiPropCategory.VISUAL),
    COLOR("color", UiPropCategory.VISUAL),
    RADIUS("radius", UiPropCategory.VISUAL),
    OPACITY("opacity", UiPropCategory.VISUAL),
    FONT("font", UiPropCategory.VISUAL),
    FONT_SIZE("fontSize", UiPropCategory.VISUAL),

    // ---- 可访问性 ----
    NARRATION("narration", UiPropCategory.ACCESSIBILITY),
    TOOLTIP("tooltip", UiPropCategory.ACCESSIBILITY),

    // ---- 状态 ----
    DISABLED("disabled", UiPropCategory.STATE),
    FOCUSED("focused", UiPropCategory.STATE),
    HOVERED("hovered", UiPropCategory.STATE),
    PRESSED("pressed", UiPropCategory.STATE),
    LOADING("loading", UiPropCategory.STATE),
    ERROR("error", UiPropCategory.STATE),
    MAX_LENGTH("maxLength", UiPropCategory.STATE),

    // ---- 事件（lowerCamelCase，值为脚本函数或 null）----
    ON_CLICK("onClick", UiPropCategory.EVENT),
    ON_MOUSE_DOWN("onMouseDown", UiPropCategory.EVENT),
    ON_MOUSE_UP("onMouseUp", UiPropCategory.EVENT),
    ON_MOUSE_MOVE("onMouseMove", UiPropCategory.EVENT),
    ON_SCROLL("onScroll", UiPropCategory.EVENT),
    ON_KEY_DOWN("onKeyDown", UiPropCategory.EVENT),
    ON_KEY_UP("onKeyUp", UiPropCategory.EVENT),
    ON_CHAR("onChar", UiPropCategory.EVENT),
    ON_FOCUS("onFocus", UiPropCategory.EVENT),
    ON_BLUR("onBlur", UiPropCategory.EVENT),
    ON_SUBMIT("onSubmit", UiPropCategory.EVENT),
    ON_CLOSE("onClose", UiPropCategory.EVENT);

    private final String jsName;
    private final UiPropCategory category;

    UiProp(String jsName, UiPropCategory category) {
        this.jsName = jsName;
        this.category = category;
    }

    /** 脚本侧属性名。 */
    public String jsName() {
        return jsName;
    }

    /** 属性分类。 */
    public UiPropCategory category() {
        return category;
    }

    /** 是否是事件属性。 */
    public boolean isEvent() {
        return category == UiPropCategory.EVENT;
    }

    /** 是否是布局属性。 */
    public boolean isLayout() {
        return category == UiPropCategory.LAYOUT;
    }

    /** 按脚本侧属性名解析；未登记返回 {@code null}。 */
    public static UiProp fromJsName(String name) {
        if (name == null) return null;
        for (UiProp prop : values()) {
            if (prop.jsName.equals(name)) return prop;
        }
        return null;
    }
}