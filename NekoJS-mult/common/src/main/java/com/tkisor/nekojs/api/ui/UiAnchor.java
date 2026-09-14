package com.tkisor.nekojs.api.ui;

/** 节点在父节点中的锚定位置（受控九宫格，不实现任意 CSS 定位）。 */
public enum UiAnchor {
    TOP_LEFT("top-left"),
    TOP_CENTER("top-center"),
    TOP_RIGHT("top-right"),
    CENTER_LEFT("center-left"),
    CENTER("center"),
    CENTER_RIGHT("center-right"),
    BOTTOM_LEFT("bottom-left"),
    BOTTOM_CENTER("bottom-center"),
    BOTTOM_RIGHT("bottom-right");

    private final String jsName;

    UiAnchor(String jsName) {
        this.jsName = jsName;
    }

    /** 脚本侧写法。 */
    public String jsName() {
        return jsName;
    }

    /** 解析；未登记写法显式抛错。 */
    public static UiAnchor parse(String raw) {
        if (raw == null) return null;
        for (UiAnchor anchor : values()) {
            if (anchor.jsName.equals(raw)) return anchor;
        }
        throw new IllegalArgumentException("unknown anchor: " + raw);
    }
}
