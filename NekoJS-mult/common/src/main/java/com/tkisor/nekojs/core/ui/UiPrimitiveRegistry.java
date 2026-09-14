package com.tkisor.nekojs.core.ui;

import com.tkisor.nekojs.api.ui.UiAnchor;
import com.tkisor.nekojs.api.ui.UiDiagnosticStage;
import com.tkisor.nekojs.api.ui.UiFailure;
import com.tkisor.nekojs.api.ui.UiPrimitive;
import com.tkisor.nekojs.api.ui.UiProp;
import com.tkisor.nekojs.api.ui.UiProps;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * JSX UI 的<b>唯一登记表</b>（AC2）：primitive 标签、允许属性集合与受控 props 取值校验。
 *
 * <p>登记表是 JSX 声明（{@code UiJsxDeclarationGenerator}）、managed contract、Probe 与
 * automatic runtime 校验的同源输入；不存在第二份 primitive/props catalog。
 *
 * <p>未知 primitive、非法 props、重复 key 一律显式失败（{@link UiFailure}），不静默回退。
 */
public final class UiPrimitiveRegistry {

    private static final Map<UiPrimitive, Set<UiProp>> ALLOWED = buildAllowed();

    private UiPrimitiveRegistry() {
    }

    /** primitive 的允许属性集合（含事件属性）。 */
    public static Set<UiProp> allowedProps(UiPrimitive primitive) {
        Set<UiProp> props = ALLOWED.get(primitive);
        if (props == null) {
            throw new UiFailure(UiDiagnosticStage.HOST_UPDATE, UiFailure.UNKNOWN_PRIMITIVE,
                    "unknown primitive: " + primitive, null);
        }
        return props;
    }

    /** 全部登记 primitive 标签。 */
    public static List<String> tags() {
        List<String> tags = new ArrayList<>();
        for (UiPrimitive primitive : UiPrimitive.values()) {
            tags.add(primitive.tag());
        }
        return List.copyOf(tags);
    }

    /** 标签 → primitive；未登记返回 {@code null}。 */
    public static UiPrimitive byTag(String tag) {
        return UiPrimitive.fromTag(tag);
    }

    /** 登记表快照：primitive → 允许属性名（保持登记顺序）。 */
    public static Map<String, List<String>> snapshot() {
        Map<String, List<String>> snapshot = new LinkedHashMap<>();
        for (UiPrimitive primitive : UiPrimitive.values()) {
            List<String> names = new ArrayList<>();
            for (UiProp prop : ALLOWED.get(primitive)) {
                names.add(prop.jsName());
            }
            snapshot.put(primitive.tag(), List.copyOf(names));
        }
        return snapshot;
    }

    /**
     * 校验一个 host 节点的 props 包：未登记属性名、事件值类型、受控取值都显式失败。
     *
     * @param primitive 节点 primitive
     * @param props     候选 props
     * @param path      节点路径（诊断）
     * @throws UiFailure 属性未登记或取值非法
     */
    public static void validateProps(UiPrimitive primitive, UiProps props, String path) {
        if (props == null) return;
        Set<UiProp> allowed = allowedProps(primitive);
        for (String name : props.names()) {
            UiProp prop = UiProp.fromJsName(name);
            if (prop == null) {
                throw new UiFailure(UiDiagnosticStage.COMPONENT, UiFailure.INVALID_PROP,
                        "unknown prop '" + name + "' on <" + primitive.tag() + ">", path);
            }
            if (!allowed.contains(prop)) {
                throw new UiFailure(UiDiagnosticStage.COMPONENT, UiFailure.INVALID_PROP,
                        "prop '" + name + "' is not allowed on <" + primitive.tag() + ">", path);
            }
            validateValue(prop, props, path, primitive);
        }
    }

    /**
     * 校验重复 key：同一父节点下同一 host type 的 key 必须唯一。
     *
     * @param parentPath 父节点路径（诊断）
     * @param keys       子节点 (type-tag, key) 对
     * @throws UiFailure key 重复
     */
    public static void validateUniqueKeys(String parentPath, List<KeyedTag> keys) {
        Set<String> seen = new java.util.HashSet<>();
        for (KeyedTag keyed : keys) {
            if (keyed.key() == null) continue;
            String identity = keyed.tag() + "#" + keyed.key();
            if (!seen.add(identity)) {
                throw new UiFailure(UiDiagnosticStage.RENDER, UiFailure.DUPLICATE_KEY,
                        "duplicate key '" + keyed.key() + "' for <" + keyed.tag() + "> children of "
                                + (parentPath == null ? "<root>" : parentPath), parentPath);
            }
        }
    }

    /** 重复 key 校验输入：节点的 type 标签与 key。 */
    public record KeyedTag(String tag, String key) {
    }

