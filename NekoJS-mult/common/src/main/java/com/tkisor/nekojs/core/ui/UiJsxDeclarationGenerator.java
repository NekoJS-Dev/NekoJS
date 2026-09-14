package com.tkisor.nekojs.core.ui;

import com.tkisor.nekojs.api.ui.UiAlignment;
import com.tkisor.nekojs.api.ui.UiAnchor;
import com.tkisor.nekojs.api.ui.UiEventType;
import com.tkisor.nekojs.api.ui.UiPrimitive;
import com.tkisor.nekojs.api.ui.UiProp;
import com.tkisor.nekojs.api.ui.UiPropCategory;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * TypeScript JSX 声明生成器（jsx-ui-spec User Story 44/45、AC6）。
 *
 * <p>声明<b>同源</b>生成：intrinsic element 集合、允许属性、受控事件、枚举取值都
 * 直接迭代 Java 侧登记表（{@link UiPrimitiveRegistry} / {@link UiProp} /
 * {@link UiEventType}），与 {@code NormativeApiContract} / Probe 使用同一份输入——
 * 不存在手写第二份 UI 声明，因此「声明有而 runtime 无」或「runtime 有而声明缺失」
 * 会同时让漂移测试变红。
 */
public final class UiJsxDeclarationGenerator {

    /** automatic runtime 模块名（与 {@code NekoJsxCompiler} 注入的 import 一致）。 */
    public static final String RUNTIME_MODULE = "nekojs/jsx-runtime";

    private UiJsxDeclarationGenerator() {
    }

