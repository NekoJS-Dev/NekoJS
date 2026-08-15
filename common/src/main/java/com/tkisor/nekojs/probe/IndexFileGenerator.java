package com.tkisor.nekojs.probe;

import com.tkisor.nekojs.probe.ir.FieldDecl;
import com.tkisor.nekojs.probe.ir.MethodDecl;
import com.tkisor.nekojs.probe.ir.TypeDecl;
import com.tkisor.nekojs.probe.ir.TypeScriptClassRenderer;
import com.tkisor.nekojs.probe.ir.TypeSlot;
import com.tkisor.nekojs.probe.types.TypeConverter;

import java.lang.reflect.*;
import java.util.*;

/**
 * index.d.ts 文件生成器：为每个包生成模块声明文件。
 *
 * <p>格式参考 ProbeJS：
 * <pre>
 * import { $ClassA } from "java:other/package";
 * export * as subpackage from "java:package/subpackage";
 *
 * declare module "java:package" {
 *     export class $ClassA { ... }
 * }
 * </pre>
 */
public final class IndexFileGenerator {
    private final TypeScriptClassRenderer irRenderer;
    private final TypeConverter typeConverter;
    private final AdapterAliasGenerator adapterAliasGenerator;

    // 性能缓存（线程安全，支持并行生成）
    private final java.util.concurrent.ConcurrentHashMap<String, Class<?>> classCache = new java.util.concurrent.ConcurrentHashMap<>();
    private final java.util.concurrent.ConcurrentHashMap<String, String> declCache = new java.util.concurrent.ConcurrentHashMap<>();
    private final java.util.concurrent.ConcurrentHashMap<String, Set<String>> importCache = new java.util.concurrent.ConcurrentHashMap<>();
    /**
     * 枚举输入别名缓存（FQN → 别名名 + 就近发射的声明行）。predeclareClass 时从 {@link TypeDecl}
     * 计算，generate() 在枚举所在包模块内发射（{@code $Color_ = $Color | "RED" | ...}）。
     * 参数放宽由 {@link com.tkisor.nekojs.probe.types.TypeAliasRegistry} 对枚举 FQN 的
     * 惰性别名解析完成（与适配器别名经 registerClassAlias 放宽参数的机制同一出口）。
     */
    private final java.util.concurrent.ConcurrentHashMap<String, EnumAlias> enumAliasCache = new java.util.concurrent.ConcurrentHashMap<>();

    /** 枚举输入别名：别名名（如 {@code $DyeColor_}）+ 完整声明行（含换行）。 */
    private record EnumAlias(String aliasName, String declaration) {}

    /**
     * 被 {@code probe.modify_type} 隐藏（hide）的类 FQN 集合：generate 时过滤这些类的 import 与
     * 类型别名，防止其他类（反射 import 收集）悬空引用已隐藏类。由 backend 在每次 generate 前
     * 经 {@link #setHiddenClasses(Set)} 设置；{@link #clearCaches()} 不清除本集合（backend 重新设置）。
     */
    private volatile Set<String> hiddenClasses = Set.of();

    public IndexFileGenerator(TypeScriptClassRenderer irRenderer,
                              TypeConverter typeConverter, AdapterAliasGenerator adapterAliasGenerator) {
        this.irRenderer = irRenderer;
        this.typeConverter = typeConverter;
        this.adapterAliasGenerator = adapterAliasGenerator;
    }

