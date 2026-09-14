package com.tkisor.nekojs.api.ui;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 单档 Viewport Profile 的布局覆盖包（jsx-ui-spec「响应式模型」，票 43 AC2/AC3）。
 *
 * <p>脚本在节点上写一个 {@code profiles} 属性，把 1–6 档各自的覆盖放在一起——不必为每个
 * 属性重复六遍，也不必复制节点。每个覆盖值只描述「这一档相对基础值怎么变」：
 *
 * <pre>
 *   &lt;column gap={4} profiles={{ 1: { gap: 12, direction: 'row' },
 *                                 4: { gap: 2, visible: false } }}&gt;
 * </pre>
 *
 * <p>解析顺序（固定，AC2）：
 * <ol>
 *   <li>基础值 = 节点上的普通属性（没有覆盖时原样使用）；</li>
 *   <li>覆盖值 = 本档的覆盖；缺省回退见 {@link #forProfile}；</li>
 *   <li>解析结果再进入 {@link UiLayoutSpec} 的取值校验与 min/max 裁剪。</li>
 * </ol>
 *
 * <p>受控覆盖键恰好是 {@link #KEYS}；未知键、非法档位、非法取值一律显式抛
 * {@link UiFailure}（{@code INVALID_LAYOUT}），不静默忽略、不静默夹取。
 */
public record UiProfileOverride(
        UiDimension width,
        UiDimension height,
        Double minWidth,
        Double minHeight,
        Double maxWidth,
        Double maxHeight,
        Double margin,
        Double padding,
        Double gap,
        UiAlignment align,
        UiAlignment justify,
        UiAnchor anchor,
        UiAxis direction,
        Double fontSize,
        Boolean visible
) {

    /** 受控覆盖键（排序后的稳定顺序，供声明与漂移测试使用）。 */
    public static final List<String> KEYS = List.of(
            "align", "anchor", "direction", "fontSize", "gap", "height", "justify",
            "margin", "maxHeight", "maxWidth", "minHeight", "minWidth", "padding", "visible", "width");

    /** 空覆盖（不改变任何属性）。 */
    public static final UiProfileOverride NONE =
            new UiProfileOverride(null, null, null, null, null, null, null, null, null,
                    null, null, null, null, null, null);

    public UiProfileOverride {
        if (minWidth != null) minWidth = requireSize(minWidth, "minWidth");
        if (minHeight != null) minHeight = requireSize(minHeight, "minHeight");
        if (maxWidth != null) maxWidth = requireSize(maxWidth, "maxWidth");
        if (maxHeight != null) maxHeight = requireSize(maxHeight, "maxHeight");
        if (margin != null) margin = requireSize(margin, "margin");
        if (padding != null) padding = requireSize(padding, "padding");
        if (gap != null) gap = requireSize(gap, "gap");
        if (fontSize != null && (!Double.isFinite(fontSize) || fontSize <= 0 || fontSize > UiLayoutSpec.MAX_LOGICAL_PIXELS)) {
            throw new IllegalArgumentException("fontSize override must be a positive finite logical-pixel value, got " + fontSize);
        }
    }

    /** 是否没有任何覆盖值。 */
    public boolean isEmpty() {
        return this.equals(NONE);
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

    /** 本档的覆盖值合并到基础属性上（只写覆盖中显式出现的键）。 */
    public Map<String, Object> applyTo(Map<String, Object> base) {
        Map<String, Object> merged = new LinkedHashMap<>(base);
        if (width != null) merged.put(UiProp.WIDTH.jsName(), width);
        if (height != null) merged.put(UiProp.HEIGHT.jsName(), height);
        if (minWidth != null) merged.put(UiProp.MIN_WIDTH.jsName(), minWidth);
        if (minHeight != null) merged.put(UiProp.MIN_HEIGHT.jsName(), minHeight);
        if (maxWidth != null) merged.put(UiProp.MAX_WIDTH.jsName(), maxWidth);
        if (maxHeight != null) merged.put(UiProp.MAX_HEIGHT.jsName(), maxHeight);
        if (margin != null) merged.put(UiProp.MARGIN.jsName(), margin);
        if (padding != null) merged.put(UiProp.PADDING.jsName(), padding);
        if (gap != null) merged.put(UiProp.GAP.jsName(), gap);
        if (align != null) merged.put(UiProp.ALIGN.jsName(), align.jsName());
        if (justify != null) merged.put(UiProp.JUSTIFY.jsName(), justify.jsName());
        if (anchor != null) merged.put(UiProp.ANCHOR.jsName(), anchor.jsName());
        if (direction != null) merged.put(UiProp.DIRECTION.jsName(), direction.code());
        if (fontSize != null) merged.put(UiProp.FONT_SIZE.jsName(), fontSize);
        if (visible != null) merged.put(UiProp.VISIBLE.jsName(), visible);
        return merged;
    }

    /**
     * 本节点在给定档位下生效的覆盖（AC2 的「缺省回退」规则，固定且可观察）。
     *
     * <p>规则与 CSS {@code min-width} 断点同构：<b>一条声明作用于它自己以及所有更宽的档位</b>，
     * 即「取编号 ≤ 当前档位编号的最大已声明档」。因此基础值是<b>窄屏缺省</b>，脚本只在
     * 需要的断点上写「从这一档起改成什么」，不必把同一属性重复填六遍；更窄的档位永远落到
     * 基础值，窄屏策略不会被动地被桌面覆盖偷走。
     *
     * <p>没有任何声明落在当前档或更宽处时用基础值（{@link #NONE}）。
     *
     * <pre>
     *   profiles={{ 3: { direction: 'row', gap: 8 } }}   // 3 档及以上横排；1–2 档用基础值
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

    // ---- 解析辅助 ----

    private static void mergeInto(Map<ViewportProfile, UiProfileOverride> target, Map<?, ?> map, String path) {
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            ViewportProfile profile = profileOfKey(entry.getKey(), path);
            Map<?, ?> values = valuesOf(entry.getValue(), path);
            target.put(profile, parseOne(values, path));
        }
    }

    private static Map<?, ?> valuesOf(Object raw, String path) {
        if (raw instanceof Map<?, ?> map) {
            return map;
        }
        throw invalid(path, "profile override must be an object, got " + describe(raw));
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

    private static UiProfileOverride parseOne(Map<?, ?> values, String path) {
        UiDimension width = null;
        UiDimension height = null;
        Double minWidth = null;
        Double minHeight = null;
        Double maxWidth = null;
        Double maxHeight = null;
        Double margin = null;
        Double padding = null;
        Double gap = null;
        UiAlignment align = null;
        UiAlignment justify = null;
        UiAnchor anchor = null;
        UiAxis direction = null;
        Double fontSize = null;
        Boolean visible = null;

        for (Map.Entry<?, ?> entry : values.entrySet()) {
            String key = String.valueOf(entry.getKey());
            Object value = entry.getValue();
            if (!KEYS.contains(key)) {
                throw invalid(path, "unknown profile override key '" + key + "'; supported keys are " + KEYS);
            }
            try {
                switch (key) {
                    case "width" -> width = dimension(value, key, path);
                    case "height" -> height = dimension(value, key, path);
                    case "minWidth" -> minWidth = size(value, key, path);
                    case "minHeight" -> minHeight = size(value, key, path);
                    case "maxWidth" -> maxWidth = size(value, key, path);
                    case "maxHeight" -> maxHeight = size(value, key, path);
                    case "margin" -> margin = size(value, key, path);
                    case "padding" -> padding = size(value, key, path);
                    case "gap" -> gap = size(value, key, path);
                    case "fontSize" -> fontSize = size(value, key, path);
                    case "align" -> align = UiAlignment.parse(string(value, key, path));
                    case "justify" -> justify = UiAlignment.parse(string(value, key, path));
                    case "anchor" -> anchor = UiAnchor.parse(string(value, key, path));
                    case "direction" -> direction = axis(value, path);
                    case "visible" -> visible = bool(value, key, path);
                    default -> throw invalid(path, "unknown profile override key '" + key + "'");
                }
            } catch (UiFailure failure) {
                throw failure;
            } catch (RuntimeException failure) {
                throw invalid(path, "invalid profile override '" + key + "': " + failure.getMessage());
            }
        }
        try {
            return new UiProfileOverride(width, height, minWidth, minHeight, maxWidth, maxHeight,
                    margin, padding, gap, align, justify, anchor, direction, fontSize, visible);
        } catch (IllegalArgumentException failure) {
            throw invalid(path, failure.getMessage());
        }
    }

    private static UiDimension dimension(Object value, String key, String path) {
        if (value == null) {
            throw invalid(path, "profile override '" + key + "' must not be null");
        }
        if (value instanceof UiDimension dimension) {
            return dimension;
        }
        if (value instanceof Number || value instanceof String) {
            return UiDimension.parse(value);
        }
        throw invalid(path, "profile override '" + key + "' must be a size value, got " + describe(value));
    }

    private static Double size(Object value, String key, String path) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        if (value instanceof String text) {
            try {
                return Double.parseDouble(text.trim());
            } catch (NumberFormatException failure) {
                throw invalid(path, "profile override '" + key + "' is not a number: " + text);
            }
        }
        throw invalid(path, "profile override '" + key + "' must be a number, got " + describe(value));
    }

    private static UiAxis axis(Object value, String path) {
        if (!(value instanceof String text)) {
            throw invalid(path, "profile override 'direction' must be row|column, got " + describe(value));
        }
        UiAxis axis = UiAxis.fromCode(text);
        if (axis == null) {
            throw invalid(path, "profile override 'direction' must be row|column, got '" + text + "'");
        }
        return axis;
    }

    private static String string(Object value, String key, String path) {
        if (value instanceof String text) {
            return text;
        }
        throw invalid(path, "profile override '" + key + "' must be a string, got " + describe(value));
    }

    private static Boolean bool(Object value, String key, String path) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value instanceof String text) {
            if ("true".equals(text)) return Boolean.TRUE;
            if ("false".equals(text)) return Boolean.FALSE;
        }
        throw invalid(path, "profile override '" + key + "' must be a boolean, got " + describe(value));
    }

    private static double requireSize(double value, String name) {
        if (!Double.isFinite(value) || value < 0 || value > UiLayoutSpec.MAX_LOGICAL_PIXELS) {
            throw new IllegalArgumentException(name + " override must be a finite logical-pixel value in [0, "
                    + UiLayoutSpec.MAX_LOGICAL_PIXELS + "], got " + value);
        }
        return value;
    }

    private static String describe(Object value) {
        return value == null ? "null" : value.getClass().getSimpleName();
    }

    private static UiFailure invalid(String path, String message) {
        return new UiFailure(UiDiagnosticStage.LAYOUT, UiFailure.INVALID_LAYOUT, message, path);
    }
}