    /** 渲染完整 JSX 声明文件内容（确定性排序）。 */
    public static String generate() {
        StringBuilder sb = new StringBuilder();
        sb.append("// Auto-generated NekoJS JSX UI declarations from the Java-side UI registry.\n");
        sb.append("// Do not edit; the registry (UiPrimitiveRegistry/UiProp) is the single source.\n\n");
        sb.append("declare namespace NekoJsx {\n");
        sb.append("  type UiAlignment = ").append(literalUnion(alignmentValues())).append(";\n");
        sb.append("  type UiAnchor = ").append(literalUnion(anchorValues())).append(";\n");
        sb.append("  type UiEventName = ").append(literalUnion(eventNames())).append(";\n\n");
        sb.append("  interface UiSize { readonly width: number; readonly height: number }\n");
        sb.append("  interface UiRect { readonly x: number; readonly y: number;");
        sb.append(" readonly width: number; readonly height: number }\n");
        sb.append("  interface UiProps {\n");
        sb.append("    readonly [name: string]: unknown;\n");
        sb.append("    readonly children?: unknown;\n");
        sb.append("  }\n\n");
        sb.append("  interface UiRootHandle {\n");
        sb.append("    readonly rootId: string;\n");
        sb.append("    readonly generation: number;\n");
        sb.append("    readonly status: 'created' | 'open' | 'invalidated' | 'disposed';\n");
        sb.append("    readonly isActive: boolean;\n");
        sb.append("    open(): void;\n");
        sb.append("    invalidate(): void;\n");
        sb.append("    invalidateAndReconcile(): void;\n");
        sb.append("    dispatchEvent(event: UiEvent): void;\n");
        sb.append("    dispose(): void;\n");
        sb.append("    readonly focusedPath: string | null;\n");
        sb.append("  }\n\n");
        sb.append("  interface UiEvent {\n");
        sb.append("    readonly type: UiEventName;\n");
        sb.append("    readonly mouseX: number;\n");
        sb.append("    readonly mouseY: number;\n");
        sb.append("    readonly button: number;\n");
        sb.append("    readonly scrollDelta: number;\n");
        sb.append("    readonly keyCode: number;\n");
        sb.append("    readonly key: string | null;\n");
        sb.append("    readonly character: string | null;\n");
        sb.append("    readonly shift: boolean;\n");
        sb.append("    readonly control: boolean;\n");
        sb.append("    readonly alt: boolean;\n");
        sb.append("    readonly targetPath: string | null;\n");
        sb.append("    readonly rootId: string | null;\n");
        sb.append("    readonly generation: number;\n");
        sb.append("  }\n\n");
        sb.append("  interface UiSignal<T> {\n");
        sb.append("    get(): T;\n");
        sb.append("    set(value: T): void;\n");
        sb.append("    update(updater: (previous: T) => T): void;\n");
        sb.append("  }\n\n");
        sb.append("  interface UiStore {\n");
        sb.append("    read(key: string): unknown;\n");
        sb.append("    write(key: string, value: unknown): void;\n");
        sb.append("    merge(values: Record<string, unknown>): void;\n");
        sb.append("    field(key: string): { get(): unknown };\n");
        sb.append("    snapshot(): Record<string, unknown>;\n");
        sb.append("  }\n\n");
        sb.append("  interface UiStateOwner {\n");
        sb.append("    createSignal<T>(initial: T): UiSignal<T>;\n");
        sb.append("    createStore(initial: Record<string, unknown>): UiStore;\n");
        sb.append("    runTick(): void;\n");
        sb.append("    dispose(): void;\n");
        sb.append("    readonly isDisposed: boolean;\n");
        sb.append("  }\n\n");
        sb.append("  interface UiFacade {\n");
        sb.append("    readonly generation: number;\n");
        sb.append("    createStateOwner(): UiStateOwner;\n");
        sb.append("    create(screen: UiScreen): UiRootHandle;\n");
        sb.append("    open(screen: UiScreen): UiRootHandle;\n");
        sb.append("    invalidateGeneration(generation: number): void;\n");
        sb.append("    closeGeneration(generation: number): void;\n");
        sb.append("    roots(): UiRootHandle[];\n");
        sb.append("    dispose(): void;\n");
        sb.append("  }\n\n");
        sb.append("  interface UiScreenOptions {\n");
        sb.append("    readonly title: string;\n");
        sb.append("    readonly pauseGame: boolean;\n");
        sb.append("    readonly closeOnEscape: boolean;\n");
        sb.append("    readonly closeOnExternal: boolean;\n");
        sb.append("  }\n\n");
        sb.append("  interface UiScreen {\n");
        sb.append("    render(): NekoJsxElement | null;\n");
        sb.append("    readonly options?: UiScreenOptions;\n");
        sb.append("  }\n\n");
        sb.append("  interface UiPrimitiveProps {\n");
        for (Map.Entry<UiPrimitive, Set<UiProp>> entry : allowedProps().entrySet()) {
            sb.append("    readonly '").append(entry.getKey().tag()).append("': {");
            sb.append(renderPropList(entry.getValue()));
            sb.append(" };\n");
        }
        sb.append("  }\n\n");
        sb.append("  type UiPrimitiveName = ")
                .append(literalUnion(UiPrimitiveRegistry.tags())).append(";\n");
        sb.append("  type UiEventPropName = ").append(literalUnion(eventProps())).append(";\n");
        sb.append("}\n\n");
        sb.append("declare global {\n");
        sb.append("  const Ui: NekoJsx.UiFacade;\n\n");
        sb.append("  interface NekoJsxElement {\n");
        sb.append("    readonly kind: 'jsx' | 'jsxs';\n");
        sb.append("    readonly type: unknown;\n");
        sb.append("    readonly key: string | null;\n");
        sb.append("    readonly props: NekoJsx.UiProps;\n");
        sb.append("    readonly children: readonly unknown[];\n");
        sb.append("  }\n\n");
        sb.append("  namespace JSX {\n");
        sb.append("    interface Element extends NekoJsxElement {}\n");
        sb.append("    interface ElementChildrenAttribute { children: {} }\n");
        sb.append("    interface IntrinsicElements extends NekoJsx.UiPrimitiveProps {}\n");
        sb.append("  }\n");
        sb.append("}\n");
        return sb.toString();
    }

    /** 渲染 {@code nekojs/jsx-runtime} 模块声明（automatic runtime 的公开面）。 */
    public static String generateRuntimeModule() {
        StringBuilder sb = new StringBuilder();
        sb.append("declare module '").append(RUNTIME_MODULE).append("' {\n");
        sb.append("  export type NekoJsxElement = globalThis.NekoJsxElement;\n");
        sb.append("  export const Fragment: unique symbol;\n");
        sb.append("  export function jsx(type: unknown, props: Record<string, unknown> | null,");
        sb.append(" key?: string | number): NekoJsxElement;\n");
        sb.append("  export function jsxs(type: unknown, props: Record<string, unknown> | null,");
        sb.append(" key?: string | number): NekoJsxElement;\n");
        sb.append("}\n");
        return sb.toString();
    }

    /** 声明中被承诺的 intrinsic element 标签（漂移测试用）。 */
    public static List<String> declaredElementTags() {
        return UiPrimitiveRegistry.tags();
    }