    /** 受控 props 取值校验：每类属性只接受对应取值形状。 */
    private static void validateValue(UiProp prop, UiProps props, String path, UiPrimitive primitive) {
        Object raw = props.raw(prop.jsName());
        if (raw == null) {
            if (prop.isEvent()) return; // 显式 null 事件是有意的解绑
            return;
        }
        switch (prop) {
            case TEXT, VALUE, PLACEHOLDER, TITLE, NARRATION, TOOLTIP, SOURCE,
                 FONT, COLOR, BACKGROUND, BORDER -> {
                if (raw instanceof String) return;
                if (prop == UiProp.SOURCE) return; // 资源标识也可以是受控对象形式，由 Adapter 校验
                throw invalid(prop, primitive, raw, path, "must be a string");
            }
            case FIT, WRAP, VISIBLE, DISABLED, FOCUSED, HOVERED, PRESSED, LOADING, SCROLLABLE -> {
                if (raw instanceof Boolean) return;
                throw invalid(prop, primitive, raw, path, "must be a boolean");
            }
            case WIDTH, HEIGHT -> {
                if (raw instanceof Number || raw instanceof String) return;
                throw invalid(prop, primitive, raw, path, "must be a number or auto|fill|<n>%");
            }
            case MIN_WIDTH, MIN_HEIGHT, MAX_WIDTH, MAX_HEIGHT, MARGIN, PADDING, GAP, GROW,
                 RADIUS, OPACITY, FONT_SIZE, SCROLL_X, SCROLL_Y -> {
                if (raw instanceof Number) return;
                throw invalid(prop, primitive, raw, path, "must be a number");
            }
            case ALIGN, JUSTIFY -> {
                if (raw instanceof String) return;
                throw invalid(prop, primitive, raw, path, "must be a string alignment");
            }
            case ANCHOR -> {
                if (raw instanceof String) return;
                throw invalid(prop, primitive, raw, path, "must be a string anchor");
            }
            case ERROR -> {
                if (raw instanceof String || raw instanceof Boolean) return;
                throw invalid(prop, primitive, raw, path, "must be a string or boolean");
            }
            case MAX_LENGTH -> {
                if (raw instanceof Number number && number.doubleValue() >= 0) return;
                throw invalid(prop, primitive, raw, path, "must be a non-negative number");
            }
            default -> {
                if (prop.isEvent()) {
                    // 事件值必须是脚本函数（opaque 句柄）或显式 null；字符串/数字是非法事件值。
                    if (raw instanceof Runnable || raw instanceof java.util.function.Consumer<?>) {
                        return;
                    }
                    if (raw instanceof String || raw instanceof Number || raw instanceof Boolean) {
                        throw invalid(prop, primitive, raw, path, "event value must be a function or null");
                    }
                    return; // Adapter 侧句柄（如 Graal Value）由 host Adapter 校验
                }
                throw invalid(prop, primitive, raw, path, "unsupported prop category");
            }
        }
    }

    private static UiFailure invalid(UiProp prop, UiPrimitive primitive, Object raw,
                                     String path, String requirement) {
        return new UiFailure(UiDiagnosticStage.COMPONENT, UiFailure.INVALID_PROP,
                "prop '" + prop.jsName() + "' on <" + primitive.tag() + "> " + requirement
                        + ", got " + raw.getClass().getName(), path);
    }

    /** 校验受控资源标识（image.src 等）：{@code namespace:path}，拒绝 URL 与绝对路径。 */
    public static void validateResourceId(String raw, String path) {
        if (raw == null) return;
        int separator = raw.indexOf(':');
        if (separator <= 0 || separator == raw.length() - 1) {
            throw new UiFailure(UiDiagnosticStage.COMPONENT, UiFailure.INVALID_PROP,
                    "resource id must be namespace:path, got '" + raw + "'", path);
        }
        String namespace = raw.substring(0, separator);
        String rest = raw.substring(separator + 1);
        if (rest.contains("://") || rest.startsWith("/") || rest.contains("\\")
                || rest.contains("..") || namespace.contains("/") || namespace.contains(".")) {
            throw new UiFailure(UiDiagnosticStage.COMPONENT, UiFailure.INVALID_PROP,
                    "resource id must be a controlled resource path, got '" + raw + "'", path);
        }
    }

    /**
     * 校验完整布局约束：在候选择阶段就解析 {@link com.tkisor.nekojs.api.ui.UiLayoutSpec}，
     * 使非法尺寸/间距/对齐在<b>创建任何 host node 之前</b>失败（AC5：布局失败不提交半成品树）。
     */
    public static void validateLayoutSpec(UiProps props, String path) {
        try {
            com.tkisor.nekojs.api.ui.UiLayoutSpec.fromProps(props);
        } catch (UiFailure failure) {
            throw failure;
        } catch (IllegalArgumentException failure) {
            throw new UiFailure(UiDiagnosticStage.LAYOUT, UiFailure.INVALID_LAYOUT,
                    failure.getMessage(), path, failure);
        }
    }

