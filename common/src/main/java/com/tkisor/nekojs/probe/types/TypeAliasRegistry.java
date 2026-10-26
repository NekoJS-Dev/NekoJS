package com.tkisor.nekojs.probe.types;

import com.tkisor.nekojs.core.reflect.FunctionalInterfaceResolver;
import com.tkisor.nekojs.probe.backend.typescript.IndexFileGenerator;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registers TypeScript input aliases for Java conversion boundaries.
 * Host containers retain their generic arguments; converted inputs use independently rendered element types.
 * Return values retain their host types rather than accepting input conversions.
 */
public final class TypeAliasRegistry {
    private final Map<String, String> classAliases = new LinkedHashMap<>();
    private final Map<String, Integer> adapterTypeParameters = new LinkedHashMap<>();
    private final Map<String, CollectionAlias> collectionAliases = new LinkedHashMap<>();
    private final Map<String, FunctionalInterfaceAlias> functionalAliases = new LinkedHashMap<>();
    private java.util.function.BiFunction<String, String, String> references = (fqn, name) -> name;
    private boolean projectScalarValues = true;
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

    /** Creates an independent rendering view; module-local names never mutate the shared alias registry. */
    public TypeAliasRegistry withReferences(java.util.function.BiFunction<String, String, String> references) {
        TypeAliasRegistry view = new TypeAliasRegistry();
        view.classAliases.clear();
        view.classAliases.putAll(classAliases);
        view.adapterTypeParameters.putAll(adapterTypeParameters);
        view.collectionAliases.clear();
        view.collectionAliases.putAll(collectionAliases);
        view.functionalAliases.putAll(functionalAliases);
        view.enumAliasCache.putAll(enumAliasCache);
        view.references = references;
        view.projectScalarValues = projectScalarValues;
        return view;
    }

    /** Keeps explicit host references free of scalar alternatives while retaining names and input aliases. */
    public TypeAliasRegistry withRawValueReferences() {
        TypeAliasRegistry view = withReferences(references);
        view.projectScalarValues = false;
        return view;
    }

    public boolean projectsScalarValues() {
        return projectScalarValues;
    }

    /** Resolves one exported symbol by its Java identity within the current module. */
    public String reference(String fqn, String exportedName) {
        return references.apply(fqn, exportedName);
    }

    private void registerDefaults() {
        // String → string（已在 TypeConverter 中处理，这里不重复）

        // 集合类型别名
        collectionAliases.put("java.util.List", hostCollection("$List", true));
        collectionAliases.put("java.util.Collection", hostCollection("$Collection", true));
        collectionAliases.put("java.util.Set", hostCollection("$Set", false));
        collectionAliases.put("java.lang.Iterable", hostCollection("$Iterable", true));
        collectionAliases.put("java.util.Iterator", new CollectionAlias("$Iterator", ""));

        // Host maps accept arbitrary keys; object inputs are limited to TypeScript property keys.
        collectionAliases.put("java.util.Map", new CollectionAlias("$Map", "") {
            @Override
            public String getInputType(String[] hostArgs, String[] inputArgs, String hostName) {
                if (hostArgs.length == 2 && inputArgs.length == 2) {
                    String host = hostName + "<" + hostArgs[0] + ", " + hostArgs[1] + ">";
                    // Keep the record outside the conditional: concrete-key narrowing can erase its index signature.
                    return "(" + host + " | (([" + inputArgs[0] + "] extends [PropertyKey] ? unknown : never)"
                            + " & Partial<Record<Extract<" + inputArgs[0] + ", PropertyKey>, " + inputArgs[1] + ">>))";
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
        classAliases.put("java.lang.Object", "unknown");
        // UUID, Path and File retain their host identity unless a real adapter supplies an input alias.
    }

    private static CollectionAlias hostCollection(String name, boolean acceptsArray) {
        return new CollectionAlias(name, "[]") {
            @Override
            public String getInputType(String[] hostArgs, String[] inputArgs, String hostName) {
                String host = hostName + "<" + (hostArgs.length == 0 ? "any" : hostArgs[0]) + ">";
                String input = inputArgs.length == 0 ? "any" : inputArgs[0];
                return acceptsArray ? "(" + host + " | (" + input + ")[])" : host;
            }
        };
    }

    /**
     * 清空全部注册并恢复默认别名表——供 backend 每次 probe 运行前调用，避免上一轮的适配器别名
     * （{@code registerClassAlias} 产物）泄漏到本轮：若某目标类本轮不再生成，其残留别名仍会放宽参数类型。
     */
    public void clear() {
        classAliases.clear();
        adapterTypeParameters.clear();
        collectionAliases.clear();
        functionalAliases.clear();
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
        String alias = explicit != null ? explicit : enumInputAlias(className);
        return alias == null ? null : reference(className, alias);
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
        return getCollectionAlias(rawFqn, tsArgs, tsArgs);
    }

    /** Host generic arguments retain identity while converted array/object inputs use their input types. */
    public String getCollectionAlias(String rawFqn, String[] hostArgs, String[] inputArgs) {
        CollectionAlias alias = collectionAliases.get(rawFqn);
        if (alias == null) return null;
        return alias.getInputType(hostArgs, inputArgs, reference(rawFqn, alias.baseType));
    }

    /**
     * 注册自定义类别名。
     */
    public void registerClassAlias(String className, String tsType) {
        classAliases.put(className, tsType);
        adapterTypeParameters.remove(className);
    }

    /** Registers an adapter's actual generic arity; ordinary aliases do not consume host arguments. */
    public void registerAdapterAlias(String className, String tsType, int parameters) {
        classAliases.put(className, tsType);
        adapterTypeParameters.put(className, parameters);
    }

    /** Returns an explicitly registered class alias without resolving the lazy enum aliases. */
    public String getRegisteredAlias(String className) {
        String alias = classAliases.get(className);
        return alias == null ? null : reference(className, alias);
    }

    /** Explicit host arguments retain their identity; an empty list selects the alias's raw Java defaults. */
    public String renderRegisteredAlias(String className, List<String> hostArguments) {
        String alias = getRegisteredAlias(className);
        if (alias == null || adapterTypeParameters.getOrDefault(className, 0) == 0 || hostArguments.isEmpty()) {
            return alias;
        }
        return alias + "<" + String.join(", ", hostArguments) + ">";
    }

    public FunctionalInterfaceAlias getFunctionalInterfaceAlias(String className) {
        return functionalAliases.get(className);
    }

    public void clearFunctionalInterfaceAliases() {
        functionalAliases.clear();
    }

    public void registerFunctionalInterfaceAlias(String className, FunctionalInterfaceAlias alias) {
        if (!classAliases.containsKey(className)) {
            functionalAliases.put(className, alias);
        }
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

        /** Retains custom conversion implementations while allowing host names to be scoped. */
        public String getInputType(String[] typeArgs, String hostName) {
            return getInputType(typeArgs);
        }

        /** Existing custom aliases keep their conversion implementation unless they opt into host arguments. */
        public String getInputType(String[] hostArgs, String[] inputArgs, String hostName) {
            return getInputType(inputArgs, hostName);
        }
    }

    /** A SAM signature expressed with the declaring interface's type variables. */
    public record FunctionalInterfaceAlias(String aliasName, Class<?> interfaceClass,
                                           FunctionalInterfaceResolver.Signature signature,
                                           java.util.Set<String> generatedTypes) {
        public FunctionalInterfaceAlias {
            generatedTypes = java.util.Set.copyOf(generatedTypes);
        }
    }
}
