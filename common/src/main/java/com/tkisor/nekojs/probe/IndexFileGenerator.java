package com.tkisor.nekojs.probe;

import com.tkisor.nekojs.probe.types.TypeConverter;

import java.lang.reflect.*;
import java.util.*;

/**
 * index.d.ts 文件生成器：为每个包生成模块声明文件。
 *
 * <p>格式参考 ProbeJS：
 * <pre>
 * import { $ClassA } from "@package/other/package";
 * export * as subpackage from "@package/package/subpackage";
 *
 * declare module "@package/package" {
 *     export class $ClassA { ... }
 * }
 * </pre>
 */
public final class IndexFileGenerator {
    private final ClassDeclGenerator classDeclGenerator;
    private final TypeConverter typeConverter;

    // 性能缓存（线程安全，支持并行生成）
    private final java.util.concurrent.ConcurrentHashMap<String, Class<?>> classCache = new java.util.concurrent.ConcurrentHashMap<>();
    private final java.util.concurrent.ConcurrentHashMap<String, String> declCache = new java.util.concurrent.ConcurrentHashMap<>();
    private final java.util.concurrent.ConcurrentHashMap<String, Set<String>> importCache = new java.util.concurrent.ConcurrentHashMap<>();

    public IndexFileGenerator(ClassDeclGenerator classDeclGenerator, TypeConverter typeConverter) {
        this.classDeclGenerator = classDeclGenerator;
        this.typeConverter = typeConverter;
    }

    /**
     * 为一个包节点生成 index.d.ts 内容。
     */
    public String generate(String packageName, List<String> classNames, List<String> subpackages,
                           Set<String> allClasses) {
        StringBuilder sb = new StringBuilder();
        String modulePath = "@package/" + packageName.replace('.', '/');

        // 收集需要 import 的类（使用缓存）
        Set<String> importsNeeded = new LinkedHashSet<>();
        for (String simpleName : classNames) {
            String fullName = packageName + "." + simpleName;
            Set<String> cached = importCache.get(fullName);
            if (cached != null) {
                importsNeeded.addAll(cached);
            } else {
                Class<?> cls = findClass(fullName);
                if (cls != null) {
                    Set<String> classImports = new LinkedHashSet<>();
                    collectImports(cls, classImports, packageName);
                    importCache.put(fullName, classImports);
                    importsNeeded.addAll(classImports);
                }
            }
        }

        // 生成 import 语句（按包分组）
        if (!importsNeeded.isEmpty()) {
            Map<String, List<String>> importsByPackage = new TreeMap<>();
            for (String fqn : importsNeeded) {
                int dot = fqn.lastIndexOf('.');
                if (dot < 0) continue; // 默认包的类无法通过模块路径导入，跳过
                String pkg = fqn.substring(0, dot);
                String simple = fqn.substring(dot + 1);
                importsByPackage.computeIfAbsent(pkg, k -> new ArrayList<>()).add("$" + simple);
            }

            for (var entry : importsByPackage.entrySet()) {
                String importPath = "@package/" + entry.getKey().replace('.', '/');
                sb.append("import { ").append(String.join(", ", entry.getValue()));
                sb.append(" } from \"").append(importPath).append("\";\n");
            }
            sb.append("\n");
        }

        // 额外的 import 语句（来自 ClassDeclGenerator.overrideGetter）
        Set<String> extraImports = new LinkedHashSet<>();
        for (String simpleName : classNames) {
            String fullName = packageName + "." + simpleName;
            extraImports.addAll(classDeclGenerator.getExtraImports(fullName));
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
                } else {
                    Class<?> cls = findClass(fullName);
                    if (cls != null) {
                        String decl = classDeclGenerator.generate(cls);
                        declCache.put(fullName, decl);
                        sb.append(decl);
                        sb.append("\n");
                    }
                }
            }

            // 生成类型别名（$List_ = E[] 等）
            for (String simpleName : classNames) {
                String fullName = packageName + "." + simpleName;
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
            sb.append("export * as ").append(pkg).append(" from \"@package/").append(pkg).append("\";\n");
        }

