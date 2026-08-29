package com.tkisor.nekojs.probe.types;

import com.tkisor.nekojs.probe.backend.typescript.IndexFileGenerator;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 类型别名注册表：管理 Java 类型到 TypeScript 输入别名的映射。
 *
 * <p>当 Java 类型用作方法参数时，使用更宽松的 TypeScript 类型（如 List → E[]）。
 * 当用作返回值时，使用完整类型（如 $List）。
 */
public final class TypeAliasRegistry {
    private final Map<String, String> classAliases = new LinkedHashMap<>();
    private final Map<String, CollectionAlias> collectionAliases = new LinkedHashMap<>();
    /**
     * 枚举输入别名的惰性解析缓存：FQN → {@code $<SimpleName>_}；空串 = 已确认非枚举/不可加载。
     *
     * <p>枚举别名必须与适配器别名（{@code registerClassAlias}，在全部参数渲染前一次性注册）一样
     * 「先于参数渲染可用」——但共享 IR 是逐类并行预声明的，逐类注册会因任务调度顺序不同导致
     * 参数是否被放宽随运行抖动（违反产物确定性）。故这里在查询点（{@link TypeConverter} 的
     * input 查询）按需反射判定枚举并缓存，效果等价于启动时对每个枚举 FQN 调一次
     * {@code registerClassAlias(fqn, "$" + SimpleName + "_")}，且与处理顺序无关。
     */
    private final Map<String, String> enumAliasCache = new ConcurrentHashMap<>();

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
     * 清空全部注册并恢复默认别名表——供 backend 每次 probe 运行前调用，避免上一轮的适配器别名
     * （{@code registerClassAlias} 产物）泄漏到本轮：若某目标类本轮不再生成，其残留别名仍会放宽参数类型。
     */
    public void clear() {
        classAliases.clear();
        collectionAliases.clear();
        enumAliasCache.clear();
        registerDefaults();
    }

    /**
     * 检查是否有类级别的输入别名。
     *
     * <p>除显式注册（{@link #registerClassAlias}）外，枚举类型恒有输入别名
     * {@code $<SimpleName>_}（枚举 | 字符串字面量联合，声明由 IndexFileGenerator 就近发射）。
     */
    public boolean hasAlias(String className) {
        if (classAliases.containsKey(className)) return true;
        return enumInputAlias(className) != null;
    }

    /**
     * 获取类级别的输入别名。
     *
     * <p>显式注册的别名优先；枚举类型回退到 {@code $<SimpleName>_}（内部类为
     * {@code $Parent$Child_}，与各生成器的 TS 命名一致）。
     */
    public String getAlias(String className) {
        String explicit = classAliases.get(className);
        if (explicit != null) return explicit;
        return enumInputAlias(className);
    }

    /**
     * 惰性解析枚举的输入别名名：枚举 FQN → {@code $<TS名>_}，非枚举/不可加载 → null。
     * 结果缓存（含否定结果），跨线程安全。
     */
    private String enumInputAlias(String className) {
        if (className == null) return null;
        String cached = enumAliasCache.get(className);
        if (cached != null) return cached.isEmpty() ? null : cached;
        String alias = null;
        try {
            Class<?> cls = Class.forName(className, false, Thread.currentThread().getContextClassLoader());
            if (cls.isEnum()) {
                alias = "$" + tsClassName(cls) + "_";
            }
        } catch (ClassNotFoundException | LinkageError ignored) {
            // 不可加载（含 NoClassDefFoundError）：与显式别名缺失时的行为一致（不放宽，正常渲染 $Foo）
        }
        enumAliasCache.put(className, alias == null ? "" : alias);
        return alias;
    }

    /** 类的 TypeScript 标识符名（内部类用 Parent$Child 格式，与各生成器一致）。 */
    private static String tsClassName(Class<?> cls) {
        if (cls.getEnclosingClass() != null && !cls.isAnonymousClass()) {
            return tsClassName(cls.getEnclosingClass()) + "$" + cls.getSimpleName();
        }
        return cls.getSimpleName();
    }

    /**
     * 获取集合类型的输入别名。
     *
     * @param rawClass 参数化类型的 raw class
     * @param tsArgs   各类型实参**独立渲染后**的 TS 字符串（结构化数组，来自
     *                 {@code ParameterizedType.getActualTypeArguments()} 逐个转换）。
     *                 不能传入 join 后的整体字符串再按 ", " 拆分——实参自身可能含 ", "
     *                 （嵌套多实参泛型），拆分会截断嵌套类型。
     * @return 输入类型字符串，如果不是集合类型则返回 null
     */
    public String getCollectionAlias(Class<?> rawClass, String[] tsArgs) {
        return getCollectionAlias(rawClass.getName(), tsArgs);
    }

    /**
     * fqn 版集合输入别名查询（ref 渲染路径用——ref 只有符号 FQN，没有 Class 实例）。
     * 语义与 {@link #getCollectionAlias(Class, String[])} 完全一致。
     */
    public String getCollectionAlias(String rawFqn, String[] tsArgs) {
        CollectionAlias alias = collectionAliases.get(rawFqn);
        if (alias == null) return null;
        return alias.getInputType(tsArgs);
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
