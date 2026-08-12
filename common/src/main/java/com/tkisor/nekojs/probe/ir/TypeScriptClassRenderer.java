package com.tkisor.nekojs.probe.ir;

import com.tkisor.nekojs.api.surface.ApiTypeRef;
import com.tkisor.nekojs.probe.types.TypeConverter;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * {@link TypeDecl} → TypeScript 类/接口/枚举声明块（不含 {@code declare module} 外壳）。
 *
 * <p>镜像旧 {@code ClassDeclGenerator} 的分段与格式，确保**未编辑** IR 的渲染与旧实现逐字一致：
 * <ul>
 *   <li>未编辑类型槽 → {@link TypeConverter#toTypeScript(java.lang.reflect.Type, boolean)} 渲染 sourceType</li>
 *   <li>modify_type 改写过的槽（{@link TypeSlot#overridden}）→ {@link #renderRef(ApiTypeRef)} 渲染 ref</li>
 * </ul>
 *
 * <p>getter 覆盖表（{@link #overrideGetter}）镜像 {@code ClassDeclGenerator.overrideGetter}：
 * {@code probe.modify_type}/{@code probe.assign_type} 把类标记 mutated 后改走本 renderer 重渲染，
 * 旧路径上注册的 getter 覆盖（如 RecipeEventJS.recipes → DocumentedRecipes）必须在此同样生效，
 * 否则覆盖会随重渲染丢失。
 */
public final class TypeScriptClassRenderer {
    private final TypeConverter typeConverter;

    // 覆盖 getter 的返回类型 (fqn -> getter 属性名 -> { returnType, importStatement })
    // 键是 getter 的**属性名**（如 "recipes"），与 ClassDeclGenerator 的 lookup 口径一致
    private final Map<String, Map<String, GetterOverride>> getterOverrides = new LinkedHashMap<>();

    public TypeScriptClassRenderer(TypeConverter typeConverter) {
        this.typeConverter = typeConverter;
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
            sb.append(" extends ").append(renderSlot(d.superType, false));
        }
        if (!d.interfaces.isEmpty()) {
            sb.append(" implements ")
              .append(d.interfaces.stream().map(s -> renderSlot(s, false)).collect(Collectors.joining(", ")));
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
            sb.append(" extends ")
              .append(d.interfaces.stream().map(s -> renderSlot(s, false)).collect(Collectors.joining(", ")));
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
            sb.append(p.name);
            if (p.varargs || p.optional) sb.append("?");
            sb.append(": ");
            sb.append(renderSlot(p.type, true));
            if (p.varargs) sb.append("[]");
        }
    }

    /** 渲染类型槽：overridden → ref；否则 → TypeConverter(sourceType)。 */
    private String renderSlot(TypeSlot slot, boolean input) {
        if (slot == null) return "any";
        if (slot.overridden || slot.sourceType == null) return renderTypeRef(slot.ref);
        return typeConverter.toTypeScript(slot.sourceType, input);
    }

    /** 简单 ApiTypeRef → TS（用于 modify_type 改写过的槽，及 probe.add_global 的全局声明类型）。 */
    public static String renderTypeRef(ApiTypeRef ref) {
        if (ref == null) return "any";
        return switch (ref.kind()) {
            case VOID -> "void";
            case PRIMITIVE -> mapPrimT(ref.name());
            case TYPE_VARIABLE -> ref.name();
            case ARRAY -> renderTypeRef(ref.arguments().get(0)) + "[]";
            case UNION -> ref.arguments().stream()
                    .map(TypeScriptClassRenderer::renderTypeRef)
                    .collect(Collectors.joining(" | "));
            case SYMBOL -> "$" + simpleSymbolName(ref.name());
            case CALLBACK -> "(...args: any[]) => any";
        };
    }

    private String renderRef(ApiTypeRef ref) {
        return renderTypeRef(ref);
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
