package com.tkisor.nekojs.core.ui;

import com.tkisor.nekojs.api.ui.UiAnchor;
import com.tkisor.nekojs.api.ui.UiAlignment;
import com.tkisor.nekojs.api.ui.UiDiagnosticStage;
import com.tkisor.nekojs.api.ui.UiFailure;
import com.tkisor.nekojs.api.ui.UiLayoutSpec;
import com.tkisor.nekojs.api.ui.UiPrimitive;
import com.tkisor.nekojs.api.ui.UiProfileOverride;
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
 * JSX UI 的<b>唯一登记表</b>（AC2）：primitive 标签到允许属性集合的映射，以及候选 props 校验。
 *
 * <p>属性名与取值种类来自 {@link UiProp}（唯一事实源）；本类只负责"哪个 primitive 允许哪些
 * 属性"。两者共同构成 JSX 声明（{@code UiJsxDeclarationGenerator}）、managed contract 与
 * automatic runtime 校验的同源输入——不存在第二份 primitive/props catalog。
 *
 * <p>未知 primitive、非法 props 取值、非法布局值一律显式失败（{@link UiFailure}），不静默回退。
 */
public final class UiPrimitiveRegistry {

    private static final Map<UiPrimitive, Set<UiProp>> ALLOWED = buildAllowed();

    private UiPrimitiveRegistry() {
    }

    /** primitive 的允许属性集合（含事件属性）。 */
    public static Set<UiProp> allowedProps(UiPrimitive primitive) {
        if (primitive == null) {
            throw new UiFailure(UiDiagnosticStage.HOST_UPDATE, UiFailure.UNKNOWN_PRIMITIVE,
                    "unknown primitive: null", null);
        }
        return ALLOWED.get(primitive);
    }

    /** 全部登记 primitive 标签。 */
    public static List<String> tags() {
        List<String> tags = new ArrayList<>();
        for (UiPrimitive primitive : UiPrimitive.values()) {
            tags.add(primitive.tag());
        }
        return List.copyOf(tags);
    }

    /** 标签到 primitive；未登记返回 null。 */
    public static UiPrimitive byTag(String tag) {
        return UiPrimitive.fromTag(tag);
    }

    /** 登记表快照：primitive 到允许属性名（保持登记顺序）。 */
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
     * 校验一个 host 节点的 props 包：未登记属性名与受控取值都显式失败。
     *
     * <p>取值校验委派给 {@link UiProp#kind()}，布局约束委派给 {@link UiLayoutSpec}——本方法
     * 不重复属性语义。
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
            Object raw = props.raw(prop);
            if (raw == null) {
                if (prop.kind().acceptsNull()) continue;
                continue;
            }
            prop.kind().validate(prop, primitive.tag(), raw, path);
        }
        validateLayout(primitive, props, path);
    }

    /**
     * 校验完整布局约束：在候选择阶段就解析 {@link UiLayoutSpec}，使非法尺寸/间距/对齐在
     * <b>创建任何 host node 之前</b>失败（AC5：布局失败不提交半成品树）。
     */
    public static void validateLayout(UiPrimitive primitive, UiProps props, String path) {
        try {
            if (props.has(UiProp.ALIGN)) {
                UiAlignment.parse(props.string(UiProp.ALIGN));
            }
            if (props.has(UiProp.JUSTIFY)) {
                UiAlignment.parse(props.string(UiProp.JUSTIFY));
            }
            if (props.has(UiProp.ANCHOR)) {
                UiAnchor.parse(props.string(UiProp.ANCHOR));
            }
            UiLayoutSpec.fromProps(props);
            // 每一条 profile 覆盖自身都必须合法：非法覆盖在候选阶段失败，不等某个档位命中。
            for (UiProfileOverride override : UiProfileOverride
                    .parseAll(props.raw(UiProp.PROFILES), path).values()) {
                UiLayoutSpec.fromProps(props, override);
            }
        } catch (UiFailure failure) {
            throw failure;
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
                UiProp.DIRECTION, UiProp.PROFILES,
                UiProp.BACKGROUND, UiProp.BORDER, UiProp.RADIUS, UiProp.OPACITY);
        Set<UiProp> pointer = EnumSet.of(
                UiProp.ON_CLICK, UiProp.ON_MOUSE_DOWN, UiProp.ON_MOUSE_UP,
                UiProp.ON_MOUSE_MOVE, UiProp.ON_FOCUS, UiProp.ON_BLUR);
        Set<UiProp> interactivity = EnumSet.of(
                UiProp.DISABLED, UiProp.FOCUSED, UiProp.HOVERED, UiProp.PRESSED,
                UiProp.NARRATION, UiProp.TOOLTIP);

        map.put(UiPrimitive.SCREEN, EnumSet.of(
                UiProp.TITLE, UiProp.WIDTH, UiProp.HEIGHT, UiProp.BACKGROUND, UiProp.PADDING,
                UiProp.ALIGN, UiProp.JUSTIFY, UiProp.VISIBLE, UiProp.PROFILES, UiProp.ON_CLOSE));
        map.put(UiPrimitive.PANEL, union(box, interactivity, pointer, EnumSet.of(UiProp.LOADING)));
        map.put(UiPrimitive.ROW, union(box, EnumSet.of(UiProp.GAP, UiProp.ALIGN, UiProp.JUSTIFY)));
        map.put(UiPrimitive.COLUMN, union(box, EnumSet.of(UiProp.GAP, UiProp.ALIGN, UiProp.JUSTIFY)));
        map.put(UiPrimitive.STACK, union(box, EnumSet.of(UiProp.ALIGN, UiProp.JUSTIFY)));
        map.put(UiPrimitive.SCROLL, union(box, interactivity, pointer, EnumSet.of(
                UiProp.ON_SCROLL)));
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
                UiProp.SOURCE, UiProp.NARRATION, UiProp.TOOLTIP, UiProp.OPACITY)));
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
