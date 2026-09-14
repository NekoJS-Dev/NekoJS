package com.tkisor.nekojs.api.ui;

/** row/column 主轴与交叉轴对齐。 */
public enum UiAlignment {
    START("start"),
    CENTER("center"),
    END("end"),
    SPACE_BETWEEN("space-between"),
    SPACE_AROUND("space-around");

    private final String jsName;

    UiAlignment(String jsName) {
        this.jsName = jsName;
    }

    /** 脚本侧写法。 */
    public String jsName() {
        return jsName;
    }

    /** 解析；未登记写法显式抛错。 */
    public static UiAlignment parse(String raw) {
        if (raw == null) return null;
        for (UiAlignment alignment : values()) {
            if (alignment.jsName.equals(raw)) return alignment;
        }
        throw new IllegalArgumentException("unknown alignment: " + raw);
    }
}
