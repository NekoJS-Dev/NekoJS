package com.tkisor.nekojs.api.ui;

/**
 * JSX UI 的受控属性词汇表——属性名与受控取值种类的<b>唯一事实源</b>。
 *
 * <p>每个属性声明两件事：脚本侧名字（{@link #jsName()}）与取值种类（{@link #kind()}）。
 * 取值校验（{@link UiValueKind#validate}）与 TypeScript JSX 声明（{@link UiValueKind#tsType()}）
 * 都从同一行派生，因此新增属性不会在"登记表 / 校验器 / 声明生成器"三处漂移。
 *
 * <p>primitive 到允许属性集合的映射由引擎侧 primitive 登记表给出；两者共同构成 AC2 要求的
 * 唯一登记表。未登记的属性名一律显式失败。
 *
 * <p>事件属性使用 lowerCamelCase，事件值必须是脚本函数或明确的空值（jsx-ui-spec「props 规则」）。
 */
public enum UiProp {

    // ---- 内容 ----
    TEXT("text", UiPropCategory.CONTENT, UiValueKind.STRING),
    VALUE("value", UiPropCategory.CONTENT, UiValueKind.STRING),
    PLACEHOLDER("placeholder", UiPropCategory.CONTENT, UiValueKind.STRING),
    TITLE("title", UiPropCategory.CONTENT, UiValueKind.STRING),
    SOURCE("src", UiPropCategory.CONTENT, UiValueKind.RESOURCE),
    WRAP("wrap", UiPropCategory.CONTENT, UiValueKind.BOOLEAN),

    // ---- 布局 ----
    WIDTH("width", UiPropCategory.LAYOUT, UiValueKind.DIMENSION),
    HEIGHT("height", UiPropCategory.LAYOUT, UiValueKind.DIMENSION),
    MIN_WIDTH("minWidth", UiPropCategory.LAYOUT, UiValueKind.NUMBER),
    MIN_HEIGHT("minHeight", UiPropCategory.LAYOUT, UiValueKind.NUMBER),
    MAX_WIDTH("maxWidth", UiPropCategory.LAYOUT, UiValueKind.NUMBER),
    MAX_HEIGHT("maxHeight", UiPropCategory.LAYOUT, UiValueKind.NUMBER),
    MARGIN("margin", UiPropCategory.LAYOUT, UiValueKind.NUMBER),
    PADDING("padding", UiPropCategory.LAYOUT, UiValueKind.NUMBER),
    GAP("gap", UiPropCategory.LAYOUT, UiValueKind.NUMBER),
    ALIGN("align", UiPropCategory.LAYOUT, UiValueKind.ALIGNMENT),
    JUSTIFY("justify", UiPropCategory.LAYOUT, UiValueKind.ALIGNMENT),
    ANCHOR("anchor", UiPropCategory.LAYOUT, UiValueKind.ANCHOR),
    GROW("grow", UiPropCategory.LAYOUT, UiValueKind.NUMBER),
    VISIBLE("visible", UiPropCategory.LAYOUT, UiValueKind.BOOLEAN),
    /** 主轴方向覆盖；缺省继承 primitive（{@code row}/{@code column}），见 {@link UiAxis}。 */
    DIRECTION("direction", UiPropCategory.LAYOUT, UiValueKind.AXIS),
    /** Viewport Profile 覆盖包（票 43 AC3）；语义是声明式覆盖，不是另一个布局容器。 */
    PROFILES("profiles", UiPropCategory.LAYOUT, UiValueKind.PROFILE_OVERRIDES),

    // ---- 视觉 ----
    BACKGROUND("background", UiPropCategory.VISUAL, UiValueKind.STRING),
    BORDER("border", UiPropCategory.VISUAL, UiValueKind.STRING),
    COLOR("color", UiPropCategory.VISUAL, UiValueKind.STRING),
    RADIUS("radius", UiPropCategory.VISUAL, UiValueKind.NUMBER),
    OPACITY("opacity", UiPropCategory.VISUAL, UiValueKind.NUMBER),
    FONT("font", UiPropCategory.VISUAL, UiValueKind.STRING),
    FONT_SIZE("fontSize", UiPropCategory.VISUAL, UiValueKind.NUMBER),

    // ---- 可访问性 ----
    NARRATION("narration", UiPropCategory.ACCESSIBILITY, UiValueKind.STRING),
    TOOLTIP("tooltip", UiPropCategory.ACCESSIBILITY, UiValueKind.STRING),

    // ---- 状态 ----
    DISABLED("disabled", UiPropCategory.STATE, UiValueKind.BOOLEAN),
    FOCUSED("focused", UiPropCategory.STATE, UiValueKind.BOOLEAN),
    HOVERED("hovered", UiPropCategory.STATE, UiValueKind.BOOLEAN),
    PRESSED("pressed", UiPropCategory.STATE, UiValueKind.BOOLEAN),
    LOADING("loading", UiPropCategory.STATE, UiValueKind.BOOLEAN),
    ERROR("error", UiPropCategory.STATE, UiValueKind.STRING_OR_BOOLEAN),
    MAX_LENGTH("maxLength", UiPropCategory.STATE, UiValueKind.NON_NEGATIVE_NUMBER),

    // ---- 事件（lowerCamelCase，值为脚本函数或 null）----
    ON_CLICK("onClick", UiPropCategory.EVENT, UiValueKind.EVENT),
    ON_MOUSE_DOWN("onMouseDown", UiPropCategory.EVENT, UiValueKind.EVENT),
    ON_MOUSE_UP("onMouseUp", UiPropCategory.EVENT, UiValueKind.EVENT),
    ON_MOUSE_MOVE("onMouseMove", UiPropCategory.EVENT, UiValueKind.EVENT),
    ON_SCROLL("onScroll", UiPropCategory.EVENT, UiValueKind.EVENT),
    ON_KEY_DOWN("onKeyDown", UiPropCategory.EVENT, UiValueKind.EVENT),
    ON_KEY_UP("onKeyUp", UiPropCategory.EVENT, UiValueKind.EVENT),
    ON_CHAR("onChar", UiPropCategory.EVENT, UiValueKind.EVENT),
    ON_FOCUS("onFocus", UiPropCategory.EVENT, UiValueKind.EVENT),
    ON_BLUR("onBlur", UiPropCategory.EVENT, UiValueKind.EVENT),
    ON_SUBMIT("onSubmit", UiPropCategory.EVENT, UiValueKind.EVENT),
    ON_CLOSE("onClose", UiPropCategory.EVENT, UiValueKind.EVENT);

    private final String jsName;
    private final UiPropCategory category;
    private final UiValueKind kind;

    UiProp(String jsName, UiPropCategory category, UiValueKind kind) {
        this.jsName = jsName;
        this.category = category;
        this.kind = kind;
    }

    /** 脚本侧属性名。 */
    public String jsName() {
        return jsName;
    }

    /** 属性分类。 */
    public UiPropCategory category() {
        return category;
    }

    /** 受控取值种类（取值校验与 TS 声明类型的同源输入）。 */
    public UiValueKind kind() {
        return kind;
    }

    /** 是否是事件属性。 */
    public boolean isEvent() {
        return category == UiPropCategory.EVENT;
    }

    /** 按脚本侧属性名解析；未登记返回 null。 */
    public static UiProp fromJsName(String name) {
        if (name == null) return null;
        for (UiProp prop : values()) {
            if (prop.jsName.equals(name)) return prop;
        }
        return null;
    }
}