    /**
     * 为一个包节点生成 index.d.ts 内容。
     */
    public String generate(String packageName, List<String> classNames, List<String> subpackages,
                           Set<String> allClasses) {
        StringBuilder sb = new StringBuilder();
        String modulePath = "java:" + packageName.replace('.', '/');

        // 收集需要 import 的类（predeclareClass 已按 IR 计算并缓存；缓存缺失表示该类反射失败，跳过）
        Set<String> importsNeeded = new LinkedHashSet<>();
        for (String simpleName : classNames) {
            String fullName = packageName + "." + simpleName;
            Set<String> cached = importCache.get(fullName);
            if (cached != null) {
                importsNeeded.addAll(cached);
            }
        }

        // 合并适配器输入别名引用的跨包类型（如 $Item、$NekoId），并探测是否引用了 @special 注册表字面量
        boolean moduleUsesRegistry = false;
        for (String simpleName : classNames) {
            AdapterAliasGenerator.AdapterAlias alias = adapterAliasGenerator.getAlias(packageName + "." + simpleName);
            if (alias == null) continue;
            importsNeeded.addAll(alias.importFqns());
            if (alias.usesRegistry()) moduleUsesRegistry = true;
        }

        // 引用 @special 注册表字面量时，需要导入 RegistryTypes 命名空间
        if (moduleUsesRegistry) {
            sb.append("import type { RegistryTypes } from \"@special/types\";\n");
        }

        // C5a：过滤被 hide 的类——其他类的反射 import 收集仍会引用它们（悬空），统一在此剔除
        if (!hiddenClasses.isEmpty()) {
            importsNeeded.removeIf(hiddenClasses::contains);
        }

        // 生成 import 语句（按包分组；包内符号名按字典序排序，保证跨 JVM 运行输出确定——
        // 反射的 getInterfaces/getDeclaredMethods 顺序无规范保证，直接沿用首插序会导致产物抖动）
        if (!importsNeeded.isEmpty()) {
            Map<String, List<String>> importsByPackage = new TreeMap<>();
            for (String fqn : importsNeeded) {
                int dot = fqn.lastIndexOf('.');
                if (dot < 0) continue; // 默认包的类无法通过模块路径导入，跳过
                String pkg = fqn.substring(0, dot);
                String simple = fqn.substring(dot + 1);
                List<String> names = importsByPackage.computeIfAbsent(pkg, k -> new ArrayList<>());
                names.add("$" + simple);
                // 若该类有适配器输入别名，方法参数放宽后会引用 $Foo_，需一并导入
                AdapterAliasGenerator.AdapterAlias alias = adapterAliasGenerator.getAlias(fqn);
                if (alias != null) {
                    names.add(alias.aliasName());
                }
                // 枚举同理：参数放宽为 $Enum_ 后需导入枚举所在模块的别名声明
                EnumAlias enumAlias = enumAliasCache.get(fqn);
                if (enumAlias != null) {
                    names.add(enumAlias.aliasName());
                }
            }

            for (var entry : importsByPackage.entrySet()) {
                String importPath = "java:" + entry.getKey().replace('.', '/');
                List<String> names = new ArrayList<>(new LinkedHashSet<>(entry.getValue()));
                Collections.sort(names);
                sb.append("import { ").append(String.join(", ", names));
                sb.append(" } from \"").append(importPath).append("\";\n");
            }
            sb.append("\n");
        }

        // 额外的 import 语句（来自 TypeScriptClassRenderer.overrideGetter）
        Set<String> extraImports = new LinkedHashSet<>();
        for (String simpleName : classNames) {
            String fullName = packageName + "." + simpleName;
            extraImports.addAll(irRenderer.getExtraImports(fullName));
        }
        for (String stmt : extraImports) {
            sb.append(stmt).append("\n");
        }
        if (!extraImports.isEmpty()) {
            sb.append("\n");
        }

        // 生成子包 re-export
        for (String sub : subpackages) {
            sb.append("export * as ").append(sub).append(" from \"")
              .append(modulePath).append("/").append(sub).append("\";\n");
        }
        if (!subpackages.isEmpty()) {
            sb.append("\n");
        }

        // 生成模块声明（仅当包内有类时）
        if (!classNames.isEmpty()) {
            sb.append("declare module \"").append(modulePath).append("\" {\n");

            for (String simpleName : classNames) {
                String fullName = packageName + "." + simpleName;
                String cached = declCache.get(fullName);
                if (cached != null) {
                    sb.append(cached);
                    sb.append("\n");
                }
            }

            // 生成类型别名：优先适配器驱动的输入别名（$ItemStack_ 等），其次枚举字面量别名
            // （$DyeColor_ = $DyeColor | "RED" | ...），否则回退到集合/残留别名
            for (String simpleName : classNames) {
                String fullName = packageName + "." + simpleName;
                AdapterAliasGenerator.AdapterAlias adapterAlias = adapterAliasGenerator.getAlias(fullName);
                if (adapterAlias != null) {
                    adapterAlias.doc().ifPresent(doc ->
                        sb.append("    /** ").append(doc.replace("\n", "\n     * ")).append(" */\n"));
                    sb.append("    export type ").append(adapterAlias.aliasName())
                      .append(" = ").append(adapterAlias.union()).append(";\n");
                    continue;
                }
                EnumAlias enumAlias = enumAliasCache.get(fullName);
                if (enumAlias != null) {
                    sb.append(enumAlias.declaration());
                    continue;
                }
                String alias = generateTypeAlias(fullName, simpleName);
                if (alias != null) {
                    sb.append(alias);
                }
            }

            sb.append("}\n");
        }
        return sb.toString();
    }

