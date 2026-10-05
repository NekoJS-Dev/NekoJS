package com.tkisor.nekojs.probe.backend.typescript;

import com.tkisor.nekojs.api.surface.ApiTypeRef;
import com.tkisor.nekojs.probe.ir.TypeScriptClassRenderer;
import com.tkisor.nekojs.probe.types.TypeAliasRegistry;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 函数式接口的 lambda 输入别名生成器。
 *
 * <p>Java 侧收 {@code Consumer<ItemStack>} 参数时，脚本传的是<b>箭头函数</b>——而箭头函数
 * 没有 {@code accept}/{@code andThen} 这些接口成员，直接赋给 {@code $Consumer<T>} 永远不成立。
 * 这里为每个函数式接口生成一个输入别名，把「可接受的 lambda 形状」显式写出来：
 *
 * <pre>
 *   export type $Consumer_&lt;T&gt; = (arg0: T) =&gt; void;
 * </pre>
 *
 * <p>别名注册进 {@link TypeAliasRegistry}（泛型类别别名）后，参数位置的
 * {@code Consumer<ItemStack>} 会自动渲染成 {@code $Consumer_&lt;$ItemStack&gt;}，
 * 脚本因此能直接写 {@code list.forEach(x => ...)} 且 {@code x} 有类型。
 *
 * <h2>为什么别名只含箭头函数，不含 {@code | $Consumer<T>}</h2>
 *
 * 与 ProbeJS 一致（见其 {@code InterfaceTypes}）：带上原始类会让它的成员出现在
 * IDE 的补全建议里——用户敲 {@code {} 时看到的是一堆 {@code accept}/{@code andThen}，
 * 而不是"这里该写个函数"。代价是传不了真实例，但那不是脚本场景。
 *
 * <h2>方法级类型变量 → any</h2>
 *
 * 形如 {@code <V> Function<V, R> compose(...)} 的 SAM 里，{@code V} 由<b>调用点</b>推断，
 * 别名声明处无从表达，故降级为 {@code any}；**类级形参保留**（那才是别名形参要承接的东西）。
 */
public final class FunctionalInterfaceAliasGenerator {

    private final TypeAliasRegistry aliasRegistry;
    private final Map<String, FunctionalAlias> aliases = new LinkedHashMap<>();

    public FunctionalInterfaceAliasGenerator(TypeAliasRegistry aliasRegistry) {
        this.aliasRegistry = aliasRegistry;
    }

    /** 函数式接口的别名：别名名（{@code $Consumer_}）+ 形参名 + 完整声明文本 + 引用的跨包类型。 */
    public record FunctionalAlias(
            String aliasName,
            List<String> typeParams,
            String declaration,
            Set<String> imports
    ) {
    }

    /**
     * 为给定的类集合生成 lambda 别名。
     *
     * <p>必须在参数渲染之前调用（参数位置的别名查询依赖此处的注册）。
     *
     * @param classesToGenerate 本次会被实际生成声明的全限定名集合。只处理其中的类——
     *                          为不生成的接口注册别名会让参数引用悬空类型。
     */
    public void prepare(Set<String> classesToGenerate) {
        aliases.clear();

        // 第一遍：只登记，「别名名 + 形参名」先可用，避免 SAM 签名里互相引用时受处理顺序影响
        Map<String, Class<?>> eligible = new LinkedHashMap<>();
        for (String fqn : classesToGenerate) {
            Class<?> cls = loadIfPossible(fqn);
            if (cls == null) continue;
            Method sam = singleAbstractMethod(cls);
            if (sam == null) continue;
            eligible.put(fqn, cls);
            aliasRegistry.registerGenericClassAlias(fqn, "$" + tsClassName(cls) + "_",
                    typeParamNames(cls));
        }

        // 第二遍：渲染签名（此时别名表已完整，签名里引用别的函数式接口也能命中）
        for (var entry : eligible.entrySet()) {
            FunctionalAlias alias = render(entry.getKey(), entry.getValue());
            if (alias != null) {
                aliases.put(entry.getKey(), alias);
            }
        }
    }

    /** 别名声明的完整行（含缩进），供 {@link IndexFileGenerator} 就近发射；未生成别名返回 null。 */
    public FunctionalAlias getAlias(String fqn) {
        return aliases.get(fqn);
    }

    public boolean hasAlias(String fqn) {
        return aliases.containsKey(fqn);
    }

    /**
     * 所有别名签名里引用的跨包类型全限定名，供 orchestrator 纳入生成集合，避免悬空 import。
     *
     * <p>返回按收集序的不可变快照（{@code Set.copyOf} 的迭代序无规范保证，会破坏产物确定性）。
     */
    public Set<String> hostImports() {
        Set<String> out = new LinkedHashSet<>();
        for (FunctionalAlias alias : aliases.values()) {
            out.addAll(alias.imports());
        }
        return java.util.Collections.unmodifiableSet(out);
    }

    // ------------------------------------------------------------------
    // 内部
    // ------------------------------------------------------------------

    private FunctionalAlias render(String fqn, Class<?> cls) {
        Method sam = singleAbstractMethod(cls);
        if (sam == null) return null;

        List<String> typeParams = typeParamNames(cls);
        // 方法级类型变量：别名声明处无从表达，降级 any
        Set<String> methodVars = new LinkedHashSet<>();
        for (TypeVariable<?> tv : sam.getTypeParameters()) {
            methodVars.add(tv.getName());
        }

        Set<String> imports = new LinkedHashSet<>();
        List<String> params = new ArrayList<>();
        Type[] genericParams = sam.getGenericParameterTypes();
        boolean varArgs = sam.isVarArgs();
        for (int i = 0; i < genericParams.length; i++) {
            boolean isVarargs = varArgs && i == genericParams.length - 1;
            params.add("arg" + i + ": " + renderParamType(genericParams[i], isVarargs, methodVars, imports));
        }
        String ret = sam.getReturnType() == void.class
                ? "void"
                : renderType(sam.getGenericReturnType(), methodVars, imports);

        String typeParamsDecl = typeParams.isEmpty() ? "" : "<" + String.join(", ", typeParams) + ">";
        String decl = aliasName(cls) + typeParamsDecl + " = ("
                + String.join(", ", params) + ") => " + ret + ";";
        return new FunctionalAlias(aliasName(cls), typeParams, decl, imports);
    }

    /** 变参按 IR 约定取组件类型再补 {@code []}（与 reflectParamsInto 一致）。 */
    private String renderParamType(Type type, boolean isVarargs, Set<String> methodVars, Set<String> imports) {
        Type effective = type;
        if (isVarargs) {
            if (type instanceof java.lang.reflect.GenericArrayType gat) {
                effective = gat.getGenericComponentType();
            } else if (type instanceof Class<?> c && c.isArray()) {
                effective = c.getComponentType();
            }
        }
        String rendered = renderType(effective, methodVars, imports);
        return isVarargs ? (rendered.endsWith("[]") ? rendered : rendered + "[]") : rendered;
    }

    /**
     * Java 反射类型 → TS 文本；方法级类型变量替换为 {@code any}。
     *
     * <p>渲染按 {@code input=true}（走别名放宽）：lambda 的形参与返回值同样是"脚本提供值"的位置，
     * 里面的 {@code ItemStack} 应放宽成 {@code $ItemStack_}，与 ProbeJS 的行为一致
     * （其 {@code InterfaceTypes} 对形参和返回都调 {@code markAsInput}）。
     */
    private String renderType(Type type, Set<String> methodVars, Set<String> imports) {
        if (type instanceof TypeVariable<?> tv) {
            return methodVars.contains(tv.getName()) ? "any" : tv.getName();
        }
        ApiTypeRef ref = com.tkisor.nekojs.probe.ir.TypeReflector.toRef(type);
        collectImports(ref, imports);
        return TypeScriptClassRenderer.renderTypeRef(ref, aliasRegistry, true);
    }

    /** 收集引用里的跨包类型 FQN（与适配器别名同口径，供 orchestrator 纳入生成集合）。 */
    private static void collectImports(ApiTypeRef ref, Set<String> out) {
        if (ref == null) return;
        if (ref.kind() == ApiTypeRef.Kind.SYMBOL && ref.name() != null) {
            int colon = ref.name().indexOf(':');
            out.add(colon >= 0 ? ref.name().substring(colon + 1) : ref.name());
        }
        for (ApiTypeRef arg : ref.arguments()) {
            collectImports(arg, out);
        }
    }

    /**
     * 函数式接口判定：接口 + 恰一个抽象方法。
     *
     * <p>{@code @FunctionalInterface} 注解不是必须的（它只在编译期做校验），故按 JDK 的 SAM 规则判定。
     */
    private static Method singleAbstractMethod(Class<?> cls) {
        if (!cls.isInterface()) return null;
        Method found = null;
        for (Method m : cls.getMethods()) {
            if (m.isDefault() || Modifier.isStatic(m.getModifiers())) continue;
            if (m.getDeclaringClass() == Object.class) continue;
            if (found != null) return null;   // 多于一个抽象方法 → 不是函数式接口
            found = m;
        }
        return found;
    }

    private static List<String> typeParamNames(Class<?> cls) {
        List<String> names = new ArrayList<>();
        for (TypeVariable<?> tv : cls.getTypeParameters()) {
            names.add(tv.getName());
        }
        return names;
    }

    private static String aliasName(Class<?> cls) {
        return "$" + tsClassName(cls) + "_";
    }

    private static String tsClassName(Class<?> cls) {
        if (cls.getEnclosingClass() != null && !cls.isAnonymousClass()) {
            return tsClassName(cls.getEnclosingClass()) + "$" + cls.getSimpleName();
        }
        return cls.getSimpleName();
    }

    private static Class<?> loadIfPossible(String fqn) {
        try {
            return Class.forName(fqn, false, Thread.currentThread().getContextClassLoader());
        } catch (ClassNotFoundException | LinkageError ignored) {
            return null;
        }
    }
}
