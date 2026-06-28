package com.tkisor.nekojs.probe.types;

import java.util.*;

/**
 * 类型别名注册表：管理 Java 类型到 TypeScript 输入别名的映射。
 *
 * <p>当 Java 类型用作方法参数时，使用更宽松的 TypeScript 类型（如 List → E[]）。
 * 当用作返回值时，使用完整类型（如 $List）。
 */
public final class TypeAliasRegistry {
    private final Map<String, String> classAliases = new LinkedHashMap<>();
    private final Map<String, CollectionAlias> collectionAliases = new LinkedHashMap<>();

    public TypeAliasRegistry() {
        registerDefaults();
    }

    private void registerDefaults() {
        // String → string（已在 TypeConverter 中处理，这里不重复）

        // 集合类型别名
        collectionAliases.put("java.util.List", new CollectionAlias("$List", "[]"));
        collectionAliases.put("java.util.Collection", new CollectionAlias("$Collection", "[]"));
        collectionAliases.put("java.util.Set", new CollectionAlias("$Set", "[]"));
        collectionAliases.put("java.util.Iterable", new CollectionAlias("$Iterable", "[]"));
        collectionAliases.put("java.util.Iterator", new CollectionAlias("$Iterator", ""));

        // Map 特殊处理
        collectionAliases.put("java.util.Map", new CollectionAlias("$Map", "") {
            @Override
            public String getInputType(String[] typeArgs) {
                if (typeArgs.length == 2) {
                    return "{ [key: " + typeArgs[0] + "]: " + typeArgs[1] + " }";
                }
                return "{ [key: string]: any }";
            }
        });

        // 常用类型别名
        classAliases.put("java.lang.String", "string");
        classAliases.put("java.lang.Boolean", "boolean");
        classAliases.put("java.lang.Byte", "number");
        classAliases.put("java.lang.Short", "number");
        classAliases.put("java.lang.Integer", "number");
        classAliases.put("java.lang.Long", "number");
        classAliases.put("java.lang.Float", "number");
        classAliases.put("java.lang.Double", "number");
        classAliases.put("java.lang.Character", "string");
        classAliases.put("java.lang.Object", "object");
        classAliases.put("java.util.UUID", "string");
        classAliases.put("java.nio.file.Path", "string");
        classAliases.put("java.io.File", "string");
    }

    /**
     * 检查是否有类级别的输入别名。
     */
    public boolean hasAlias(String className) {
        return classAliases.containsKey(className);
    }

    /**
     * 获取类级别的输入别名。
     */
    public String getAlias(String className) {
        return classAliases.get(className);
    }

    /**
     * 获取集合类型的输入别名。
     *
     * @return 输入类型字符串，如果不是集合类型则返回 null
     */
    public String getCollectionAlias(Class<?> rawClass, String typeArgs) {
        CollectionAlias alias = collectionAliases.get(rawClass.getName());
        if (alias == null) return null;
        String[] args = typeArgs.isEmpty() ? new String[0] : typeArgs.split(", ");
        return alias.getInputType(args);
    }

    /**
     * 注册自定义类别名。
     */
    public void registerClassAlias(String className, String tsType) {
        classAliases.put(className, tsType);
    }

    /**
     * 注册自定义集合别名。
     */
    public void registerCollectionAlias(String className, String baseType, String suffix) {
        collectionAliases.put(className, new CollectionAlias(baseType, suffix));
    }

    public static class CollectionAlias {
        private final String baseType;
        private final String suffix;

        public CollectionAlias(String baseType, String suffix) {
            this.baseType = baseType;
            this.suffix = suffix;
        }

        public String getInputType(String[] typeArgs) {
            if (typeArgs.length == 0) return "any" + suffix;
            return typeArgs[0] + suffix;
        }
    }
}