    /**
     * 生成根 index.d.ts（re-export 所有一级包）。
     */
    public String generateRoot(List<String> topPackages) {
        StringBuilder sb = new StringBuilder();
        sb.append("// NekoJS Probe Type Declarations\n\n");

        for (String pkg : topPackages) {
            sb.append("export * as ").append(pkg).append(" from \"java:").append(pkg).append("\";\n");
        }

        return sb.toString();
    }

    private Class<?> findClass(String fullName) {
        return classCache.computeIfAbsent(fullName, name -> {
            try {
                return Class.forName(name, false, Thread.currentThread().getContextClassLoader());
            } catch (ClassNotFoundException | NoClassDefFoundError e) {
                return null;
            }
        });
    }

    private void collectTypeImports(Type type, Set<String> imports, String currentPackage) {
        if (type instanceof Class<?> cls) {
            // 数组类：递归收集组件类型，避免 "[Lnet/.../Foo;" 描述符泄漏到 import
            if (cls.isArray()) {
                collectTypeImports(cls.getComponentType(), imports, currentPackage);
                return;
            }
            if (!cls.isPrimitive() && !inSamePackage(cls, currentPackage) && cls != Object.class) {
                imports.add(cls.getName());
            }
        } else if (type instanceof ParameterizedType pt) {
            if (pt.getRawType() instanceof Class<?> rawCls) {
                if (!inSamePackage(rawCls, currentPackage)) {
                    imports.add(rawCls.getName());
                }
            }
            for (Type arg : pt.getActualTypeArguments()) {
                collectTypeImports(arg, imports, currentPackage);
            }
        } else if (type instanceof GenericArrayType gat) {
            collectTypeImports(gat.getGenericComponentType(), imports, currentPackage);
        }
    }

    private boolean inSamePackage(Class<?> cls, String packageName) {
        if (cls.getPackage() == null) return packageName.isEmpty();
        return cls.getPackage().getName().equals(packageName);
    }

