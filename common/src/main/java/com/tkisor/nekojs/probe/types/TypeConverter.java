package com.tkisor.nekojs.probe.types;

import java.lang.reflect.*;
import java.util.*;

/**
 * Java 类型 → TypeScript 类型转换器。
 *
 * <p>将 Java Type（Class、ParameterizedType、GenericArrayType 等）转换为 TypeScript 类型字符串。
 * 支持基础类型、集合类型、泛型、数组等映射。
 */
public final class TypeConverter {
    private final TypeAliasRegistry aliases;
    private final Set<String> discoveredClasses = new LinkedHashSet<>();

    public TypeConverter(TypeAliasRegistry aliases) {
        this.aliases = aliases;
    }

    /**
     * 将 Java Type 转换为 TypeScript 类型字符串（输出模式，返回完整类型）。
     */
    public String toTypeScript(Type type) {
        return toTypeScript(type, false);
    }

    /**
     * 将 Java Type 转换为 TypeScript 类型字符串。
     *
     * @param type  Java 类型
     * @param input true 时使用输入别名（如 List_ = E[]），false 时返回完整类型
     */
    public String toTypeScript(Type type, boolean input) {
        if (type == null || type == void.class) return "void";

        // 基础类型
        String primitive = mapPrimitive(type);
        if (primitive != null) return primitive;

        // Class 类型
        if (type instanceof Class<?> cls) {
            return mapClass(cls, input);
        }

        // 参数化类型 Foo<A, B>
        if (type instanceof ParameterizedType pt) {
            return mapParameterized(pt, input);
        }

        // 泛型数组 T[]
        if (type instanceof GenericArrayType gat) {
            return toTypeScript(gat.getGenericComponentType(), input) + "[]";
        }

        // 类型变量 T
        if (type instanceof TypeVariable<?> tv) {
            return tv.getName();
        }

        // 通配符 ? extends Bound
        if (type instanceof WildcardType wt) {
            Type[] upper = wt.getUpperBounds();
            if (upper.length > 0 && upper[0] != Object.class) {
                return toTypeScript(upper[0], input);
            }
            return "any";
        }

        return "any";
    }

    /**
     * 获取所有发现的 Java 类全限定名。
     */
    public Set<String> getDiscoveredClasses() {
        return discoveredClasses;
    }

    private String mapPrimitive(Type type) {
        if (type == boolean.class) return "boolean";
        if (type == byte.class || type == short.class || type == int.class ||
            type == long.class || type == float.class || type == double.class) return "number";
        if (type == char.class) return "string";
        if (type == void.class) return "void";
        if (type == Void.class) return "void";
        return null;
    }

    private String mapClass(Class<?> cls, boolean input) {
        // 基础类型
        if (cls == String.class) return "string";
        if (cls == Boolean.class || cls == boolean.class) return "boolean";
        if (Number.class.isAssignableFrom(cls) || cls.isPrimitive()) {
            String p = mapPrimitive(cls);
            if (p != null) return p;
            return "number";
        }
        if (cls == Object.class) return "object";

        // void
        if (cls == void.class || cls == Void.class) return "void";

        // 数组
        if (cls.isArray()) {
            return toTypeScript(cls.getComponentType(), input) + "[]";
        }

        // 记录发现的类
        discoveredClasses.add(cls.getName());

        // 检查输入别名
        if (input && aliases.hasAlias(cls.getName())) {
            return aliases.getAlias(cls.getName());
        }

        // 生成 $ClassName（内部类使用 Parent$Child 格式）
        return "$" + getTsClassName(cls);
    }

    /**
     * 获取类的 TypeScript 标识符名（内部类使用 Parent$Child 格式）。
     */
    private static String getTsClassName(Class<?> cls) {
        if (cls.getEnclosingClass() != null && !cls.isAnonymousClass()) {
            return getTsClassName(cls.getEnclosingClass()) + "$" + cls.getSimpleName();
        }
        return cls.getSimpleName();
    }

    private String mapParameterized(ParameterizedType pt, boolean input) {
        Type rawType = pt.getRawType();
        if (!(rawType instanceof Class<?> rawClass)) {
            return "any";
        }

        Type[] typeArgs = pt.getActualTypeArguments();
        String[] tsArgs = new String[typeArgs.length];
        for (int i = 0; i < typeArgs.length; i++) {
            tsArgs[i] = toTypeScript(typeArgs[i], input);
        }
        String argsStr = String.join(", ", tsArgs);

        // 检查输入别名（集合类型）
        if (input) {
            String alias = aliases.getCollectionAlias(rawClass, argsStr);
            if (alias != null) return alias;
        }

        // 记录发现的类
        discoveredClasses.add(rawClass.getName());

        return "$" + getTsClassName(rawClass) + "<" + argsStr + ">";
    }

    /**
     * 清理生成过程中积累的缓存，释放内存。
     */
    public void clearCaches() {
        discoveredClasses.clear();
    }
}
