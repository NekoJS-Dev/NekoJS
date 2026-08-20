package com.tkisor.nekojs.probe.ir;

import com.tkisor.nekojs.probe.backend.typescript.IndexFileGenerator;
import com.tkisor.nekojs.api.surface.ApiTypeRef;
import com.tkisor.nekojs.probe.types.TypeAliasRegistry;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * {@link TypeDecl} → TypeScript 类/接口/枚举声明块（不含 {@code declare module} 外壳）。
 *
 * <p>类型槽**唯一**经 {@link TypeSlot#ref}（{@link ApiTypeRef}）渲染——TS 与 Python 共用同一份
 * 类型语义（映射在 {@link TypeReflector#toRef}）；{@code sourceType} 不参与渲染（仅排序/溯源）。
 *
 * <p>参数位置（{@code input=true}）应用输入别名放宽（适配器/枚举/集合别名，查
 * {@link TypeAliasRegistry}），语义与旧 TypeConverter 逐项对齐。
 *
 * <p>getter 覆盖表（{@link #overrideGetter}）镜像 {@code ClassDeclGenerator.overrideGetter}：
 * {@code probe.modify_type}/{@code probe.assign_type} 把类标记 mutated 后改走本 renderer 重渲染，
 * 旧路径上注册的 getter 覆盖（如 RecipeEventJS.recipes → DocumentedRecipes）必须在此同样生效，
 * 否则覆盖会随重渲染丢失。
 */
public final class TypeScriptClassRenderer {
    private final TypeAliasRegistry aliases;

    // 覆盖 getter 的返回类型 (fqn -> getter 属性名 -> { returnType, importStatement })
    // 键是 getter 的**属性名**（如 "recipes"），与 ClassDeclGenerator 的 lookup 口径一致
    private final Map<String, Map<String, GetterOverride>> getterOverrides = new LinkedHashMap<>();

    public TypeScriptClassRenderer(TypeAliasRegistry aliases) {
        this.aliases = aliases;
    }

    /** getter 覆盖条目：返回类型字符串 + 附带 import 语句（import 由 IndexFileGenerator 合并，本类不消费）。 */
    public record GetterOverride(String returnType, String importStatement) {}

    /**
     * 覆盖某类 getter 的返回类型（键为 getter 属性名，镜像 {@code ClassDeclGenerator#overrideGetter}）。
     * 命中覆盖时 getter 段只发射 {@code get prop(): T}（跳过原方法名双发射与 setter），与旧实现逐字一致。
     */
    public void overrideGetter(Class<?> cls, String getterName, String returnType, String importStatement) {
        getterOverrides.computeIfAbsent(cls.getName(), k -> new LinkedHashMap<>())
                .put(getterName, new GetterOverride(returnType, importStatement));
    }

    /**
     * 某类所有 getter 覆盖附带的 import 语句（供 IndexFileGenerator 在写包级 index.d.ts 时合并）。
     * 镜像旧 {@code ClassDeclGenerator#getExtraImports}。
     */
    public Set<String> getExtraImports(String className) {
        Map<String, GetterOverride> overrides = getterOverrides.get(className);
        if (overrides == null || overrides.isEmpty()) return Set.of();
        Set<String> imports = new LinkedHashSet<>();
        for (GetterOverride override : overrides.values()) {
            if (override.importStatement() != null && !override.importStatement().isEmpty()) {
                imports.add(override.importStatement());
            }
        }
        return imports;
    }

    public String render(TypeDecl decl) {
        if (decl.hidden) return "";
        return switch (decl.kind) {
            case INTERFACE -> renderInterface(decl);
            case ENUM -> renderEnum(decl);
            default -> renderClass(decl);
        };
    }

    private String renderClass(TypeDecl d) {
        StringBuilder sb = new StringBuilder();
        appendDoc(sb, "    ", d.docs);
        sb.append("    export class $").append(effectiveClassName(d));
        appendTypeParameters(sb, d);

        if (d.superType != null) {
            String superTs = renderSlot(d.superType, false);
            // 父类槽经 TypeConverter 可能映射为 TS 原始类型（Number 子类 → number，即 $Double extends number），
            // 经 probe 编辑（changeSuper/assign_type）还可能是联合/回调（extends string | number）——
            // 这些都不能作 heritage，省略整个 extends 子句（implements 等照常保留）
            if (isHeritageSafe(superTs)) {
                sb.append(" extends ").append(superTs);
            }
        }
        // implements 条目经 probe.assign_type 可被重写为任意 ref（primitive/union/回调）——不安全条目逐条
        // 省略（见 isHeritageSafe），其余条目与整个子句的存续照常保留
        List<String> implemented = renderHeritageEntries(d.interfaces);
        if (!implemented.isEmpty()) {
            sb.append(" implements ").append(String.join(", ", implemented));
        }
        sb.append(" {\n");

        for (MethodDecl c : d.constructors) {
            if (!c.hidden) sb.append(formatConstructor(c));
        }
        for (FieldDecl f : d.fields) {
            if (!f.hidden && f.isStatic) sb.append(formatField(f, true));
        }
        for (FieldDecl f : d.fields) {
            if (!f.hidden && !f.isStatic) sb.append(formatField(f, false));
        }
        // getter 段：get prop() + 原方法名() 双发射 + setter
        for (MethodDecl m : d.methods) {
            if (m.hidden || !m.isGetter) continue;
            // 属性名非合法 TS 标识符（数字开头，如 get2DigitYearStart → 2DigitYearStart）：
            // 只渲染原方法名（getter 标记使方法段排除它，这里降级补发），脚本仍可调用
            if (!isValidTsIdentifier(m.property)) {
                appendDoc(sb, "        ", m.docs);
                sb.append("        ").append(m.effectiveName()).append("(): ")
                  .append(renderSlot(m.returnType, false)).append(";\n");
                continue;
            }
            appendDoc(sb, "        ", m.docs);
            // getter 覆盖（如 RecipeEventJS.recipes → DocumentedRecipes）：镜像 ClassDeclGenerator，
            // 命中时只发射 get 行，跳过双发射与 setter
            GetterOverride ov = getterOverrides.getOrDefault(d.fqn, Map.of()).get(m.property);
            if (ov != null) {
                sb.append("        get ").append(m.property).append("(): ").append(ov.returnType()).append(";\n");
                continue;
            }
            String type = renderSlot(m.returnType, false);
            sb.append("        get ").append(m.property).append("(): ").append(type).append(";\n");
            sb.append("        ").append(m.effectiveName()).append("(): ").append(type).append(";\n");
            if (m.setterParamType != null) {
                sb.append("        set ").append(m.property).append("(value: ")
                  .append(renderSlot(m.setterParamType, true)).append(");\n");
            }
        }
        // 静态方法（与旧实现一致：不排除 getter/setter；getter 本就非静态，不会进这里）
        for (MethodDecl m : d.methods) {
            if (m.hidden || m.isConstructor || !m.isStatic) continue;
            sb.append(formatMethod(m, true));
        }
        // 实例方法（排除 getter/setter/构造器/静态）
        for (MethodDecl m : d.methods) {
            if (m.hidden || m.isConstructor || m.isStatic || m.isGetter || m.isSetter) continue;
            sb.append(formatMethod(m, false));
        }
        sb.append("    }\n");
        return sb.toString();
    }

    private String renderInterface(TypeDecl d) {
        StringBuilder sb = new StringBuilder();
        appendDoc(sb, "    ", d.docs);
        sb.append("    export interface $").append(effectiveClassName(d));
        appendTypeParameters(sb, d);
        if (!d.interfaces.isEmpty()) {
            // 接口 extends 列表与 class implements 同样经 assign_type 可被注入不安全 ref（extends string），
            // 逐条过滤（见 isHeritageSafe）
            List<String> extended = renderHeritageEntries(d.interfaces);
            if (!extended.isEmpty()) {
                sb.append(" extends ").append(String.join(", ", extended));
            }
        }
        sb.append(" {\n");
        for (MethodDecl m : d.methods) {
            if (!m.hidden) sb.append(formatMethod(m, false));
        }
        for (FieldDecl f : d.fields) {
            if (!f.hidden && f.isStatic && f.isFinal) sb.append(formatField(f, true));
        }
        sb.append("    }\n");
        return sb.toString();
    }

    private String renderEnum(TypeDecl d) {
        StringBuilder sb = new StringBuilder();
        String name = "$" + effectiveClassName(d);
        appendDoc(sb, "    ", d.docs);
        sb.append("    export class ").append(name).append(" {\n");
        for (FieldDecl f : d.fields) {
            if (!f.hidden && f.isEnumConstant) {
                appendDoc(sb, "        ", f.docs);
                sb.append("        static ").append(f.effectiveName()).append(": ").append(name).append(";\n");
            }
        }
        sb.append("        name(): string;\n");
        sb.append("        ordinal(): number;\n");
        sb.append("        toString(): string;\n");
        sb.append("        static values(): ").append(name).append("[];\n");
        sb.append("        static valueOf(name: string): ").append(name).append(";\n");
        sb.append("    }\n");
        return sb.toString();
    }

    private void appendTypeParameters(StringBuilder sb, TypeDecl d) {
        if (d.typeParams.isEmpty()) return;
        sb.append("<");
        sb.append(d.typeParams.stream().map(tp -> {
            String s = tp.name;
            if (tp.bound != null) s += " extends " + renderSlot(tp.bound, false);
            return s;
        }).collect(Collectors.joining(", ")));
        sb.append(">");
    }

    private String formatConstructor(MethodDecl c) {
        StringBuilder sb = new StringBuilder();
        appendDoc(sb, "        ", c.docs);
        sb.append("        constructor(");
        appendParameters(sb, c.params);
        sb.append(");\n");
        return sb.toString();
    }

    private String formatField(FieldDecl f, boolean isStatic) {
        StringBuilder sb = new StringBuilder();
        appendDoc(sb, "        ", f.docs);
        sb.append("        ");
        if (isStatic) sb.append("static ");
        sb.append(f.effectiveName()).append(": ").append(renderSlot(f.type, false)).append(";\n");
        return sb.toString();
    }

    private String formatMethod(MethodDecl m, boolean isStatic) {
        StringBuilder sb = new StringBuilder();
        appendDoc(sb, "        ", m.docs);
        sb.append("        ");
        if (isStatic) sb.append("static ");
        sb.append(m.effectiveName());
        if (!m.typeParams.isEmpty()) {
            sb.append("<").append(String.join(", ", m.typeParams)).append(">");
        }
        sb.append("(");
        appendParameters(sb, m.params);
        sb.append("): ").append(renderSlot(m.returnType, false)).append(";\n");
        return sb.toString();
    }

    private void appendParameters(StringBuilder sb, List<MethodDecl.MethodParam> params) {
        for (int i = 0; i < params.size(); i++) {
            MethodDecl.MethodParam p = params.get(i);
            if (i > 0) sb.append(", ");
            sb.append(tsParamName(p.name));
            if (p.varargs || p.optional) sb.append("?");
            sb.append(": ");
            sb.append(renderSlot(p.type, true));
            if (p.varargs) sb.append("[]");
        }
    }

    /** TS 参数名转义：JS/TS 保留字不能作参数名（如 Java 参数 {@code function}），追加 {@code _}。 */
    private static String tsParamName(String name) {
        if (name == null || name.isEmpty()) return "arg";
        return TS_RESERVED_WORDS.contains(name) ? name + "_" : name;
    }

    /** getter/setter 属性名是否为合法 TS 标识符（首字符须字母/下划线/$；数字开头如 {@code 2DigitYearStart} 非法）。 */
    private static boolean isValidTsIdentifier(String name) {
        if (name == null || name.isEmpty()) return false;
        char first = name.charAt(0);
        if (!(Character.isLetter(first) || first == '_' || first == '$')) return false;
        for (int i = 1; i < name.length(); i++) {
            char c = name.charAt(i);
            if (!(Character.isLetterOrDigit(c) || c == '_' || c == '$')) return false;
        }
        return true;
    }

    /** JS/TS 中不能作为参数名的保留字（含严格模式/上下文关键字，保守集合）。 */
    private static final Set<String> TS_RESERVED_WORDS = Set.of(
            "break", "case", "catch", "class", "const", "continue", "debugger", "default",
            "delete", "do", "else", "enum", "export", "extends", "false", "finally", "for",
            "function", "if", "import", "in", "instanceof", "new", "null", "return", "super",
            "switch", "this", "throw", "true", "try", "typeof", "var", "void", "while", "with",
            "let", "static", "yield", "await", "implements", "interface", "package", "private",
            "protected", "public", "arguments", "eval");

    /**
     * 渲染出的类型字符串是否是 TS 原始类型关键字（number/string/boolean 等）——这些不能出现在
     * {@code extends} 子句里（TypeConverter 会把 Number 子类映射为 number、String/Boolean 同理）。
     */
    private static boolean isTsPrimitiveKeyword(String tsType) {
        return switch (tsType) {
            case "number", "string", "boolean", "void", "object", "any" -> true;
            default -> false;
        };
    }

    /**
     * 渲染 interfaces 槽列表并逐条剔除 heritage 不安全条目；全被剔除时返回空列表（调用方省略整个子句）。
     */
    private List<String> renderHeritageEntries(List<TypeSlot> slots) {
        return slots.stream()
                .map(s -> renderSlot(s, false))
                .filter(TypeScriptClassRenderer::isHeritageSafe)
                .toList();
    }

    /**
     * 渲染出的类型字符串能否安全地出现在 heritage 位置（class 的 extends/implements、interface 的 extends）。
     * heritage 只接受普通类型引用，以下形态不安全：
     * <ul>
     *   <li>TS 原始类型关键字（含 any/void/object）——{@link #isTsPrimitiveKeyword}</li>
     *   <li>联合类型（含 {@code |}，渲染为 {@code A | B}）</li>
     *   <li>回调类型（含 {@code =>}，渲染为 {@code (...args: any[]) => any}）</li>
     * </ul>
     *
     * <p>可达路径：{@code probe.assign_type} 可把任一 SYMBOL 槽（含 implements / 接口 extends 条目）
     * 重写为任意 ref（如 {@code probe.assign("java.lang.CharSequence", "string")}）；
     * {@code probe.modify_type.changeSuper} 可把父类槽换成 union/回调/原始类型。
     *
     * <p><b>策略：省略（OMIT）而非失败</b>——不安全条目被丢弃、其余条目及声明主体照常渲染，
     * 保证产出声明语法有效。本 renderer 是纯函数、无日志通道，故以此注释记录该静默策略。
     */
    private static boolean isHeritageSafe(String renderedTs) {
        if (renderedTs == null || renderedTs.isBlank()) return false;
        if (isTsPrimitiveKeyword(renderedTs)) return false;
        return !renderedTs.contains("|") && !renderedTs.contains("=>");
    }

    /** 渲染类型槽：唯一路径 = ref（input=true 时应用输入别名放宽）。 */
    private String renderSlot(TypeSlot slot, boolean input) {
        if (slot == null || slot.ref == null) return "any";
        return renderTypeRef(slot.ref, aliases, input);
    }

    /** 简单 ApiTypeRef → TS（无别名表；probe.add_global 的全局声明类型等声明位使用）。 */
    public static String renderTypeRef(ApiTypeRef ref) {
        return renderTypeRef(ref, null, false);
    }

    /**
     * ApiTypeRef → TS。{@code input=true} 且提供别名表时应用输入别名放宽——语义与旧
     * TypeConverter 逐项对齐：参数化符号先查集合别名（结构化实参数组，防嵌套泛型被
     * {@code ", "} 拆坏），非参数化符号查类别名（适配器/枚举惰性别名）。
     */
    public static String renderTypeRef(ApiTypeRef ref, TypeAliasRegistry aliases, boolean input) {
        if (ref == null) return "any";
        return switch (ref.kind()) {
            case VOID -> "void";
            case PRIMITIVE -> mapPrimT(ref.name());
            case TYPE_VARIABLE -> ref.name();
            case ARRAY -> renderTypeRef(ref.arguments().get(0), aliases, input) + "[]";
            case UNION -> ref.arguments().stream()
                    .map(a -> renderTypeRef(a, aliases, input))
                    .collect(Collectors.joining(" | "));
            case SYMBOL -> renderSymbol(ref, aliases, input);
            case CALLBACK -> "(...args: any[]) => any";
        };
    }

    /** SYMBOL 渲染：input 别名（集合/类）优先，否则 {@code $Name<实参...>}（实参递归、input 传播）。 */
    private static String renderSymbol(ApiTypeRef ref, TypeAliasRegistry aliases, boolean input) {
        String fqn = fqnOfSymbol(ref.name());
        if (input && aliases != null) {
            if (!ref.arguments().isEmpty()) {
                String[] renderedArgs = ref.arguments().stream()
                        .map(a -> renderTypeRef(a, aliases, true))
                        .toArray(String[]::new);
                String alias = aliases.getCollectionAlias(fqn, renderedArgs);
                if (alias != null) return alias;
            } else if (aliases.hasAlias(fqn)) {
                return aliases.getAlias(fqn);
            }
        }
        StringBuilder sb = new StringBuilder("$").append(simpleSymbolName(ref.name()));
        if (!ref.arguments().isEmpty()) {
            sb.append('<');
            sb.append(ref.arguments().stream()
                    .map(a -> renderTypeRef(a, aliases, input))
                    .collect(Collectors.joining(", ")));
            sb.append('>');
        }
        return sb.toString();
    }

    /** symbol name 形如 {@code "java:java.util.List"} → 取 FQN 部分。 */
    private static String fqnOfSymbol(String symbolName) {
        int colon = symbolName.indexOf(':');
        return colon >= 0 ? symbolName.substring(colon + 1) : symbolName;
    }

    /**
     * 渲染 {@code docs} 列表为 JSDoc 块（行前用 {@code indent} 缩进）。单行 doc 折叠为一行形式的
     * 块注释，多行（含条目内嵌换行）逐行以星号前缀展开。docs 为空时不产出任何内容，
     * 因此 TypeReflector 的默认产出（docs 为空）渲染结果与旧实现逐字一致。
     */
    private static void appendDoc(StringBuilder sb, String indent, List<String> docs) {
        if (docs == null || docs.isEmpty()) return;
        boolean singleLine = docs.size() == 1 && !docs.get(0).contains("\n");
        if (singleLine) {
            sb.append(indent).append("/** ").append(docs.get(0)).append(" */\n");
            return;
        }
        sb.append(indent).append("/**\n");
        for (String doc : docs) {
            for (String line : doc.split("\n", -1)) {
                sb.append(indent).append(" * ").append(line).append("\n");
            }
        }
        sb.append(indent).append(" */\n");
    }

    private static String mapPrimT(String name) {
        return switch (name) {
            case "boolean" -> "boolean";
            case "int", "byte", "short", "long", "float", "double", "number" -> "number";
            case "char", "string" -> "string";
            case "void" -> "void";
            case "object" -> "object";
            default -> "any";
        };
    }

    /** symbol name 形如 {@code "java:java.util.List"} → 取最后一段点分作为简单名。 */
    private static String simpleSymbolName(String symbolName) {
        int colon = symbolName.indexOf(':');
        String qn = colon >= 0 ? symbolName.substring(colon + 1) : symbolName;
        int dot = qn.lastIndexOf('.');
        return dot >= 0 ? qn.substring(dot + 1) : qn;
    }

    /** 渲染用的类名：renameTo（modify_type 改名）优先，否则回退 sourceClass 原名。 */
    private static String effectiveClassName(TypeDecl d) {
        return d.effectiveTypeName() != null ? d.effectiveTypeName() : tsClassName(d.sourceClass);
    }

    private static String tsClassName(Class<?> cls) {
        if (cls == null) return "Unknown";
        if (cls.getEnclosingClass() != null && !cls.isAnonymousClass()) {
            return tsClassName(cls.getEnclosingClass()) + "$" + cls.getSimpleName();
        }
        return cls.getSimpleName();
    }
}