    /**
     * 为已知集合类型生成输入别名。
     * 例如 List&lt;E&gt; → $List_&lt;E&gt; = E[]
     */
    private String generateTypeAlias(String fullName, String simpleName) {
        // C5a：隐藏类的别名不生成（其声明已为空，别名会悬空引用 $Hidden）
        if (hiddenClasses.contains(fullName)) return null;

        // 需要别名的类型：(完整名, 类型参数数量, 别名模板)
        // 别名模板中 {0} = 第一个类型参数, {1} = 第二个类型参数
        String alias = switch (fullName) {
            case "java.util.List", "java.util.ArrayList", "java.util.LinkedList",
                 "java.util.Collection", "java.util.SequencedCollection" ->
                    "{0}[]";
            case "java.util.Set", "java.util.HashSet", "java.util.TreeSet",
                 "java.util.LinkedHashSet", "java.util.SequencedSet" ->
                    "{0}[]";
            case "java.util.Map", "java.util.HashMap", "java.util.TreeMap",
                 "java.util.LinkedHashMap", "java.util.SortedMap", "java.util.SequencedMap" ->
                    "{ [key: string]: {1} }";
            case "java.util.Optional" ->
                    "{0} | null";
            case "java.lang.Iterable", "java.util.Iterator", "java.util.Spliterator",
                 "java.util.stream.Stream", "java.util.stream.IntStream",
                 "java.util.stream.LongStream", "java.util.stream.DoubleStream" ->
                    "{0}[]";
            case "java.util.function.Consumer", "java.util.function.IntConsumer",
                 "java.util.function.LongConsumer", "java.util.function.DoubleConsumer" ->
                    "({0}) => void";
            case "java.util.function.Function", "java.util.function.UnaryOperator" ->
                    "({0}) => {1}";
            case "java.util.function.BiFunction" ->
                    "({0}, {1}) => any";
            case "java.util.function.Supplier", "java.util.function.IntSupplier",
                 "java.util.function.LongSupplier", "java.util.function.DoubleSupplier",
                 "java.util.function.BooleanSupplier" ->
                    "() => {0}";
            case "java.util.function.Predicate", "java.util.function.IntPredicate",
                 "java.util.function.LongPredicate", "java.util.function.DoublePredicate" ->
                    "({0}) => boolean";
            case "java.util.function.BiConsumer" ->
                    "({0}, {1}) => void";
            case "java.util.function.BiPredicate" ->
                    "({0}, {1}) => boolean";
            case "java.util.function.BinaryOperator" ->
                    "({0}, {0}) => {0}";
            // ========== 非 adapter 的残留输入别名（无对应适配器，固定类型）==========
            case "net.minecraft.world.item.Items" ->
                    "NON_GENERIC:$Items";
            case "net.minecraft.core.BlockPos" ->
                    "NON_GENERIC:$BlockPos | [number, number, number]";
            case "net.minecraft.world.level.block.state.BlockState" ->
                    "NON_GENERIC:$BlockState | string";
            case "net.minecraft.core.registries.BuiltInRegistries" ->
                    "NON_GENERIC:$BuiltInRegistries";
            default -> null;
        };

        if (alias == null) return null;

        Class<?> cls = findClass(fullName);
        if (cls == null) return null;

        // 非泛型的 adapter 输入别名（如 $ItemStack_ = $ItemStack | string）
        // 这些没有类型参数，直接生成固定类型
        if (alias.startsWith("NON_GENERIC:")) {
            return "    export type $" + simpleName + "_ = " + alias.substring("NON_GENERIC:".length()) + ";\n";
        }

        TypeVariable<?>[] typeParams = cls.getTypeParameters();
        if (typeParams.length == 0) return null;

        // 构建类型参数列表
        StringJoiner paramJoiner = new StringJoiner(", ");
        for (TypeVariable<?> tp : typeParams) {
            paramJoiner.add(tp.getName());
        }

        // 替换模板中的占位符
        String result = alias;
        for (int i = 0; i < typeParams.length; i++) {
            result = result.replace("{" + i + "}", typeParams[i].getName());
        }

        return "    export type $" + simpleName + "_<" + paramJoiner + "> = " + result + ";\n";
    }

    // ------------------------------------------------------------------
    //  IR 唯一路径（Phase 2.7）：单次反射 → TypeDecl → 声明 + import 集合
    // ------------------------------------------------------------------

    /**
     * 从已反射的 {@link TypeDecl} 计算 import 集合，镜像旧基于 {@code Class<?>} 直接反射的
     * 逐项语义（父类/接口/公开字段/公开方法的参数与返回类型——TypeReflector 的 IR 枚举
     * 与旧实现逐项对齐），保证切换 IR 唯一路径后包级 import 块字节不变。
     */
    public Set<String> collectImportsFromIr(TypeDecl decl, String currentPackage) {
        Set<String> imports = new LinkedHashSet<>();
        Class<?> source = decl.sourceClass;

        // 父类：镜像旧实现（class 取 getSuperclass；interface 为 null；enum 为 java.lang.Enum）
        if (source != null) {
            Class<?> superClass = source.getSuperclass();
            if (superClass != null && superClass != Object.class && !inSamePackage(superClass, currentPackage)) {
                imports.add(superClass.getName());
            }
        }

        // 接口
        for (TypeSlot iface : decl.interfaces) {
            if (iface.sourceType instanceof Class<?> cls && !inSamePackage(cls, currentPackage)) {
                imports.add(cls.getName());
            }
        }

        // 公开字段（IR 字段集 = 旧实现的公开字段集，含枚举常量）
        for (FieldDecl field : decl.fields) {
            collectTypeImports(field.type.sourceType, imports, currentPackage);
        }

        // 公开方法（IR 方法集 = 旧实现的公开方法集；构造器与旧实现一致不收集）
        for (MethodDecl method : decl.methods) {
            collectTypeImports(method.returnType != null ? method.returnType.sourceType : null, imports, currentPackage);
            for (MethodDecl.MethodParam p : method.params) {
                collectTypeImports(p.type.sourceType, imports, currentPackage);
            }
        }
        return imports;
    }

