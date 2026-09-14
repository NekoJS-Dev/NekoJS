package com.tkisor.nekojs.api.ui;

/**
 * 初始 JSX UI host primitive 集合（ticket 40 / jsx-ui-spec「初始 host primitive 集合」）。
 *
 * <p>小写 JSX 标签只对应这里登记的 primitive：它们不对应 HTML 标签，也不允许脚本借助
 * 未知标签绕过登记。标签到允许属性集合的唯一登记表是
 * {@code com.tkisor.nekojs.core.ui.UiPrimitiveRegistry}——本枚举只给出标签词表，
 * 不构成第二份事实源。
 *
 * <p>{@code screen} 是 root 投影，只能出现在候选树根；其余 primitive 可作为任意
 * 已提交 host 节点的子节点。
 */
public enum UiPrimitive {
    SCREEN("screen"),
    PANEL("panel"),
    ROW("row"),
    COLUMN("column"),
    STACK("stack"),
    SCROLL("scroll"),
    LABEL("label"),
    BUTTON("button"),
    INPUT("input"),
    IMAGE("image"),
    SPACER("spacer");

    private final String tag;

    UiPrimitive(String tag) {
        this.tag = tag;
    }

    /** JSX 小写标签名（同时是 host capability 名）。 */
    public String tag() {
        return tag;
    }

    /** 是否是树根 primitive（只有 screen 允许作为候选树根）。 */
    public boolean isRoot() {
        return this == SCREEN;
    }

    /** 按标签解析；未知标签返回 {@code null}（由登记表显式失败，不静默回退）。 */
    public static UiPrimitive fromTag(String tag) {
        if (tag == null) return null;
        for (UiPrimitive primitive : values()) {
            if (primitive.tag.equals(tag)) return primitive;
        }
        return null;
    }
}
