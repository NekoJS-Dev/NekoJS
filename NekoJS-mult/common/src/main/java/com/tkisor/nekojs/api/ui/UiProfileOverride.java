package com.tkisor.nekojs.api.ui;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 单档 Viewport Profile 的受控覆盖包（jsx-ui-spec「响应式模型」，票 43 AC2/AC3）。
 *
 * <p>脚本在节点上写一个 {@code profiles} 属性，把 1–6 档各自的覆盖放在一起——不必为每个
 * 属性重复六遍，也不必复制节点。每个覆盖值只描述「这一档相对基础值怎么变」：
 *
 * <pre>
 *   &lt;scroll gap={4} profiles={{ 1: { gap: 12, direction: 'row' },
 *                                 4: { gap: 2, scrollY: 40, visible: false } }}&gt;
 * </pre>
 *
 * <p><b>唯一事实源</b>：受控覆盖键只由 {@link #KEYS} 声明一次；解析（类型校验）与
 * {@link #applyTo} 都按该表驱动——新增一个键只需在 {@link #KEYS} 加一项并补一条取值校验，
 * 不存在「键表 / 解析 switch / 应用 switch」三份平行副本（票 40 的教训）。
 *
 * <p>解析顺序（固定，AC2）：
 * <ol>
 *   <li>基础值 = 节点上的普通属性（没有覆盖时原样使用）；</li>
 *   <li>覆盖值 = 本档的覆盖；缺省回退见 {@link #forProfile}；</li>
 *   <li>解析结果再进入 {@link UiLayoutSpec} 的取值校验与 min/max 裁剪。</li>
 * </ol>
 *
 * <p>未知键、非法档位、非法取值一律显式抛 {@link UiFailure}（{@code INVALID_LAYOUT}），
 * 不静默忽略、不静默夹取。覆盖键是否适用于某个 primitive 由
 * {@code UiPrimitiveRegistry} 在候选择阶段一并校验。
 */
public record UiProfileOverride(Map<String, Object> values) {

    /** 受控覆盖键（唯一键表；1..6 档共用同一份词表）。 */
    public static final List<String> KEYS = List.of(
            "align", "anchor", "direction", "font", "fontSize", "gap", "height", "justify",
            "margin", "maxHeight", "maxWidth", "minHeight", "minWidth", "padding", "scrollX",
            "scrollY", "visible", "width", "wrap");

    /** 数值型键（逻辑像素或权重；全部要求有限的非负数）。 */
    private static final List<String> NUMERIC_KEYS = List.of(
            "fontSize", "gap", "margin", "maxHeight", "maxWidth", "minHeight", "minWidth",
            "padding", "scrollX", "scrollY");

    /** 单轴尺寸键（接受 auto/fill/百分比/逻辑像素/设计坐标）。 */
    private static final List<String> DIMENSION_KEYS = List.of("width", "height");

    /** 布尔键。 */
    private static final List<String> BOOLEAN_KEYS = List.of("visible", "wrap");

    /** 文本规格键（字体、字号、换行）。 */
    public static final List<String> TEXT_KEYS = List.of("font", "fontSize", "wrap");

    /** 滚动策略键。 */
    public static final List<String> SCROLL_KEYS = List.of("scrollX", "scrollY");

    /** 空覆盖（不改变任何属性）。 */
    public static final UiProfileOverride NONE = new UiProfileOverride(Map.of());

    public UiProfileOverride {
        values = values == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(values));
    }

    // ---- 类型化访问器（都从同一份 values 派生）----

    public UiDimension width() { return dimension("width"); }
    public UiDimension height() { return dimension("height"); }
    public Double minWidth() { return number("minWidth"); }
    public Double minHeight() { return number("minHeight"); }
    public Double maxWidth() { return number("maxWidth"); }
    public Double maxHeight() { return number("maxHeight"); }
    public Double margin() { return number("margin"); }
    public Double padding() { return number("padding"); }
    public Double gap() { return number("gap"); }
    public Double fontSize() { return number("fontSize"); }
    public Double scrollX() { return number("scrollX"); }
    public Double scrollY() { return number("scrollY"); }
    public UiAlignment align() { return alignment("align"); }
    public UiAlignment justify() { return alignment("justify"); }
    public UiAnchor anchor() { return anchorValue("anchor"); }
    public UiAxis direction() { return axis("direction"); }
    public Boolean visible() { return bool("visible"); }
    public Boolean wrap() { return bool("wrap"); }
    public String font() { return text("font"); }

    /** 本覆盖是否声明了任何文本规格键（字号/字体/换行）。 */
    public boolean hasTextSpec() {
        return values.keySet().stream().anyMatch(TEXT_KEYS::contains);
    }

    /** 本覆盖是否声明了任何滚动策略键。 */
    public boolean hasScrollSpec() {
        return values.keySet().stream().anyMatch(SCROLL_KEYS::contains);
    }

    /**
     * 解析节点上的 {@code profiles} 属性（AC2）。
     *
     * <p>接受的形状：
     * <ul>
     *   <li>映射：档位编号（{@code "1"}..{@code "6"}）或档位码（{@code "desktop"} 等）→ 覆盖对象；</li>
     *   <li>数组：按顺序合并的若干映射（后面的覆盖前面的）。</li>
     * </ul>
     *
     * @param raw  原始取值，可为 {@code null}（无覆盖）
     * @param path 节点路径（诊断）
     * @throws UiFailure 非法档位、非法键或非法取值
     */
    public static Map<ViewportProfile, UiProfileOverride> parseAll(Object raw, String path) {
        Map<ViewportProfile, UiProfileOverride> result = new LinkedHashMap<>();
        if (raw == null) {
            return result;
        }
        if (raw instanceof Map<?, ?> map) {
            mergeInto(result, map, path);
            return result;
        }
        if (raw instanceof Iterable<?> iterable) {
            for (Object element : iterable) {
                if (!(element instanceof Map<?, ?> map)) {
                    throw invalid(path, "profiles entries must be objects, got " + describe(element));
                }
                mergeInto(result, map, path);
            }
            return result;
        }
        throw invalid(path, "profiles must be an object keyed by profile 1-6, got " + describe(raw));
    }

    /**
     * 本档的覆盖值合并到基础属性上（键表驱动；只写覆盖中显式出现的键）。
     *
     * <p>枚举取值统一还原为脚本侧字面量（{@code align}/{@code anchor}/{@code direction}），
     * 与基础 props 的表示保持一致，因此下游解析只有一条路径。
     */
    public Map<String, Object> applyTo(Map<String, Object> base) {
        Map<String, Object> merged = new LinkedHashMap<>(base);
        for (Map.Entry<String, Object> entry : values.entrySet()) {
            Object value = entry.getValue();
            if (value instanceof UiAlignment alignment) {
                merged.put(entry.getKey(), alignment.jsName());
            } else if (value instanceof UiAnchor anchor) {
                merged.put(entry.getKey(), anchor.jsName());
            } else if (value instanceof UiAxis axis) {
                merged.put(entry.getKey(), axis.code());
            } else {
                merged.put(entry.getKey(), value);
            }
        }
        return merged;
    }

    /**
     * 本节点在给定档位下生效的覆盖（AC2 的「缺省回退」规则，固定且可观察）。
     *
     * <p>规则：<b>取编号小于等于当前档位的最大已声明档</b>——一条声明从它声明的档位开始，
     * 向更窄方向持续生效，直到被更窄档的新声明覆盖。基础值是<b>窄屏缺省</b>，作者只在需要
     * 改变的断点上写「从这一档起改成什么」。
     *
     * <p><b>不援引 CSS 类比</b>：CSS {@code min-width} 媒体查询是「宽度大于等于该值时生效」，
     * 方向是向上的；本契约是「从声明的档位起向更窄方向生效」，方向相反。本条语义由本票在
     * contract 中定义，理由：profile 编号 1（最宽）→ 6（最窄）是有序的布局能力序列，
     * 从宽到窄依次降级更符合「降级声明」的直觉，也让 1 档声明可以覆盖全部档位。
     *
     * <p>没有任何声明落在当前档或更窄处时用基础值（{@link #NONE}）。
     *
     * <pre>
     *   profiles={{ 4: { direction: 'column' } }}   // 4–6 档竖排；1–3 档用基础值
     * </pre>
     *
     * @param profile  当前判定出的档位
     * @param declared 已解析的覆盖集合
     */
    public static UiProfileOverride forProfile(ViewportProfile profile,
                                               Map<ViewportProfile, UiProfileOverride> declared) {
        if (profile == null || declared == null || declared.isEmpty()) {
            return NONE;
        }
        for (int number = profile.number(); number >= 1; number--) {
            UiProfileOverride override = declared.get(ViewportProfile.byNumber(number));
            if (override != null) return override;
        }
        return NONE;
    }

    // ---- 解析辅助（分派由键表分类驱动）----

    private static void mergeInto(Map<ViewportProfile, UiProfileOverride> target, Map<?, ?> map, String path) {
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            ViewportProfile profile = profileOfKey(entry.getKey(), path);
            if (!(entry.getValue() instanceof Map<?, ?> values)) {
                throw invalid(path, "profile override must be an object, got " + describe(entry.getValue()));
            }
            target.put(profile, parseOne(values, path));
        }
    }

    private static UiProfileOverride parseOne(Map<?, ?> values, String path) {
        Map<String, Object> parsed = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : values.entrySet()) {
            String key = String.valueOf(entry.getKey());
            if (!KEYS.contains(key)) {
                throw invalid(path, "unknown profile override key '" + key + "'; supported keys are " + KEYS);
            }
            parsed.put(key, parseValue(key, entry.getValue(), path));
        }
        return new UiProfileOverride(parsed);
    }

    /** 单键取值解析：唯一的分派点。 */
    private static Object parseValue(String key, Object value, String path) {
        if (value == null) {
            throw invalid(path, "profile override '" + key + "' must not be null");
        }
        if (DIMENSION_KEYS.contains(key)) {
            if (value instanceof UiDimension dimension) return dimension;
            if (value instanceof Number || value instanceof String) {
                try {
                    return UiDimension.parse(value);
                } catch (IllegalArgumentException failure) {
                    throw invalid(path, "invalid profile override '" + key + "': " + failure.getMessage());
                }
            }
            throw invalid(path, "profile override '" + key + "' must be a size value, got " + describe(value));
        }
        if (NUMERIC_KEYS.contains(key)) {
            return requireNonNegative(key, numeric(value, key, path), path);
        }
        if (BOOLEAN_KEYS.contains(key)) {
            return boolValue(key, value, path);
        }
        return switch (key) {
            case "align", "justify" -> alignmentValue(key, value, path);
            case "anchor" -> anchorValueOf(key, value, path);
            case "direction" -> axisValue(key, value, path);
            case "font" -> textValue(key, value, path);
            default -> throw invalid(path, "unknown profile override key '" + key + "'");
        };
    }

    private static double numeric(Object value, String key, String path) {
        if (value instanceof Number number) return number.doubleValue();
        if (value instanceof String text) {
            try {
                return Double.parseDouble(text.trim());
            } catch (NumberFormatException failure) {
                throw invalid(path, "profile override '" + key + "' is not a number: " + text);
            }
        }
        throw invalid(path, "profile override '" + key + "' must be a number, got " + describe(value));
    }

    private static double requireNonNegative(String key, double value, String path) {
        if (!Double.isFinite(value) || value < 0 || value > UiLayoutSpec.MAX_LOGICAL_PIXELS) {
            throw invalid(path, "profile override '" + key + "' must be a finite logical-pixel value in [0, "
                    + UiLayoutSpec.MAX_LOGICAL_PIXELS + "], got " + value);
        }
        return value;
    }

    private static Boolean boolValue(String key, Object value, String path) {
        if (value instanceof Boolean bool) return bool;
        if (value instanceof String text) {
            if ("true".equals(text)) return Boolean.TRUE;
            if ("false".equals(text)) return Boolean.FALSE;
        }
        throw invalid(path, "profile override '" + key + "' must be a boolean, got " + describe(value));
    }

    private static UiAlignment alignmentValue(String key, Object value, String path) {
        if (value instanceof UiAlignment alignment) return alignment;
        if (!(value instanceof String text)) {
            throw invalid(path, "profile override '" + key + "' must be an alignment string, got "
                    + describe(value));
        }
        UiAlignment alignment = UiAlignment.parse(text);
        if (alignment == null) {
            throw invalid(path, "profile override '" + key + "' must be a registered alignment, got '" + text + "'");
        }
        return alignment;
    }

    private static UiAnchor anchorValueOf(String key, Object value, String path) {
        if (value instanceof UiAnchor anchor) return anchor;
        if (!(value instanceof String text)) {
            throw invalid(path, "profile override '" + key + "' must be an anchor string, got " + describe(value));
        }
        UiAnchor anchor = UiAnchor.parse(text);
        if (anchor == null) {
            throw invalid(path, "profile override '" + key + "' must be a registered anchor, got '" + text + "'");
        }
        return anchor;
    }

    private static UiAxis axisValue(String key, Object value, String path) {
        if (value instanceof UiAxis axis) return axis;
        if (!(value instanceof String text)) {
            throw invalid(path, "profile override '" + key + "' must be row|column, got " + describe(value));
        }
        UiAxis axis = UiAxis.fromCode(text);
        if (axis == null) {
            throw invalid(path, "profile override '" + key + "' must be row|column, got '" + text + "'");
        }
        return axis;
    }

    private static String textValue(String key, Object value, String path) {
        if (value instanceof String text && !text.isBlank()) return text;
        throw invalid(path, "profile override '" + key + "' must be a non-blank string, got " + describe(value));
    }

    private static ViewportProfile profileOfKey(Object key, String path) {
        if (key instanceof Number number) {
            int value = number.intValue();
            if (value != number.doubleValue()) {
                throw invalid(path, "profile key must be an integer 1-6, got " + key);
            }
            return parseNumberKey(value, path);
        }
        if (key instanceof String text) {
            String trimmed = text.trim();
            ViewportProfile byCode = ViewportProfile.fromCode(trimmed);
            if (byCode != null) return byCode;
            try {
                return parseNumberKey(Integer.parseInt(trimmed), path);
            } catch (NumberFormatException failure) {
                throw invalid(path, "unknown viewport profile '" + text + "'; expected 1-6 or a profile code");
            }
        }
        throw invalid(path, "profile key must be an integer 1-6 or a profile code, got " + describe(key));
    }

    private static ViewportProfile parseNumberKey(int number, String path) {
        try {
            return ViewportProfile.byNumber(number);
        } catch (IllegalArgumentException failure) {
            throw invalid(path, failure.getMessage());
        }
    }

    // ---- 访问器辅助 ----

    private UiDimension dimension(String key) {
        Object value = values.get(key);
        return value instanceof UiDimension dimension ? dimension : null;
    }

    private Double number(String key) {
        Object value = values.get(key);
        return value instanceof Number number ? number.doubleValue() : null;
    }

    private Boolean bool(String key) {
        Object value = values.get(key);
        return value instanceof Boolean bool ? bool : null;
    }

    private String text(String key) {
        Object value = values.get(key);
        return value instanceof String text ? text : null;
    }

    private UiAlignment alignment(String key) {
        Object value = values.get(key);
        return value instanceof UiAlignment alignment ? alignment : null;
    }

    private UiAnchor anchorValue(String key) {
        Object value = values.get(key);
        return value instanceof UiAnchor anchor ? anchor : null;
    }

    private UiAxis axis(String key) {
        Object value = values.get(key);
        return value instanceof UiAxis axis ? axis : null;
    }

    private static String describe(Object value) {
        return value == null ? "null" : value.getClass().getSimpleName();
    }

    private static UiFailure invalid(String path, String message) {
        return new UiFailure(UiDiagnosticStage.LAYOUT, UiFailure.INVALID_LAYOUT, message, path);
    }
}
