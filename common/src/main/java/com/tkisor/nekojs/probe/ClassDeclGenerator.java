package com.tkisor.nekojs.probe;

import com.tkisor.nekojs.probe.types.TypeConverter;

import java.lang.reflect.*;
import java.util.*;

/**
 * Java 类 → TypeScript 类声明生成器。
 *
 * <p>参考 ProbeJS 的 ClassDecl 输出格式。
 */
public final class ClassDeclGenerator {
    private final TypeConverter typeConverter;
    // 某些类的 getter 由其他生成器提供（如 recipe 的 recipes 属性），需要跳过
    private final Map<String, Set<String>> skipGetters = new HashMap<>();
    // 覆盖 getter 的返回类型 (className -> getterName -> { returnType, importStatement })
    private final Map<String, Map<String, GetterOverride>> getterOverrides = new HashMap<>();
    // 额外的 import 语句（按类名收集，由 IndexFileGenerator 在写文件时合并）
    private final Map<String, Set<String>> extraImports = new HashMap<>();

    public ClassDeclGenerator(TypeConverter typeConverter) {
        this.typeConverter = typeConverter;
    }

    public record GetterOverride(String returnType, String importStatement) {}

    /**
     * 注册需要跳过的 getter。例如 RecipeEventJS 的 recipes getter 由 RecipeEventDeclarationGenerator 提供。
     */
    public void skipGetter(Class<?> cls, String getterName) {
        skipGetters.computeIfAbsent(cls.getName(), k -> new HashSet<>()).add(getterName);
    }

    /**
     * 覆盖 getter 的返回类型，同时附带 import 语句。
     * 例如: overrideGetter(RecipeEventJS.class, "recipes", "DocumentedRecipes",
     *           "import { DocumentedRecipes } from \"@side-only/server/events/recipes\";")
     */
    public void overrideGetter(Class<?> cls, String getterName, String returnType, String importStatement) {
        getterOverrides.computeIfAbsent(cls.getName(), k -> new HashMap<>())
                .put(getterName, new GetterOverride(returnType, importStatement));
        if (importStatement != null && !importStatement.isEmpty()) {
            extraImports.computeIfAbsent(cls.getName(), k -> new LinkedHashSet<>()).add(importStatement);
        }
    }

    /**
     * 获取一个类的额外 import 语句（供 IndexFileGenerator 在写包级 index.d.ts 时合并）。
     */
    public Set<String> getExtraImports(String className) {
        Set<String> imports = extraImports.get(className);
        return imports == null ? Set.of() : imports;
    }

    /**
     * 生成类声明（不含 export/declare 包装）。
     */
    public String generate(Class<?> cls) {
        if (cls.isInterface()) {
            return generateInterface(cls);
        }
        if (cls.isEnum()) {
            return generateEnum(cls);
        }
        return generateClass(cls);
    }

    /**
     * 获取类的 TypeScript 标识符名。
     * 内部类使用 Parent$Child 格式（与 import 路径一致）。
     */
    private String getTsClassName(Class<?> cls) {
        if (cls.getEnclosingClass() != null && !cls.isAnonymousClass()) {
            return getTsClassName(cls.getEnclosingClass()) + "$" + cls.getSimpleName();
        }
        return cls.getSimpleName();
    }