    /**
     * IR 唯一路径的类预声明：用 {@link TypeScriptClassRenderer} 渲染 {@link TypeDecl} 并缓存
     * 声明与 import 集合（单次反射的多产物消费）。{@code extraImportFqns} 为 {@code modify_type}
     * 编辑引入的额外 SYMBOL 全限定名（可为空）。
     */
    public void predeclareClass(String fqn, TypeDecl decl, Set<String> extraImportFqns) {
        registerEnumAlias(fqn, decl);
        declCache.put(fqn, irRenderer.render(decl));
        String packageName = packageOf(fqn);
        Set<String> imports = new LinkedHashSet<>(collectImportsFromIr(decl, packageName));
        if (extraImportFqns != null) {
            imports.addAll(extraImportFqns);
        }
        importCache.put(fqn, imports);
        if (decl.sourceClass != null) {
            classCache.put(fqn, decl.sourceClass);
        }
    }

    /**
     * 为枚举 {@link TypeDecl} 计算输入别名声明（{@code $Color_ = $Color | "RED" | ...}）并缓存，
     * 供 {@link #generate} 在枚举所在包模块内就近发射。常量顺序与 {@code TypeScriptClassRenderer.renderEnum}
     * 的静态常量发射完全一致（同一 {@code decl.fields} 序——TypeReflector 已按名字稳定排序），
     * 不截断（对齐 ProbeJS：长枚举也发全量字面量联合）。
     *
     * <p>跳过：非枚举、被 hide 的枚举（声明为空，别名会悬空）、无 sourceClass 且未改名的
     * 合成枚举（无法得出与声明一致的稳定命名）。适配器目标为枚举时适配器别名优先（generate 处判定）。
     */
    private void registerEnumAlias(String fqn, TypeDecl decl) {
        if (decl.kind != TypeDecl.Kind.ENUM || decl.hidden) return;
        String name = decl.effectiveTypeName() != null ? decl.effectiveTypeName() : tsEnumName(decl.sourceClass);
        if (name == null) return;
        String aliasName = "$" + name + "_";
        StringBuilder union = new StringBuilder(aliasName).append(" = $").append(name);
        for (FieldDecl f : decl.fields) {
            if (!f.hidden && f.isEnumConstant) {
                // 枚举常量名是合法 Java 标识符，直接双引号包裹（与 renderEnum 的静态常量同名）
                union.append(" | \"").append(f.effectiveName()).append("\"");
            }
        }
        enumAliasCache.put(fqn, new EnumAlias(aliasName,
                "    export type " + union + ";\n"));
    }

    /** 渲染用的枚举名：镜像 TypeScriptClassRenderer.tsClassName（内部类 Parent$Child）；无源类 → null。 */
    private static String tsEnumName(Class<?> cls) {
        if (cls == null) return null;
        if (cls.getEnclosingClass() != null && !cls.isAnonymousClass()) {
            return tsEnumName(cls.getEnclosingClass()) + "$" + cls.getSimpleName();
        }
        return cls.getSimpleName();
    }

    private static String packageOf(String fqn) {
        int dot = fqn.lastIndexOf('.');
        return dot >= 0 ? fqn.substring(0, dot) : "";
    }

    /**
     * 设置本次生成需过滤的隐藏类 FQN 集合（空集 = 清除上一轮的隐藏状态）。
     * 由 {@code TypeScriptProbeBackend} 在每次 generate 前调用。
     */
    public void setHiddenClasses(Set<String> hidden) {
        this.hiddenClasses = hidden == null || hidden.isEmpty() ? Set.of() : Set.copyOf(hidden);
    }

    /**
     * 清理生成过程中积累的缓存，释放内存。
     */
    public void clearCaches() {
        classCache.clear();
        declCache.clear();
        importCache.clear();
        enumAliasCache.clear();
    }
}
