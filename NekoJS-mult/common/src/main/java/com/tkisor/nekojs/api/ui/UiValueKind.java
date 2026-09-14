package com.tkisor.nekojs.api.ui;

import java.util.Objects;

/**
 * 受控 props 取值种类——AC2 取值校验与 AC6 TypeScript 声明的<b>单一事实源</b>。
 *
 * <p>属性名与取值种类都挂在 {@link UiProp} 上；本枚举提供两件派生能力：
 * <ul>
 *   <li>{@link #validate} —— runtime 候选校验（非法形状显式失败）；</li>
 *   <li>{@link #tsType} —— TypeScript JSX 声明的属性类型文本。</li>
 * </ul>
 * 因此新增一个属性只需在 {@code UiProp} 上加一行，校验器与声明生成器不会各自漂移。
 */
public enum UiValueKind {

    /** 文本/标识字符串。 */
    STRING("string"),
    /** 布尔开关。 */
    BOOLEAN("boolean"),
    /** 有限数值。 */
    NUMBER("number"),
    /** 非负数值（如 maxLength）。 */
    NON_NEGATIVE_NUMBER("number"),
    /** 单轴尺寸：逻辑像素 / auto / fill / 受控百分比（别名由声明生成器定义）。 */
    DIMENSION("NekoJsx.UiDimensionInput"),
    /** 主轴或交叉轴对齐。 */
    ALIGNMENT("NekoJsx.UiAlignment"),
    /** 九宫格锚定。 */
    ANCHOR("NekoJsx.UiAnchor"),
    /** 主轴方向覆盖（{@code row} / {@code column}）；缺省继承 primitive。 */
    AXIS("NekoJsx.UiAxis"),
    /**
     * Viewport Profile 覆盖包：1–6 档的受控属性覆盖，键为档位编号或档位码。
     *
     * <p>类型文本用内联 mapped type，其属性列表由 {@link UiProfileOverride#KEYS} 派生
     * （见 {@code UiJsxDeclarationGenerator}），因此不会引用不存在的 NekoJsx 类型名。
     */
    PROFILE_OVERRIDES("{ readonly [profile: string]: NekoJsx.UiProfileOverride }"),
    /** 字符串或布尔（error 既可是消息也可是标记）。 */
    STRING_OR_BOOLEAN("string | boolean"),
    /** 受控资源标识（namespace:path），不接受 URL 或绝对路径。 */
    RESOURCE("string"),
    /** 事件回调：脚本函数或显式 null（解绑）。 */
    EVENT("(event: NekoJsx.UiEvent) => void | null");

    private final String tsType;

    UiValueKind(String tsType) {
        this.tsType = tsType;
    }

    /** TypeScript 声明的属性类型文本。 */
    public String tsType() {
        return tsType;
    }

    /** 显式 null 是否是该种类的合法取值（只有事件属性允许：表示解绑）。 */
    public boolean acceptsNull() {
        return this == EVENT;
    }

    /**
     * 校验脚本侧取值；非法形状显式失败（不静默回退）。
     *
     * @param prop         属性
     * @param primitiveTag 所属 primitive 标签（诊断）
     * @param raw          原始取值；null 已在调用方按 {@link #acceptsNull()} 处理
     * @param path         节点路径（诊断）
     */
    public void validate(UiProp prop, String primitiveTag, Object raw, String path) {
        Objects.requireNonNull(prop, "prop");
        switch (this) {
            case STRING -> {
                if (raw instanceof String) return;
                fail(prop, primitiveTag, raw, path, "must be a string");
            }
            case BOOLEAN -> {
                if (raw instanceof Boolean) return;
                fail(prop, primitiveTag, raw, path, "must be a boolean");
            }
            case NUMBER -> {
                if (raw instanceof Number number && Double.isFinite(number.doubleValue())) return;
                fail(prop, primitiveTag, raw, path, "must be a finite number");
            }
            case NON_NEGATIVE_NUMBER -> {
                if (raw instanceof Number number && number.doubleValue() >= 0) return;
                fail(prop, primitiveTag, raw, path, "must be a non-negative number");
            }
            case DIMENSION -> {
                if (raw instanceof Number) return;
                if (raw instanceof String text) {
                    try {
                        UiDimension.parse(text);
                        return;
                    } catch (IllegalArgumentException failure) {
                        fail(prop, primitiveTag, raw, path, "must be a logical-pixel size, auto or fill");
                    }
                }
                fail(prop, primitiveTag, raw, path, "must be a size value");
            }
            case ALIGNMENT -> {
                if (raw instanceof String text) {
                    try {
                        UiAlignment.parse(text);
                        return;
                    } catch (IllegalArgumentException failure) {
                        fail(prop, primitiveTag, raw, path, "must be a registered alignment");
                    }
                }
                fail(prop, primitiveTag, raw, path, "must be an alignment string");
            }
            case ANCHOR -> {
                if (raw instanceof String text) {
                    try {
                        UiAnchor.parse(text);
                        return;
                    } catch (IllegalArgumentException failure) {
                        fail(prop, primitiveTag, raw, path, "must be a registered anchor");
                    }
                }
                fail(prop, primitiveTag, raw, path, "must be an anchor string");
            }
            case AXIS -> {
                if (raw instanceof String text && UiAxis.fromCode(text) != null) return;
                fail(prop, primitiveTag, raw, path, "must be one of row|column");
            }
            case PROFILE_OVERRIDES -> {
                try {
                    UiProfileOverride.parseAll(raw, path);
                    return;
                } catch (UiFailure failure) {
                    throw failure;
                } catch (RuntimeException failure) {
                    fail(prop, primitiveTag, raw, path, "must map viewport profile 1-6 to "
                            + "layout overrides");
                }
            }
            case STRING_OR_BOOLEAN -> {
                if (raw instanceof String || raw instanceof Boolean) return;
                fail(prop, primitiveTag, raw, path, "must be a string or boolean");
            }
            case RESOURCE -> {
                if (raw instanceof String text) {
                    UiProps.requireResourceId(text, path);
                    return;
                }
                fail(prop, primitiveTag, raw, path, "must be a namespace:path resource id");
            }
            case EVENT -> {
                // 脚本函数以 opaque 句柄到达；字符串/数字/布尔是明确非法的事件值。
                if (raw instanceof String || raw instanceof Number || raw instanceof Boolean) {
                    fail(prop, primitiveTag, raw, path, "event value must be a function or null");
                }
            }
        }
    }

    private static void fail(UiProp prop, String primitiveTag, Object raw, String path, String requirement) {
        throw new UiFailure(UiDiagnosticStage.COMPONENT, UiFailure.INVALID_PROP,
                "prop " + prop.jsName() + " on <" + primitiveTag + "> " + requirement
                        + ", got " + raw.getClass().getName(), path);
    }
}
