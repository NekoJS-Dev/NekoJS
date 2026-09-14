package com.tkisor.nekojs.core.ui;

import com.tkisor.nekojs.api.ui.UiAlignment;
import com.tkisor.nekojs.api.ui.UiAnchor;
import com.tkisor.nekojs.api.ui.UiAxis;
import com.tkisor.nekojs.api.ui.UiEventType;
import com.tkisor.nekojs.api.ui.UiPrimitive;
import com.tkisor.nekojs.api.ui.UiProp;
import com.tkisor.nekojs.api.ui.UiPropCategory;
import com.tkisor.nekojs.api.ui.UiValueKind;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
        sb.append("  type UiDimensionInput = ").append(UiValueKind.DIMENSION.tsType()).append(";\n");
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
        sb.append("    readonly viewport: ProjectedViewport;\n");
        sb.append("    readonly viewportState: UiViewportState;\n");
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
        sb.append("  type ViewportProfileCode = ")
                .append(literalUnion(viewportProfileCodes())).append(";\n");
        sb.append("  type UiAxis = ").append(literalUnion(UiAxis.codes())).append(";\n");
        sb.append("  interface ProjectedViewport {\n");
        sb.append("    readonly profile: ViewportProfileCode;\n");
        sb.append("    readonly logicalWidth: number;\n");
        sb.append("    readonly logicalHeight: number;\n");
        sb.append("    readonly contentWidth: number;\n");
        sb.append("    readonly contentHeight: number;\n");
        sb.append("    readonly guiScale: number;\n");
        sb.append("    readonly capabilities: string[];\n");
        sb.append("    readonly revision: number;\n");
        sb.append("  }\n\n");
        sb.append("  interface UiViewportState {\n");
        sb.append("    readonly viewport: ProjectedViewport;\n");
        sb.append("    readonly profile: ViewportProfileCode;\n");
        sb.append("    readonly contentWidth: number;\n");
        sb.append("    readonly contentHeight: number;\n");
        sb.append("    readonly resolvedStyles: { readonly [nodePath: string]: UiResolvedStyle };\n");
        sb.append("  }\n\n");
        sb.append("  /** 最近一次 measure/arrange 解析出的最终布局约束（profile 覆盖与设计坐标已应用）。 */\n");
        sb.append("  interface UiResolvedStyle {\n");
        sb.append("    readonly width: ").append(UiValueKind.DIMENSION.tsType()).append(";\n");
        sb.append("    readonly height: ").append(UiValueKind.DIMENSION.tsType()).append(";\n");
        sb.append("    readonly minWidth: number;\n");
        sb.append("    readonly minHeight: number;\n");
        sb.append("    readonly maxWidth: number;\n");
        sb.append("    readonly maxHeight: number;\n");
        sb.append("    readonly margin: number;\n");
        sb.append("    readonly padding: number;\n");
        sb.append("    readonly gap: number;\n");
        sb.append("    readonly align: UiAlignment;\n");
        sb.append("    readonly justify: UiAlignment;\n");
        sb.append("    readonly anchor: UiAnchor;\n");
        sb.append("    readonly grow: number;\n");
        sb.append("    readonly visible: boolean;\n");
        sb.append("    readonly direction: UiAxis | null;\n");
        sb.append("    readonly contentScale: number;\n");
        sb.append("    readonly scrollX: number;\n");
        sb.append("    readonly scrollY: number;\n");
        sb.append("    readonly font: string | null;\n");
        sb.append("    readonly wrap: boolean | null;\n");
        sb.append("  }\n\n");
        // 覆盖包类型由 UiProfileOverride.KEYS 派生：键表变化会自动反映到声明，
        // 且每个键的类型文本来自 UiProp.kind()，不存在第二份「键 → 类型」表。
        for (String line : overrideTypeDeclaration()) {
            sb.append(line);
        }
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

    /**
     * 把 JSX 声明注册进既有 TypeDocsRegister 链（Probe/workspace 派生物的唯一入口）。
     *
     * <p>这是本生成器的<b>生产可达路径</b>：{@code NekoCommonBuiltinPlugin.registerNodeTypeDocs}
     * 调用它，声明随 probe 输出落进脚本 workspace。证据/测试只读该入口的产物，
     * 不直接写生产目录。
     *
     * @param registry 既有的类文档注册面（不新增 Point、不新增声明 DSL）
     */
    public static void register(com.tkisor.nekojs.core.plugin.TypeDocsRegister registry) {
        registry.registerManualDeclaration(com.tkisor.nekojs.api.catalog.ManualDeclarationCatalogEntry.of(
                "nekojs.jsx-ui.elements",
                generate(),
                "NekoJS JSX UI intrinsic elements, props, events and facade types "
                        + "(generated from UiProp/UiPrimitiveRegistry).",
                java.util.List.of(
                        "const root = Ui.open({ render: () => <screen><column>"
                                + "<label text='hi'/></column></screen> })")));
        registry.registerManualDeclaration(com.tkisor.nekojs.api.catalog.ManualDeclarationCatalogEntry.of(
                "nekojs.jsx-ui.runtime",
                generateRuntimeModule(),
                "Automatic JSX runtime module ('nekojs/jsx-runtime') declaration.",
                java.util.List.of("import { jsx, jsxs, Fragment } from 'nekojs/jsx-runtime'")));
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
        Map<UiPropCategory, List<UiProp>> byCategory = new EnumMap<>(UiPropCategory.class);
        for (UiProp prop : props) {
            byCategory.computeIfAbsent(prop.category(), ignored -> new ArrayList<>()).add(prop);
        }
        StringBuilder sb = new StringBuilder();
        for (UiPropCategory category : UiPropCategory.values()) {
            List<UiProp> members = byCategory.get(category);
            if (members == null || members.isEmpty()) continue;
            sb.append(" /* ").append(category.name().toLowerCase()).append(" */");
            List<UiProp> sorted = new ArrayList<>(members);
            sorted.sort(java.util.Comparator.comparing(UiProp::jsName));
            for (UiProp prop : sorted) {
                sb.append(" readonly ").append(prop.jsName())
                        .append("?: ").append(tsTypeOf(prop)).append(";");
            }
        }
        return sb.toString();
    }

    /**
     * 属性类型文本：直接读 {@link UiProp#kind()}（唯一事实源）。
     *
     * <p>本生成器不再持有第二份「属性名 → 类型」表——新增属性只需在 {@code UiProp} 上
     * 加一行，取值校验器与本处自动同步（一致性由 {@code UiContractChainTest} 钉住）。
     */
    private static String tsTypeOf(UiProp prop) {
        return prop.kind().tsType();
    }

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

    /**
     * 渲染 {@code UiProfileOverride} 类型定义（键表来自 {@link UiProfileOverride#KEYS}）。
     *
     * <p>键到类型的映射直接查 {@link UiProp#kind()}（与 runtime 取值校验同源）；没有对应
     * {@code UiProp} 的键（{	t family scrollX/scrollY 有，font/wrap 也有）不可能出现——
     * 因此声明不会引用 runtime 不认识的名字。
     */
    private static List<String> overrideTypeDeclaration() {
        List<String> lines = new ArrayList<>();
        lines.add("  /** Viewport Profile 覆盖包：1-6 档（或档位码）到受控属性覆盖的映射。 */\n");
        lines.add("  interface UiProfileOverride {\n");
        lines.add("    readonly [key: string]: unknown;\n");
        for (String key : com.tkisor.nekojs.api.ui.UiProfileOverride.KEYS) {
            UiProp prop = UiProp.fromJsName(key);
            String type = prop != null ? prop.kind().tsType() : "unknown";
            lines.add("    readonly " + key + "?: " + type + ";\n");
        }
        lines.add("  }\n\n");
        return lines;
    }

    private static List<String> viewportProfileCodes() {
        return com.tkisor.nekojs.api.ui.ViewportProfile.codes();
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