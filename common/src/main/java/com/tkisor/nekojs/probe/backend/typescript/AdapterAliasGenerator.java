package com.tkisor.nekojs.probe.backend.typescript;

import com.tkisor.nekojs.api.AdapterInputShape;
import com.tkisor.nekojs.api.AdapterInputShape.Slot;
import com.tkisor.nekojs.api.catalog.AdapterCatalogEntry;
import com.tkisor.nekojs.probe.ir.TypeDecl;
import com.tkisor.nekojs.probe.ir.TypeReflector;
import com.tkisor.nekojs.probe.ir.TypeScriptClassRenderer;
import com.tkisor.nekojs.probe.types.TypeAliasRegistry;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
    private final Map<String, AdapterCatalogEntry> entries = new LinkedHashMap<>();
    private final Map<String, List<TypeDecl.TypeParam>> parameters = new LinkedHashMap<>();
    private final Set<String> hostImports = new LinkedHashSet<>();

    public AdapterAliasGenerator(TypeAliasRegistry aliasRegistry) {
        this.aliasRegistry = aliasRegistry;
    }

    TypeAliasRegistry aliasRegistry() {
        return aliasRegistry;
    }

    /**
     * 解析适配器的形状，填充别名注册表与别名缓存。
     *
     * <p>分两遍：第一遍只登记 {@code targetType -> $Foo_} 映射，第二遍才渲染。这样嵌套位置
     * 引用别的适配器目标时（如 {@code SizedIngredient} 的 {@code ingredient} 槽位引用
     * {@code Ingredient}）不受适配器注册顺序影响。
     *
     * @param generatedClasses 本次会被实际生成声明类的全限定名集合；仅处理其中的适配器目标，
     *                         避免为未生成的目标注册别名而引入悬空类型引用
     */
    public void prepare(List<AdapterCatalogEntry> adapters, Set<String> generatedClasses) {
        prepare(adapters, generatedClasses, List.of());
    }

    /** Uses edited declarations when available; fallback reflection reads only generic parameters. */
    public void prepare(List<AdapterCatalogEntry> adapters, Set<String> generatedClasses,
                        List<TypeDecl> declarations) {
        aliases.clear();
        entries.clear();
        parameters.clear();
        hostImports.clear();
        Map<String, TypeDecl> declarationsByName = new LinkedHashMap<>();
        if (declarations != null) {
            for (TypeDecl declaration : declarations) declarationsByName.put(declaration.fqn, declaration);
        }

        List<AdapterCatalogEntry> eligible = new ArrayList<>();
        for (AdapterCatalogEntry entry : adapters) {
            if (entry.shapes().isEmpty()) continue;
            String fqn = entry.targetType().getName();
            if (!generatedClasses.contains(fqn)) continue;
            eligible.add(entry);
            TypeDecl declaration = declarationsByName.get(fqn);
            List<TypeDecl.TypeParam> typeParameters = declaration == null
                    ? TypeReflector.typeParametersOf(entry.targetType()) : List.copyOf(declaration.typeParams);
            parameters.put(fqn, typeParameters);
            aliasRegistry.registerAdapterAlias(fqn, "$" + tsClassName(entry.targetType()) + "_",
                    typeParameters.size());
        }

        for (AdapterCatalogEntry entry : eligible) {
            String fqn = entry.targetType().getName();
            entries.put(fqn, entry);
            AdapterAlias alias = renderAlias(entry, parameters.get(fqn));
            aliases.put(fqn, alias);
            // 收集别名引用的跨包 host 类型，供 orchestrator 确保它们也被生成，避免悬空 import
            for (String imp : alias.importFqns()) {
                if (!imp.equals(fqn)) hostImports.add(imp);
            }
        }
    }

    /**
     * 所有别名引用的跨包 host 类型全限定名（如 NekoId、Item），供 orchestrator 纳入生成集合。
     *
     * <p>返回按收集序的不可变快照：{@link Set#copyOf} 的迭代序无规范保证（跨 JVM/平台可能
     * 不同），直接暴露会破坏 probe 输出的确定性，故按插入序复制。
     */
    public Set<String> hostImports() {
        return java.util.Collections.unmodifiableSet(new LinkedHashSet<>(hostImports));
    }

    public boolean hasAlias(String fqn) {
        return aliases.containsKey(fqn);
    }

    public AdapterAlias getAlias(String fqn) {
        return aliases.get(fqn);
    }

    /** Re-renders canonical shapes when an imported host or input alias needs a local name. */
    public AdapterAlias getAlias(String fqn, TypeAliasRegistry moduleAliases) {
        AdapterCatalogEntry entry = entries.get(fqn);
        return entry == null ? null : new AdapterAliasGenerator(moduleAliases).renderAlias(entry, parameters.get(fqn));
    }

    private AdapterAlias renderAlias(AdapterCatalogEntry entry, List<TypeDecl.TypeParam> typeParameters) {
        Class<?> target = entry.targetType();
        String selfSimple = tsClassName(target);
        String selfPackage = target.getPackageName();
        Set<String> imports = new LinkedHashSet<>();
        Set<String> usedRegistries = new LinkedHashSet<>();
        TypeAliasRegistry scopedAliases = aliasRegistry.withReferences((fqn, name) -> {
            int separator = fqn.lastIndexOf('.');
            if (separator >= 0 && !fqn.substring(0, separator).equals(selfPackage)) imports.add(fqn);
            return aliasRegistry.reference(fqn, name);
        });
        List<String> declarations = new ArrayList<>();
        List<String> arguments = new ArrayList<>();
        for (TypeDecl.TypeParam parameter : typeParameters) {
            String declaration = parameter.name;
            if (parameter.bound != null) {
                declaration += " extends " + TypeScriptClassRenderer.renderTypeParameterBound(
                        parameter.bound.ref, parameter.name, scopedAliases, new LinkedHashSet<>());
            }
            // Raw Java uses historically accept erased values; explicit arguments still retain their bounds.
            declarations.add(declaration + " = any");
            arguments.add(parameter.name);
        }
        String argumentList = arguments.isEmpty() ? "" : "<" + String.join(", ", arguments) + ">";
        ShapeContext context = new ShapeContext(
                aliasRegistry.reference(target.getName(), "$" + selfSimple) + argumentList,
                aliasRegistry.reference(target.getName(), "$" + selfSimple + "_") + argumentList,
                selfPackage, imports, usedRegistries);
        Set<String> rendered = new LinkedHashSet<>();
        for (AdapterInputShape shape : entry.shapes()) {
            rendered.add(render(shape, context, false));
        }
        String parameterList = declarations.isEmpty() ? "" : "<" + String.join(", ", declarations) + ">";
        return new AdapterAlias("$" + selfSimple + "_", parameterList, String.join(" | ", rendered), imports,
                !usedRegistries.isEmpty(), entry.syntaxDoc());
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
            String typeParameters,
            String union,
            Set<String> importFqns,
            boolean usesRegistry,
            Optional<String> doc
    ) {}

    // ===================== 形状渲染 =====================

    private record ShapeContext(String hostSelf, String inputSelf, String selfPackage,
                                Set<String> imports, Set<String> usedRegistries) {}

    /**
     * @param nested 是否处于嵌套位置（数组元素 / 对象槽位 / 联合成员 / 模板洞）。嵌套位置的
     *               目标类型要放宽成输入别名 {@code $Foo_}：运行时那些位置同样经适配器转换，
     *               所以 {@code { any: ['minecraft:stone'] }} 之类的写法必须能通过类型检查。
     *               顶层不放宽——否则 {@code $Foo_ = $Foo_ | ...} 自引用成环。
     */
    private String render(AdapterInputShape shape, ShapeContext context, boolean nested) {
        return switch (shape) {
            case AdapterInputShape.StringValue v -> "string";
            case AdapterInputShape.LiteralValue v -> "\"" + v.text().replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
            case AdapterInputShape.NumberValue v -> "number";
            case AdapterInputShape.BooleanValue v -> "boolean";
            case AdapterInputShape.SelfValue v -> nested ? context.inputSelf() : context.hostSelf();
            case AdapterInputShape.HostValue v -> {
                Class<?> hostCls = v.cls();
                // 同包目标已在当前模块声明，无需 import；跨包才收集
                String hostPkg = hostCls.getPackage() != null ? hostCls.getPackage().getName() : "";
                if (!hostPkg.equals(context.selfPackage())) {
                    context.imports().add(hostCls.getName());
                }
                String hostAlias = nested ? aliasRegistry.getAlias(hostCls.getName()) : null;
                yield hostAlias != null ? hostAlias : aliasRegistry.reference(hostCls.getName(), "$" + tsClassName(hostCls));
            }
            case AdapterInputShape.ArrayOfValue v ->
                    render(v.element(), context, true) + "[]";
            case AdapterInputShape.ObjectValue v ->
                    renderObject(v.slots(), context);
            case AdapterInputShape.UnionValue v -> {
                List<String> members = new ArrayList<>();
                for (AdapterInputShape member : v.members()) {
                    members.add(render(member, context, true));
                }
                // 恒加括号：槽位/数组元素位置都合法，且不依赖调用点自己判断优先级
                yield "(" + String.join(" | ", members) + ")";
            }
            case AdapterInputShape.RegistryValue v -> {
                context.usedRegistries().add(v.typeName());
                yield "RegistryTypes." + v.typeName();
            }
            case AdapterInputShape.RegistryTagValue v -> {
                context.usedRegistries().add(v.typeName());
                yield "RegistryTypes." + v.typeName() + "Tag";
            }
            case AdapterInputShape.NamespaceValue v -> {
                // 与注册表字面量同一模块（@special/types），复用同一条 import 触发
                context.usedRegistries().add("Namespace");
                yield "RegistryTypes.Namespace";
            }
            case AdapterInputShape.TemplateValue v -> "`" + v.prefix()
                    + "${" + render(v.hole(), context, true) + "}"
                    + v.suffix() + "`";
            case AdapterInputShape.RawValue v -> v.ts();
        };
    }

    private String renderObject(List<Slot> slots, ShapeContext context) {
        if (slots.isEmpty()) return "{ [key: string]: any }";
        List<String> parts = new ArrayList<>();
        for (Slot slot : slots) {
            String type = render(slot.shape(), context, true);
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