    /** 校验布局取值：在 props 层就能判定的非法值（对齐/锚定/百分比）显式失败。 */
    public static void validateLayoutValues(UiProps props, String path) {
        try {
            if (props.has(UiProp.ALIGN.jsName())) {
                com.tkisor.nekojs.api.ui.UiAlignment.parse(props.string(UiProp.ALIGN.jsName()));
            }
            if (props.has(UiProp.JUSTIFY.jsName())) {
                com.tkisor.nekojs.api.ui.UiAlignment.parse(props.string(UiProp.JUSTIFY.jsName()));
            }
            if (props.has(UiProp.ANCHOR.jsName())) {
                UiAnchor.parse(props.string(UiProp.ANCHOR.jsName()));
            }
        } catch (IllegalArgumentException failure) {
            throw new UiFailure(UiDiagnosticStage.LAYOUT, UiFailure.INVALID_LAYOUT,
                    failure.getMessage(), path, failure);
        }
    }

    private static Map<UiPrimitive, Set<UiProp>> buildAllowed() {
        Map<UiPrimitive, Set<UiProp>> map = new EnumMap<>(UiPrimitive.class);

        Set<UiProp> box = EnumSet.of(
                UiProp.WIDTH, UiProp.HEIGHT, UiProp.MIN_WIDTH, UiProp.MIN_HEIGHT,
                UiProp.MAX_WIDTH, UiProp.MAX_HEIGHT, UiProp.MARGIN, UiProp.PADDING,
                UiProp.ALIGN, UiProp.JUSTIFY, UiProp.ANCHOR, UiProp.GROW, UiProp.VISIBLE,
                UiProp.BACKGROUND, UiProp.BORDER, UiProp.RADIUS, UiProp.OPACITY);
        Set<UiProp> pointer = EnumSet.of(
                UiProp.ON_CLICK, UiProp.ON_MOUSE_DOWN, UiProp.ON_MOUSE_UP,
                UiProp.ON_MOUSE_MOVE, UiProp.ON_FOCUS, UiProp.ON_BLUR);
        Set<UiProp> interactivity = EnumSet.of(
                UiProp.DISABLED, UiProp.FOCUSED, UiProp.HOVERED, UiProp.PRESSED,
                UiProp.NARRATION, UiProp.TOOLTIP);

        map.put(UiPrimitive.SCREEN, EnumSet.of(
                UiProp.TITLE, UiProp.WIDTH, UiProp.HEIGHT, UiProp.BACKGROUND, UiProp.PADDING,
                UiProp.ALIGN, UiProp.JUSTIFY, UiProp.VISIBLE, UiProp.ON_CLOSE));

        map.put(UiPrimitive.PANEL, union(box, interactivity, pointer, EnumSet.of(
                UiProp.LOADING)));

        map.put(UiPrimitive.ROW, union(box, EnumSet.of(
                UiProp.GAP, UiProp.ALIGN, UiProp.JUSTIFY)));
        map.put(UiPrimitive.COLUMN, union(box, EnumSet.of(
                UiProp.GAP, UiProp.ALIGN, UiProp.JUSTIFY)));

        map.put(UiPrimitive.STACK, union(box, EnumSet.of(UiProp.ALIGN, UiProp.JUSTIFY)));

        map.put(UiPrimitive.SCROLL, union(box, interactivity, pointer, EnumSet.of(
                UiProp.SCROLL_X, UiProp.SCROLL_Y, UiProp.SCROLLABLE, UiProp.ON_SCROLL)));

        map.put(UiPrimitive.LABEL, union(box, EnumSet.of(
                UiProp.TEXT, UiProp.COLOR, UiProp.FONT, UiProp.FONT_SIZE, UiProp.WRAP,
                UiProp.NARRATION, UiProp.TOOLTIP, UiProp.ON_CLICK)));

        map.put(UiPrimitive.BUTTON, union(box, interactivity, pointer, EnumSet.of(
                UiProp.TEXT, UiProp.COLOR, UiProp.FONT, UiProp.FONT_SIZE, UiProp.LOADING,
                UiProp.ON_CLICK, UiProp.ON_KEY_DOWN, UiProp.ON_KEY_UP, UiProp.ON_CLOSE)));

        map.put(UiPrimitive.INPUT, union(box, interactivity, pointer, EnumSet.of(
                UiProp.VALUE, UiProp.PLACEHOLDER, UiProp.COLOR, UiProp.FONT, UiProp.FONT_SIZE,
                UiProp.MAX_LENGTH, UiProp.ERROR, UiProp.ON_CHAR, UiProp.ON_KEY_DOWN,
                UiProp.ON_KEY_UP, UiProp.ON_SUBMIT, UiProp.ON_FOCUS, UiProp.ON_BLUR,
                UiProp.ON_CLOSE)));

        map.put(UiPrimitive.IMAGE, union(box, EnumSet.of(
                UiProp.SOURCE, UiProp.FIT, UiProp.NARRATION, UiProp.TOOLTIP, UiProp.OPACITY)));

        map.put(UiPrimitive.SPACER, union(box, EnumSet.of(UiProp.GROW)));

        return map;
    }

    @SafeVarargs
    private static Set<UiProp> union(Set<UiProp> base, Set<UiProp>... extras) {
        Set<UiProp> result = EnumSet.copyOf(base);
        for (Set<UiProp> extra : extras) {
            result.addAll(extra);
        }
        return result;
    }
}