    private String generateClass(Class<?> cls) {
        StringBuilder sb = new StringBuilder();

        sb.append("    export class $").append(getTsClassName(cls));
        appendTypeParameters(sb, cls);

        // extends
        Class<?> superClass = cls.getSuperclass();
        if (superClass != null && superClass != Object.class) {
            sb.append(" extends ").append(typeConverter.toTypeScript(superClass));
        }

        // implements
        Class<?>[] interfaces = cls.getInterfaces();
        if (interfaces.length > 0) {
            sb.append(" implements ");
            List<String> ifaces = new ArrayList<>();
            for (Class<?> iface : interfaces) {
                ifaces.add(typeConverter.toTypeScript(iface));
            }
            sb.append(String.join(", ", ifaces));
        }

        sb.append(" {\n");

        // 构造器
        for (Constructor<?> ctor : cls.getDeclaredConstructors()) {
            if (Modifier.isPublic(ctor.getModifiers())) {
                sb.append(formatConstructor(ctor));
            }
        }

        // 静态字段
        for (Field field : cls.getDeclaredFields()) {
            if (Modifier.isStatic(field.getModifiers()) && Modifier.isPublic(field.getModifiers())) {
                sb.append(formatField(field, true));
            }
        }

        // 实例字段
        for (Field field : cls.getDeclaredFields()) {
            if (!Modifier.isStatic(field.getModifiers()) && Modifier.isPublic(field.getModifiers())) {
                sb.append(formatField(field, false));
            }
        }

        // getter/setter（从 getter 方法推断）
        Set<String> processedProperties = new HashSet<>();
        for (Method method : cls.getDeclaredMethods()) {
            if (!Modifier.isPublic(method.getModifiers())) continue;
            if (Modifier.isStatic(method.getModifiers())) continue;

            String name = method.getName();
            if ((name.startsWith("get") || name.startsWith("is")) && method.getParameterCount() == 0) {
                String propName = getPropertyName(method);
                Set<String> skipped = skipGetters.get(cls.getName());
                if (propName == null || processedProperties.contains(propName)) continue;
                if (skipped != null && skipped.contains(propName)) {
                    processedProperties.add(propName);
                    continue;
                }
                Map<String, GetterOverride> overrides = getterOverrides.get(cls.getName());
                GetterOverride override = overrides == null ? null : overrides.get(propName);
                if (override != null) {
                    processedProperties.add(propName);
                    sb.append("        get ").append(propName).append("(): ").append(override.returnType()).append(";\n");
                    continue;
                }
                {
                    processedProperties.add(propName);
                    String type = typeConverter.toTypeScript(method.getGenericReturnType());
                    sb.append("        get ").append(propName).append("(): ").append(type).append(";\n");

                    // 查找对应的 setter
                    String setterName = "set" + propName.substring(0, 1).toUpperCase() + propName.substring(1);
                    for (Method setter : cls.getDeclaredMethods()) {
                        if (setter.getName().equals(setterName) && setter.getParameterCount() == 1
                                && Modifier.isPublic(setter.getModifiers())) {
                            String setterType = typeConverter.toTypeScript(setter.getGenericParameterTypes()[0], true);
                            sb.append("        set ").append(propName).append("(value: ").append(setterType).append(");\n");
                            break;
                        }
                    }
                }
            }
        }

        // 静态方法
        for (Method method : cls.getDeclaredMethods()) {
            if (Modifier.isStatic(method.getModifiers()) && Modifier.isPublic(method.getModifiers())) {
                sb.append(formatMethod(method, true));
            }
        }

        // 实例方法（排除 getter/setter）
        for (Method method : cls.getDeclaredMethods()) {
            if (!Modifier.isStatic(method.getModifiers()) && Modifier.isPublic(method.getModifiers())) {
                String name = method.getName();
                if (!isGetter(method) && !isSetter(method)) {
                    sb.append(formatMethod(method, false));
                }
            }
        }

        sb.append("    }\n");
        return sb.toString();
    }

    private String generateInterface(Class<?> cls) {
        StringBuilder sb = new StringBuilder();

        // 接口生成 interface 声明
        sb.append("    export interface $").append(getTsClassName(cls));
        appendTypeParameters(sb, cls);

        Class<?>[] interfaces = cls.getInterfaces();
        if (interfaces.length > 0) {
            sb.append(" extends ");
            List<String> ifaces = new ArrayList<>();
            for (Class<?> iface : interfaces) {
                ifaces.add(typeConverter.toTypeScript(iface));
            }
            sb.append(String.join(", ", ifaces));
        }

        sb.append(" {\n");

        // 方法
        for (Method method : cls.getDeclaredMethods()) {
            if (Modifier.isPublic(method.getModifiers())) {
                sb.append(formatMethod(method, false));
            }
        }

        // 常量
        for (Field field : cls.getDeclaredFields()) {
            if (Modifier.isStatic(field.getModifiers()) && Modifier.isPublic(field.getModifiers())
                    && Modifier.isFinal(field.getModifiers())) {
                sb.append(formatField(field, true));
            }
        }

        sb.append("    }\n");
        return sb.toString();
    }

