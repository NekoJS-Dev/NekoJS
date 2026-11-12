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
            // Same-package references also need declarations even though they require no imports.
            for (String imp : alias.referenceFqns()) {
                if (!imp.equals(fqn)) hostImports.add(imp);
            }
        }
    }

    /**
     * Host types referenced by aliases, including same-package types, for declaration dependency completion.
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
        Set<String> references = new LinkedHashSet<>();
        Set<String> usedRegistries = new LinkedHashSet<>();
        TypeAliasRegistry scopedAliases = aliasRegistry.withReferences((fqn, name) -> {
            references.add(fqn);
            int separator = fqn.lastIndexOf('.');
            if (separator >= 0 && !fqn.substring(0, separator).equals(selfPackage)) imports.add(fqn);
            return aliasRegistry.reference(fqn, name);
        });
        List<String> declarations = new ArrayList<>();
        List<String> arguments = new ArrayList<>();
        for (TypeDecl.TypeParam parameter : typeParameters) {
            String declaration = TypeScriptClassRenderer.renderTypeParameter(parameter, scopedAliases);
            // Raw Java uses historically accept erased values; explicit arguments still retain their bounds.
            declarations.add(declaration + " = any");
            arguments.add(parameter.name);
        }
        String argumentList = arguments.isEmpty() ? "" : "<" + String.join(", ", arguments) + ">";
        String parameterList = declarations.isEmpty() ? "" : "<" + String.join(", ", declarations) + ">";
        NamedScope named = new NamedScope("$" + selfSimple, parameterList, argumentList,
                entry.shapes().stream().noneMatch(AdapterAliasGenerator::containsOpaqueNewline));
        ShapeContext context = new ShapeContext(
                aliasRegistry.reference(target.getName(), "$" + selfSimple) + argumentList,
                aliasRegistry.reference(target.getName(), "$" + selfSimple + "_") + argumentList,
                selfPackage, imports, usedRegistries, references, true, named);
        Set<String> rendered = new LinkedHashSet<>();
        for (AdapterInputShape shape : entry.shapes()) {
            rendered.add(render(shape, context, false));
        }
        String union = String.join(" | ", rendered);
        String formatted = named.aliases.isEmpty() || !named.pretty ? union : "\n    | " + String.join("\n    | ", rendered);
        return new AdapterAlias("$" + selfSimple + "_", parameterList, union, imports,
                !usedRegistries.isEmpty(), entry.syntaxDoc(), references, List.copyOf(named.aliases.values()), formatted);
    }

    /**
     * 返回目标类的输入别名名（如 {@code $ItemStack_}），无别名时返回 null。
     * 供其它生成器在 import 列表中追加别名（参数放宽后会引用 $Foo_）。
     */
    public String aliasNameOf(String fqn) {
        AdapterAlias alias = aliases.get(fqn);
        return alias == null ? null : alias.aliasName();
    }

    /** One adapter input alias and the reusable sections emitted beside it. */
    public record AdapterAlias(
            String aliasName,
            String typeParameters,
            String union,
            Set<String> importFqns,
            boolean usesRegistry,
            Optional<String> doc,
            Set<String> referenceFqns,
            List<NamedAlias> namedAliases,
            String formattedUnion
    ) {
        public AdapterAlias {
            namedAliases = List.copyOf(namedAliases);
        }

        public AdapterAlias(String aliasName, String typeParameters, String union, Set<String> importFqns,
                            boolean usesRegistry, Optional<String> doc, Set<String> referenceFqns) {
            this(aliasName, typeParameters, union, importFqns, usesRegistry, doc, referenceFqns, List.of(), union);
        }

        public AdapterAlias(String aliasName, String typeParameters, String union, Set<String> importFqns,
                            boolean usesRegistry, Optional<String> doc) {
            this(aliasName, typeParameters, union, importFqns, usesRegistry, doc, importFqns);
        }

        /** Emits the complete declaration group, including every referenced local section. */
        public String declarations(String indent) {
            // Opaque multiline type text may contain literal newlines; never indent inside it.
            if (formattedUnion.equals(union)) {
                StringBuilder inline = new StringBuilder(indent).append("export type ").append(aliasName)
                        .append(typeParameters).append(" = ").append(union).append(";\n");
                for (NamedAlias named : namedAliases) {
                    inline.append("\n").append(indent).append("export type ").append(named.aliasName())
                            .append(named.typeParameters()).append(" = ").append(named.type()).append(";\n");
                }
                return inline.toString();
            }
            StringBuilder result = new StringBuilder();
            appendDeclaration(result, indent, aliasName, typeParameters, formattedUnion);
            for (NamedAlias named : namedAliases) {
                result.append((char) 10);
                appendDeclaration(result, indent, named.aliasName(), named.typeParameters(), named.type());
            }
            return result.toString();
        }
    }

    public record NamedAlias(String aliasName, String typeParameters, String type) {}

    private static void appendDeclaration(StringBuilder result, String indent, String name, String parameters, String type) {
        String text = "export type " + name + parameters + " =" + (type.startsWith("\n") ? "" : " ") + type + ";";
        for (String line : text.split("\n", -1)) result.append(indent).append(line).append('\n');
    }

    /** Renders explicit callable shapes with the same host references as adapter aliases. */
    public static RenderedShape renderShape(AdapterInputShape shape, TypeAliasRegistry aliases, boolean input) {
        Set<String> references = new LinkedHashSet<>();
        Set<String> registries = new LinkedHashSet<>();
        ShapeContext context = new ShapeContext(null, null, "", new LinkedHashSet<>(), registries, references, input, null);
        String type = new AdapterAliasGenerator(aliases).render(shape, context, true);
        return new RenderedShape(type, java.util.Collections.unmodifiableSet(references), !registries.isEmpty());
    }

    public record RenderedShape(String type, Set<String> referenceFqns, boolean usesRegistry) {}

    // ===================== 形状渲染 =====================

    private record ShapeContext(String hostSelf, String inputSelf, String selfPackage,
                                Set<String> imports, Set<String> usedRegistries, Set<String> references,
                                boolean inputHosts, NamedScope named) {}

    /**
     * @param nested 是否处于嵌套位置（数组元素 / 对象槽位 / 联合成员 / 模板洞）。嵌套位置的
     *               目标类型要放宽成输入别名 {@code $Foo_}：运行时那些位置同样经适配器转换，
     *               所以 {@code { any: ['minecraft:stone'] }} 之类的写法必须能通过类型检查。
     *               顶层不放宽——否则 {@code $Foo_ = $Foo_ | ...} 自引用成环。
     */
    private String render(AdapterInputShape shape, ShapeContext context, boolean nested) {
        return switch (shape) {
            case AdapterInputShape.NamedValue v -> renderNamed(v, context, nested);
            case AdapterInputShape.StringValue v -> "string";
            case AdapterInputShape.LiteralValue v -> "\"" + v.text().replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
            case AdapterInputShape.NumberValue v -> "number";
            case AdapterInputShape.BooleanValue v -> "boolean";
            case AdapterInputShape.SelfValue v -> java.util.Objects.requireNonNull(
                    nested ? context.inputSelf() : context.hostSelf(),
                    "NEKO-4063: Self input shape requires an adapter target");
            case AdapterInputShape.HostValue v -> {
                Class<?> hostCls = v.cls();
                context.references().add(hostCls.getName());
                // 同包目标已在当前模块声明，无需 import；跨包才收集
                String hostPkg = hostCls.getPackage() != null ? hostCls.getPackage().getName() : "";
                if (!hostPkg.equals(context.selfPackage())) {
                    context.imports().add(hostCls.getName());
                }
                String hostAlias = nested && context.inputHosts() ? aliasRegistry.getAlias(hostCls.getName()) : null;
                if (hostAlias != null) yield hostAlias;
                String host = aliasRegistry.reference(hostCls.getName(), "$" + tsClassName(hostCls));
                int arity = TypeReflector.typeParametersOf(hostCls).size();
                yield arity == 0 ? host : host + "<" + String.join(", ", java.util.Collections.nCopies(arity, "any")) + ">";
            }
            case AdapterInputShape.ArrayOfValue v -> {
                String element = render(v.element(), context, true);
                yield com.tkisor.nekojs.probe.types.TypeScriptTypeExpressions.arrayOf(element);
            }
            case AdapterInputShape.ObjectValue v ->
                    renderObject(v.slots(), context);
            case AdapterInputShape.UnionValue v -> {
                List<String> members = new ArrayList<>();
                for (AdapterInputShape member : v.members()) {
                    String rendered = render(member, context, true);
                    members.add(member instanceof AdapterInputShape.RawValue ? "(" + rendered + ")" : rendered);
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
            case AdapterInputShape.CallableValue v -> {
                ShapeContext parameterContext = new ShapeContext(context.hostSelf(), context.hostSelf(), context.selfPackage(),
                        context.imports(), context.usedRegistries(), context.references(), false, context.named());
                List<String> parameters = new ArrayList<>();
                for (Slot parameter : v.parameters()) {
                    parameters.add(parameter.name() + ": " + render(parameter.shape(), parameterContext, true));
                }
                yield "((" + String.join(", ", parameters) + ") => " + render(v.result(), context, true) + ")";
            }
            case AdapterInputShape.RawValue v -> v.ts();
        };
    }

    private static final class NamedScope {
        private final String prefix;
        private final String parameters;
        private final String arguments;
        private final boolean pretty;
        private final Map<String, NamedAlias> aliases = new LinkedHashMap<>();
        private final Set<String> rendering = new LinkedHashSet<>();

        private NamedScope(String prefix, String parameters, String arguments, boolean pretty) {
            this.prefix = prefix;
            this.parameters = parameters;
            this.arguments = arguments;
            this.pretty = pretty;
        }
    }

    private String renderNamed(AdapterInputShape.NamedValue named, ShapeContext context, boolean nested) {
        NamedScope scope = context.named();
        if (scope == null) return render(named.shape(), context, nested);
        String name = scope.prefix + named.suffix() + "_";
        if (!scope.rendering.add(name)) {
            throw new IllegalArgumentException("[NEKO-4063] Recursive named input section: " + name);
        }
        NamedAlias previous = scope.aliases.putIfAbsent(name, null);
        String type;
        try {
            type = renderNamedBody(named.shape(), context, nested);
        } finally {
            scope.rendering.remove(name);
        }
        NamedAlias declaration = new NamedAlias(name, scope.parameters, type);
        if (previous != null && !previous.equals(declaration)) {
            throw new IllegalArgumentException("[NEKO-4063] Conflicting named input section: " + name);
        }
        scope.aliases.put(name, declaration);
        return name + scope.arguments;
    }

    private String renderNamedBody(AdapterInputShape shape, ShapeContext context, boolean nested) {
        if (!context.named().pretty) return render(shape, context, nested);
        if (shape instanceof AdapterInputShape.ObjectValue object && !object.slots().isEmpty()) {
            StringBuilder result = new StringBuilder("{\n");
            for (Slot slot : object.slots()) {
                result.append("    ").append(quoteKey(slot.name())).append(slot.required() ? "" : "?")
                        .append(": ").append(render(slot.shape(), context, true)).append(";\n");
            }
            return result.append('}').toString();
        }
        if (shape instanceof AdapterInputShape.UnionValue union) {
            List<AdapterInputShape> members = new ArrayList<>();
            flattenUnion(union, members);
            boolean multiline = members.stream().anyMatch(AdapterInputShape.ObjectValue.class::isInstance);
            List<String> types = new ArrayList<>();
            for (AdapterInputShape member : members) {
                String type = multiline ? renderNamedBody(member, context, true) : render(member, context, true);
                if (member instanceof AdapterInputShape.RawValue && !type.matches("[A-Za-z_$][A-Za-z0-9_$.]*")) {
                    type = "(" + type + ")";
                }
                types.add(multiline ? type.replace("\n", "\n    ") : type);
            }
            return multiline ? "\n    | " + String.join("\n    | ", types) : String.join(" | ", types);
        }
        return render(shape, context, nested);
    }

    private static boolean containsOpaqueNewline(AdapterInputShape shape) {
        if (shape instanceof AdapterInputShape.RawValue raw) return raw.ts().contains("\n") || raw.ts().contains("\r");
        if (shape instanceof AdapterInputShape.NamedValue named) return containsOpaqueNewline(named.shape());
        if (shape instanceof AdapterInputShape.ArrayOfValue array) return containsOpaqueNewline(array.element());
        if (shape instanceof AdapterInputShape.UnionValue union) return union.members().stream().anyMatch(AdapterAliasGenerator::containsOpaqueNewline);
        if (shape instanceof AdapterInputShape.ObjectValue object) return object.slots().stream().anyMatch(slot -> containsOpaqueNewline(slot.shape()));
        if (shape instanceof AdapterInputShape.TemplateValue template) {
            return template.prefix().contains("\n") || template.suffix().contains("\n") || containsOpaqueNewline(template.hole());
        }
        if (shape instanceof AdapterInputShape.CallableValue callable) {
            return containsOpaqueNewline(callable.result())
                    || callable.parameters().stream().anyMatch(slot -> containsOpaqueNewline(slot.shape()));
        }
        return false;
    }

    private static void flattenUnion(AdapterInputShape shape, List<AdapterInputShape> members) {
        if (shape instanceof AdapterInputShape.UnionValue union) {
            for (AdapterInputShape member : union.members()) flattenUnion(member, members);
        } else {
            members.add(shape);
        }
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
