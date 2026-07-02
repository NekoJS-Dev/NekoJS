package com.tkisor.nekojs.probe;

import com.tkisor.nekojs.api.AdapterInputShape;
import com.tkisor.nekojs.api.AdapterInputShape.Slot;
import com.tkisor.nekojs.api.catalog.AdapterCatalogEntry;
import com.tkisor.nekojs.probe.types.TypeAliasRegistry;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 适配器驱动的输入别名生成器。
 *
 * <p>消费 {@link AdapterCatalogEntry#getShapes() 适配器声明的形状}，渲染成 TypeScript
 * 输入别名（{@code $Foo_}），并：
 * <ol>
 *   <li>把 {@code targetType -> $Foo_} 注册进 {@link TypeAliasRegistry}，使 {@link TypeConverter}
 *       自动放宽所有引用该类型的方法参数（补上原有"别名生成了却没接到参数"的缺陷）；</li>
 *   <li>按目标类全限定名缓存 {@link AdapterAlias}（别名联合类型 + 依赖的 import），
 *       供 {@link IndexFileGenerator} 在目标类所在包模块内就近发 {@code export type} 声明。</li>
 * </ol>
 *
 * <p>必须在 {@link IndexFileGenerator#pregenerateClass} 与参数渲染之前调用 {@link #prepare}。
 */
public final class AdapterAliasGenerator {
    private final TypeAliasRegistry aliasRegistry;
    private final Map<String, AdapterAlias> aliases = new LinkedHashMap<>();
    private final Set<String> hostImports = new LinkedHashSet<>();

    public AdapterAliasGenerator(TypeAliasRegistry aliasRegistry) {
        this.aliasRegistry = aliasRegistry;
    }

    /**
     * 解析适配器的形状，填充别名注册表与别名缓存。
     *
     * @param generatedClasses 本次会被实际生成声明类的全限定名集合；仅处理其中的适配器目标，
     *                         避免为未生成的目标注册别名而引入悬空类型引用
     */
    public void prepare(List<AdapterCatalogEntry> adapters, Set<String> generatedClasses) {
        aliases.clear();
        hostImports.clear();
        for (AdapterCatalogEntry entry : adapters) {
            if (entry.shapes().isEmpty()) continue;
            Class<?> target = entry.targetType();
            String fqn = target.getName();
            if (!generatedClasses.contains(fqn)) continue;
            String selfSimple = tsClassName(target);
            String selfPackage = target.getPackage() != null ? target.getPackage().getName() : "";

            Set<String> imports = new LinkedHashSet<>();
            Set<String> usedRegistries = new LinkedHashSet<>();
            Set<String> rendered = new LinkedHashSet<>();
            for (AdapterInputShape shape : entry.shapes()) {
                rendered.add(render(shape, selfSimple, selfPackage, imports, usedRegistries));
            }
            if (rendered.isEmpty()) continue;

            String union = String.join(" | ", rendered);
            String aliasName = "$" + selfSimple + "_";
            aliases.put(fqn, new AdapterAlias(aliasName, union, imports, !usedRegistries.isEmpty()));
            aliasRegistry.registerClassAlias(fqn, aliasName);
            // 收集别名引用的跨包 host 类型，供 orchestrator 确保它们也被生成，避免悬空 import
            for (String imp : imports) {
                if (!imp.equals(fqn)) hostImports.add(imp);
            }
        }
    }

    /**
     * 所有别名引用的跨包 host 类型全限定名（如 NekoId、Item），供 orchestrator 纳入生成集合。
     */
    public Set<String> hostImports() {
        return Set.copyOf(hostImports);
    }

    public boolean hasAlias(String fqn) {
        return aliases.containsKey(fqn);
    }

    public AdapterAlias getAlias(String fqn) {
        return aliases.get(fqn);
    }

    /**
     * 返回目标类的输入别名名（如 {@code $ItemStack_}），无别名时返回 null。
     * 供其它生成器在 import 列表中追加别名（参数放宽后会引用 $Foo_）。
     */
    public String aliasNameOf(String fqn) {
        AdapterAlias alias = aliases.get(fqn);
        return alias == null ? null : alias.aliasName();
    }

    /** 单个适配器目标的渲染结果。 */
    public record AdapterAlias(
            String aliasName,
            String union,
            Set<String> importFqns,
            boolean usesRegistry
    ) {}

    // ===================== 形状渲染 =====================

    private String render(AdapterInputShape shape, String selfSimple, String selfPackage,
                          Set<String> imports, Set<String> usedRegistries) {
        return switch (shape) {
            case AdapterInputShape.StringValue v -> "string";
            case AdapterInputShape.NumberValue v -> "number";
            case AdapterInputShape.BooleanValue v -> "boolean";
            case AdapterInputShape.SelfValue v -> "$" + selfSimple;
            case AdapterInputShape.HostValue v -> {
                Class<?> hostCls = v.cls();
                // 同包目标已在当前模块声明，无需 import；跨包才收集
                String hostPkg = hostCls.getPackage() != null ? hostCls.getPackage().getName() : "";
                if (!hostPkg.equals(selfPackage)) {
                    imports.add(hostCls.getName());
                }
                yield "$" + tsClassName(hostCls);
            }
            case AdapterInputShape.ArrayOfValue v ->
                    render(v.element(), selfSimple, selfPackage, imports, usedRegistries) + "[]";
            case AdapterInputShape.ObjectValue v ->
                    renderObject(v.slots(), selfSimple, selfPackage, imports, usedRegistries);
            case AdapterInputShape.RegistryValue v -> {
                usedRegistries.add(v.typeName());
                yield "RegistryTypes." + v.typeName();
            }
            case AdapterInputShape.RawValue v -> v.ts();
        };
    }

    private String renderObject(List<Slot> slots, String selfSimple, String selfPackage,
                                Set<String> imports, Set<String> usedRegistries) {
        if (slots.isEmpty()) return "{ [key: string]: any }";
        List<String> parts = new ArrayList<>();
        for (Slot slot : slots) {
            String type = render(slot.shape(), selfSimple, selfPackage, imports, usedRegistries);
            parts.add(quoteKey(slot.name()) + (slot.required() ? "" : "?") + ": " + type);
        }
        return "{ " + String.join(", ", parts) + " }";
    }

    /** 非合法标识符的 key 用引号包裹。 */
    private static String quoteKey(String name) {
        if (name.isEmpty()) return "\"\"";
        if (!Character.isJavaIdentifierStart(name.charAt(0))) return "\"" + name + "\"";
        for (int i = 1; i < name.length(); i++) {
            if (!Character.isJavaIdentifierPart(name.charAt(i))) return "\"" + name + "\"";
        }
        return name;
    }

    /** 类的 TypeScript 标识符名（内部类用 Parent$Child 格式，与各生成器一致）。 */
    private static String tsClassName(Class<?> cls) {
        if (cls.getEnclosingClass() != null && !cls.isAnonymousClass()) {
            return tsClassName(cls.getEnclosingClass()) + "$" + cls.getSimpleName();
        }
        return cls.getSimpleName();
    }
}