    private String generateEnum(Class<?> cls) {
        StringBuilder sb = new StringBuilder();

        sb.append("    export class $").append(getTsClassName(cls)).append(" {\n");

        // 枚举常量作为静态字段
        for (Field field : cls.getDeclaredFields()) {
            if (field.isEnumConstant()) {
                sb.append("        static ").append(field.getName()).append(": $").append(getTsClassName(cls)).append(";\n");
            }
        }

        // 常用方法
        sb.append("        name(): string;\n");
        sb.append("        ordinal(): number;\n");
        sb.append("        toString(): string;\n");

        // 静态方法
        sb.append("        static values(): $").append(getTsClassName(cls)).append("[];\n");
        sb.append("        static valueOf(name: string): $").append(getTsClassName(cls)).append(";\n");

        sb.append("    }\n");
        return sb.toString();
    }

    private void appendTypeParameters(StringBuilder sb, Class<?> cls) {
        TypeVariable<?>[] typeParams = cls.getTypeParameters();
        if (typeParams.length > 0) {
            sb.append("<");
            List<String> params = new ArrayList<>();
            for (TypeVariable<?> tp : typeParams) {
                String param = tp.getName();
                Type[] bounds = tp.getBounds();
                if (bounds.length > 0 && bounds[0] != Object.class) {
                    param += " extends " + typeConverter.toTypeScript(bounds[0]);
                }
                params.add(param);
            }
            sb.append(String.join(", ", params));
            sb.append(">");
        }
    }

    private String formatConstructor(Constructor<?> ctor) {
        StringBuilder sb = new StringBuilder();
        sb.append("        constructor(");
        appendParameters(sb, ctor.getParameters(), ctor.isVarArgs());
        sb.append(");\n");
        return sb.toString();
    }

    private String formatField(Field field, boolean isStatic) {
        StringBuilder sb = new StringBuilder();
        sb.append("        ");
        if (isStatic) sb.append("static ");
        sb.append(field.getName()).append(": ");
        sb.append(typeConverter.toTypeScript(field.getGenericType()));
        sb.append(";\n");
        return sb.toString();
    }

    private String formatMethod(Method method, boolean isStatic) {
        StringBuilder sb = new StringBuilder();
        sb.append("        ");
        if (isStatic) sb.append("static ");
        sb.append(method.getName());

        // 类型参数
        TypeVariable<?>[] typeParams = method.getTypeParameters();
        if (typeParams.length > 0) {
            sb.append("<");
            List<String> params = new ArrayList<>();
            for (TypeVariable<?> tp : typeParams) {
                params.add(tp.getName());
            }
            sb.append(String.join(", ", params));
            sb.append(">");
        }

        sb.append("(");
        appendParameters(sb, method.getParameters(), method.isVarArgs());
        sb.append("): ");
        sb.append(typeConverter.toTypeScript(method.getGenericReturnType()));
        sb.append(";\n");
        return sb.toString();
    }

    private void appendParameters(StringBuilder sb, Parameter[] params, boolean isVarArgs) {
        List<String> paramStrs = new ArrayList<>();
        for (int i = 0; i < params.length; i++) {
            Parameter p = params[i];
            StringBuilder paramSb = new StringBuilder();

            String name = p.isNamePresent() ? p.getName() : "arg" + i;
            paramSb.append(name);

            if (isVarArgs && i == params.length - 1) {
                paramSb.append("?");
            }

            paramSb.append(": ");

            if (isVarArgs && i == params.length - 1 && p.getType().isArray()) {
                paramSb.append(typeConverter.toTypeScript(p.getType().getComponentType(), true)).append("[]");
            } else {
                paramSb.append(typeConverter.toTypeScript(p.getParameterizedType(), true));
            }

            paramStrs.add(paramSb.toString());
        }
        sb.append(String.join(", ", paramStrs));
    }

    private String getPropertyName(Method method) {
        String name = method.getName();
        if (name.startsWith("get") && name.length() > 3 && method.getParameterCount() == 0) {
            return Character.toLowerCase(name.charAt(3)) + name.substring(4);
        }
        if (name.startsWith("is") && name.length() > 2 && method.getParameterCount() == 0) {
            return Character.toLowerCase(name.charAt(2)) + name.substring(3);
        }
        return null;
    }

    private boolean isGetter(Method method) {
        String name = method.getName();
        return ((name.startsWith("get") && name.length() > 3) || (name.startsWith("is") && name.length() > 2))
                && method.getParameterCount() == 0;
    }

    private boolean isSetter(Method method) {
        String name = method.getName();
        return name.startsWith("set") && name.length() > 3 && method.getParameterCount() == 1;
    }
}