        return sb.toString();
    }

    private Class<?> findClass(String fullName) {
        return classCache.computeIfAbsent(fullName, name -> {
            try {
                return Class.forName(name);
            } catch (ClassNotFoundException e) {
                return null;
            }
        });
    }

    private void collectImports(Class<?> cls, Set<String> imports, String currentPackage) {
        if (cls == null || cls.isPrimitive() || cls == Object.class) return;
        // 数组类：递归收集组件类型
        if (cls.isArray()) {
            collectImports(cls.getComponentType(), imports, currentPackage);
            return;
        }
        // 收集父类
        Class<?> superClass = cls.getSuperclass();
        if (superClass != null && superClass != Object.class && !inSamePackage(superClass, currentPackage)) {
            imports.add(superClass.getName());
        }

        // 收集接口
        for (Class<?> iface : cls.getInterfaces()) {
            if (!inSamePackage(iface, currentPackage)) {
                imports.add(iface.getName());
            }
        }

        // 收集字段类型
        for (Field field : cls.getDeclaredFields()) {
            if (Modifier.isPublic(field.getModifiers())) {
                collectTypeImports(field.getGenericType(), imports, currentPackage);
            }
        }

        // 收集方法参数和返回值类型
        for (Method method : cls.getDeclaredMethods()) {
            if (Modifier.isPublic(method.getModifiers())) {
                collectTypeImports(method.getGenericReturnType(), imports, currentPackage);
                for (Type paramType : method.getGenericParameterTypes()) {
                    collectTypeImports(paramType, imports, currentPackage);
                }
            }
        }
    }

    private void collectTypeImports(Type type, Set<String> imports, String currentPackage) {
        if (type instanceof Class<?> cls) {
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
            // ========== 非 adapter 的输入别名（结构体/函数式接口）==========

            // ========== Adapter 输入别名（NON_GENERIC 前缀表示固定类型）==========
            // Minecraft 核心类型
            case "net.minecraft.world.item.ItemStack" ->
                    "NON_GENERIC:$ItemStack | string";
            case "net.minecraft.world.item.Item" ->
                    "NON_GENERIC:$Item | string";
            case "net.minecraft.world.item.Items" ->
                    "NON_GENERIC:$Items";
            case "net.minecraft.world.item.crafting.Ingredient" ->
                    "NON_GENERIC:$Ingredient | string | string[]";
            case "net.minecraft.resources.ResourceLocation" ->
                    "NON_GENERIC:$ResourceLocation | string";
            case "net.minecraft.network.chat.Component" ->
                    "NON_GENERIC:$Component | string";
            case "net.minecraft.core.BlockPos" ->
                    "NON_GENERIC:$BlockPos | [number, number, number]";
            case "net.minecraft.world.level.block.state.BlockState" ->
                    "NON_GENERIC:$BlockState | string";
            case "net.minecraft.world.level.block.Block" ->
                    "NON_GENERIC:$Block | string";
            case "net.minecraft.world.entity.EntityType" ->
                    "NON_GENERIC:$EntityType | string";
            case "net.minecraft.core.registries.BuiltInRegistries" ->
                    "NON_GENERIC:$BuiltInRegistries";

            // NeoForge Fluid 类型
            case "net.neoforged.neoforge.fluids.FluidStack" ->
                    "NON_GENERIC:$FluidStack | string";
            case "net.neoforged.neoforge.fluids.crafting.FluidIngredient" ->
                    "NON_GENERIC:$FluidIngredient | string | string[]";
            case "net.neoforged.neoforge.fluids.crafting.SizedFluidIngredient" ->
                    "NON_GENERIC:$SizedFluidIngredient | { fluid?: string, amount?: number }";
            case "net.neoforged.neoforge.common.crafting.SizedIngredient" ->
                    "NON_GENERIC:$SizedIngredient | { ingredient?: any, count?: number }";
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

    /**
     * 预生成单个类的声明和 import 集合（线程安全）。
     * 用于 BFS 阶段，反射一次，结果缓存供后续 generate() 使用。
     */
    public void pregenerateClass(Class<?> cls) {
        String fullName = cls.getName();
        if (declCache.containsKey(fullName)) return;

        // 生成类声明
        String decl = classDeclGenerator.generate(cls);
        declCache.put(fullName, decl);

        // 收集 imports
        String packageName = cls.getPackage() != null ? cls.getPackage().getName() : "";
        Set<String> imports = new LinkedHashSet<>();
        collectImports(cls, imports, packageName);
        importCache.put(fullName, imports);

        // 缓存 Class 对象
        classCache.put(fullName, cls);
    }

    /**
     * 获取已缓存的类 import 集合（用于 BFS 依赖发现）。
     */
    public Set<String> getImportsForClass(String fullName) {
        return importCache.get(fullName);
    }

    /**
     * 清理生成过程中积累的缓存，释放内存。
     */
    public void clearCaches() {
        classCache.clear();
        declCache.clear();
        importCache.clear();
    }
}