    /** 声明中被承诺的 primitive → 属性名映射（漂移测试用）。 */
    public static Map<String, List<String>> declaredPropNames() {
        Map<String, List<String>> declared = new LinkedHashMap<>();
        for (Map.Entry<UiPrimitive, Set<UiProp>> entry : allowedProps().entrySet()) {
            List<String> names = new ArrayList<>();
            for (UiProp prop : entry.getValue()) {
                names.add(prop.jsName());
            }
            declared.put(entry.getKey().tag(), List.copyOf(names));
        }
        return declared;
    }

    /** 声明中被承诺的事件属性名（漂移测试用）。 */
    public static List<String> declaredEventProps() {
        return eventProps();
    }

    /** 声明中被承诺的事件类型名（漂移测试用）。 */
    public static List<String> declaredEventTypes() {
        return eventNames();
    }

    // ---- 内部 ----

    private static Map<UiPrimitive, Set<UiProp>> allowedProps() {
        Map<UiPrimitive, Set<UiProp>> map = new EnumMap<>(UiPrimitive.class);
        for (UiPrimitive primitive : UiPrimitive.values()) {
            map.put(primitive, UiPrimitiveRegistry.allowedProps(primitive));
        }
        return map;
    }

    private static String renderPropList(Set<UiProp> props) {
        Map<UiPropCategory, List<String>> byCategory = new EnumMap<>(UiPropCategory.class);
        for (UiProp prop : props) {
            byCategory.computeIfAbsent(prop.category(), ignored -> new ArrayList<>()).add(prop.jsName());
        }
        StringBuilder sb = new StringBuilder();
        for (UiPropCategory category : UiPropCategory.values()) {
            List<String> names = byCategory.get(category);
            if (names == null || names.isEmpty()) continue;
            sb.append(" /* ").append(category.name().toLowerCase()).append(" */");
            for (String name : new TreeSet<>(names)) {
                sb.append(" readonly ").append(name).append("?: ").append(tsTypeOf(name)).append(";");
            }
        }
        return sb.toString();
    }

    private static String tsTypeOf(String propName) {
        UiProp prop = UiProp.fromJsName(propName);
        if (prop == null) return "unknown";
        if (prop.isEvent()) return "(event: NekoJsx.UiEvent) => void | null";
        return switch (prop) {
            case TEXT, VALUE, PLACEHOLDER, TITLE, NARRATION, TOOLTIP, SOURCE, FONT,
                 COLOR, BACKGROUND, BORDER -> "string";
            case FIT -> "'stretch' | 'contain' | 'cover' | 'repeat'";
            case WRAP, VISIBLE, DISABLED, FOCUSED, HOVERED, PRESSED, LOADING, SCROLLABLE -> "boolean";
            case WIDTH, HEIGHT -> DIMENSION_TYPE;
            case MIN_WIDTH, MIN_HEIGHT, MAX_WIDTH, MAX_HEIGHT, MARGIN, PADDING, GAP, GROW,
                 RADIUS, OPACITY, FONT_SIZE, SCROLL_X, SCROLL_Y, MAX_LENGTH -> "number";
            case ALIGN, JUSTIFY -> "NekoJsx.UiAlignment";
            case ANCHOR -> "NekoJsx.UiAnchor";
            case ERROR -> "string | boolean";
            default -> "unknown";
        };
    }

    /** 尺寸属性的 TS 类型（与 {@code UiDimension} 的受控写法一致）。 */
    public static final String DIMENSION_TYPE = "number | 'auto' | 'fill' | `${number}%`";

    private static List<String> alignmentValues() {
        List<String> values = new ArrayList<>();
        for (UiAlignment alignment : UiAlignment.values()) {
            values.add(alignment.jsName());
        }
        return values;
    }

    private static List<String> anchorValues() {
        List<String> values = new ArrayList<>();
        for (UiAnchor anchor : UiAnchor.values()) {
            values.add(anchor.jsName());
        }
        return values;
    }

    private static List<String> eventNames() {
        List<String> values = new ArrayList<>();
        for (UiEventType type : UiEventType.values()) {
            values.add(type.jsName());
        }
        return values;
    }

    private static List<String> eventProps() {
        List<String> values = new ArrayList<>();
        for (UiProp prop : UiProp.values()) {
            if (prop.isEvent()) values.add(prop.jsName());
        }
        return values;
    }

    private static String literalUnion(List<String> values) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) sb.append(" | ");
            sb.append('\'').append(values.get(i)).append('\'');
        }
        return sb.toString();
    }
}
