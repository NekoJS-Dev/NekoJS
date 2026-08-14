package com.tkisor.nekojs.api;

import com.tkisor.nekojs.api.annotation.HideFromJS;
import com.tkisor.nekojs.api.annotation.Remap;
import com.tkisor.nekojs.api.annotation.RemapByPrefix;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.AccessibleObject;
import java.lang.reflect.Field;
import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Java 成员反射索引：统一承载「Java 对象暴露给 JS 时的成员名收集 / 拼写建议 / 注解驱动的成员名重映射」，
 * 供加载时静态校验器（{@code GlobalBindingMemberValidator}、{@code EventCallbackSourceValidator}）与
 * 离线工具（{@link MemberVisibilityQuery}）、运行时重映射（{@code NekoJSMemberRemapper}）共享，
 * 消除原先散布在 EventProxy / MemberVisibilityQuery / NekoJSMemberRemapper 三处的重复反射逻辑。
 *
 * <p>所有按 {@link Class} 收集的结果均有 {@link ConcurrentHashMap} 缓存，反射每类只跑一次。
 *
 * <p>本类不涉及运行时成员访问拦截——事件对象直接走 GraalJS 原生 host access，
 * 存在性校验统一在脚本加载时由静态扫描器完成。
 */
public final class JavaMemberIndex {

    private JavaMemberIndex() {}

    private static final int SUGGEST_MAX_DISTANCE = 3;
    private static final Map<Class<?>, Set<String>> PROPERTY_MEMBERS_CACHE = new ConcurrentHashMap<>();
    private static final Map<Class<?>, Set<String>> ALL_MEMBERS_CACHE = new ConcurrentHashMap<>();
    private static final Map<Class<?>, ExposedMembers> EXPOSED_MEMBERS_CACHE = new ConcurrentHashMap<>();

    // ==================== 类型化成员索引（供链式表达式解析） ====================

    /**
     * 保留重载的类型化成员索引：方法按 JS 可见名分组并保留全部重载，
     * getter 属性（{@code getX→x}/{@code isX→x}/{@code hasX→x}）与 public 实例字段
     * 单独分组。名字经 {@link #remapName} 归一化（尊重 {@code @HideFromJS}/
     * {@code @Remap}/{@code @RemapByPrefix}），与运行时 {@code NekoJSMemberRemapper}
     * 的暴露名一致。
     *
     * <p>与 {@link #propertyMembersOf}（名字集合，不处理 remap、折叠重载）不同，
     * 本索引保留每个成员的 {@code Type}，供链式成员访问（{@code e.getPlayer().getServer()}）
     * 按返回类型逐跳推导。
     */
    public record ExposedMembers(
            Map<String, List<Method>> methods,
            Map<String, List<Method>> propertyGetters,
            Map<String, List<Field>> fields) {

        public ExposedMembers {
            methods = Map.copyOf(methods);
            propertyGetters = Map.copyOf(propertyGetters);
            fields = Map.copyOf(fields);
        }

        public boolean hasMember(String name) {
            return methods.containsKey(name) || propertyGetters.containsKey(name) || fields.containsKey(name);
        }

        /**
         * 方法调用候选：按参数数量过滤固定参数 / varargs 重载。
         * 返回全部候选的返回类型（保守 union），不任意挑选单个重载。
         */
        public List<Type> callReturnTypes(String name, int argCount) {
            List<Method> candidates = methods.get(name);
            if (candidates == null) return List.of();
            List<Type> returns = new java.util.ArrayList<>();
            for (Method m : candidates) {
                int fixed = m.getParameterCount();
                boolean varargs = m.isVarArgs();
                if (varargs ? argCount >= fixed - 1 : argCount == fixed) {
                    returns.add(m.getGenericReturnType());
                }
            }
            return returns;
        }

        /** 属性访问候选：getter 属性 + 同名 public 实例字段的类型。 */
        public List<Type> propertyTypes(String name) {
            List<Type> types = new java.util.ArrayList<>();
            List<Method> getters = propertyGetters.get(name);
            if (getters != null) {
                for (Method g : getters) types.add(g.getGenericReturnType());
            }
            List<Field> fieldList = fields.get(name);
            if (fieldList != null) {
                for (Field f : fieldList) types.add(f.getGenericType());
            }
            return types;
        }
    }

    public static ExposedMembers exposedMembersOf(Class<?> clazz) {
        return EXPOSED_MEMBERS_CACHE.computeIfAbsent(clazz, JavaMemberIndex::collectExposedMembers);
    }

    private static ExposedMembers collectExposedMembers(Class<?> clazz) {
        Map<String, List<Method>> methods = new java.util.TreeMap<>();
        Map<String, List<Method>> propertyGetters = new java.util.TreeMap<>();
        Map<String, List<Field>> fields = new java.util.TreeMap<>();
        for (Method m : clazz.getMethods()) {
            if (isHiddenOrInternal(m)) continue;
            String jsName = remapName(m, HIDE_MARKER, m.getName());
            if (jsName == null) continue;
            methods.computeIfAbsent(jsName, ignored -> new java.util.ArrayList<>()).add(m);
            if (m.getParameterCount() == 0) {
                String prop = propertyName(m.getName());
                if (prop != null) {
                    String jsProp = remapName(m, HIDE_MARKER, prop);
                    if (jsProp != null) {
                        propertyGetters.computeIfAbsent(jsProp, ignored -> new java.util.ArrayList<>()).add(m);
                    }
                }
            }
        }
        for (Field f : clazz.getFields()) {
            if (java.lang.reflect.Modifier.isStatic(f.getModifiers())) continue;
            if (f.getDeclaringClass() == Object.class) continue;
            String jsName = remapName(f, HIDE_MARKER, f.getName());
            if (jsName == null) continue;
            fields.computeIfAbsent(jsName, ignored -> new java.util.ArrayList<>()).add(f);
        }
        return new ExposedMembers(methods, propertyGetters, fields);
    }

    private static final String HIDE_MARKER = "\u0000nekojs-hide";

    private static boolean isHiddenOrInternal(Method m) {
        String name = m.getName();
        if ("getClass".equals(name) || name.startsWith("neko$")) return true;
        if (m.getDeclaringClass() == Object.class) return true;
        if (m.isBridge() || m.isSynthetic()) return true;
        return false;
    }

    /** 把返回 {@code Type} 解析为可直接做成员查询的类集合；无法确定时返回空（调用方标 unknown）。 */
    public static List<Class<?>> typeClasses(Type type) {
        List<Class<?>> result = new java.util.ArrayList<>();
        if (type instanceof Class<?> c) {
            if (c == void.class || c.isPrimitive()) return List.of();
            result.add(c);
        } else if (type instanceof java.lang.reflect.ParameterizedType pt
                && pt.getRawType() instanceof Class<?> c) {
            result.add(c);
        } else if (type instanceof java.lang.reflect.TypeVariable<?> tv) {
            for (Type bound : tv.getBounds()) {
                result.addAll(typeClasses(bound));
            }
        } else if (type instanceof java.lang.reflect.WildcardType wt) {
            for (Type upper : wt.getUpperBounds()) {
                result.addAll(typeClasses(upper));
            }
        }
        return result;
    }

    // ==================== 成员名集合（供加载时校验） ====================

    /**
     * 面向只读事件对象回调首参的成员名集合：每个 public 方法名（原样）+ 无参 getter 属性名
     * （{@code getX→x}、{@code isX→x}、{@code hasX→x}）+ public 字段名。
     * 排除 {@code getClass}、{@code neko$} 前缀、{@code Object.class} 声明的方法。
     */
    public static Set<String> propertyMembersOf(Class<?> clazz) {
        return PROPERTY_MEMBERS_CACHE.computeIfAbsent(clazz, JavaMemberIndex::collectPropertyMembers);
    }

    /**
     * 面向全局绑定（{@code Utils}/{@code Items}/{@code Platform}）的宽集合：每个 public 方法名（原样）
     * + getter 属性名 + public 字段名。全局绑定以有参方法调用为主，误报合法方法比漏报更糟，故取并集。
     *
     * <p><b>历史遗留不对称</b>（切勿"修复"，否则改变校验器报错集）：与 {@link #collectPropertyMembers} 不同，
     * 本方法<b>不</b>排除 {@code Object.class} 声明的方法，且对<b>所有</b>方法（含参）推导属性名。
     */
    public static Set<String> allMembersOf(Class<?> clazz) {
        return ALL_MEMBERS_CACHE.computeIfAbsent(clazz, JavaMemberIndex::collectAllMembers);
    }

    /** 基于已知成员集合的拼写建议：Levenshtein 距离 ≤ {@value #SUGGEST_MAX_DISTANCE} 的最近成员，无则 null。 */
    public static String suggestMember(Set<String> members, String key) {
        return suggest(key, members);
    }

    /** 基于类的 {@link #propertyMembersOf} 的拼写建议。 */
    public static String suggestMember(Class<?> clazz, String key) {
        return suggest(key, propertyMembersOf(clazz));
    }

    /** 未知成员的标准错误信息（含 Did-You-Mean 建议）。 */
    public static String unknownMemberMessage(Class<?> clazz, String key) {
        String suggest = suggestMember(clazz, key);
        return "Event '" + clazz.getSimpleName() + "' has no member '" + key + "'." +
                (suggest != null ? " Did you mean '" + suggest + "'?" : "");
    }

    /** 推导属性的类型：{@code entity}→{@code getEntity()} 返回类型，或同名 public 字段类型；无则 null。 */
    public static Class<?> memberType(Class<?> clazz, String key) {
        if (clazz == null || key == null || key.isBlank()) {
            return null;
        }
        String cap = Character.toUpperCase(key.charAt(0)) + key.substring(1);
        try {
            return clazz.getMethod("get" + cap).getReturnType();
        } catch (NoSuchMethodException ignored) {
        }
        try {
            return clazz.getMethod("is" + cap).getReturnType();
        } catch (NoSuchMethodException ignored) {
        }
        try {
            return clazz.getMethod("has" + cap).getReturnType();
        } catch (NoSuchMethodException ignored) {
        }
        Field field = findField(clazz, key);
        return field != null ? field.getType() : null;
    }

    // ==================== 注解驱动的成员名重映射（共享原语） ====================

    /**
     * remapName 的缓存 key。两个 marker 必须都参与：各调用方的 marker 语义不同
     * （可见性查询传 null / 原名，运行时 remapper 传 {@code MemberRemapper.HIDE_MEMBER} /
     * {@code MemberRemapper.FALL_THROUGH}，ExposedMembers 用 {@code HIDE_MARKER}），
     * 只按 member 缓存会把一方的 marker 串给另一方。结果值按 member 天然有界
     * （每成员至多几个调用方组合）。
     */
    private record RemapKey(Member member, String hideMarker, String fallThroughMarker) {}

    private static final Map<RemapKey, String> REMAP_NAME_CACHE = new ConcurrentHashMap<>();

    /**
     * 无副作用的成员名重映射，按优先级：
     * {@code @HideFromJS}（成员级或类级）&gt; {@code @Remap}（成员级）&gt;
     * {@code @RemapByPrefix}（成员级）&gt; {@code @RemapByPrefix}（类级）&gt; 原名。
     *
     * <p>调用方可通过 marker 保持各自的未命中语义，避免将原名误作 Graal remapper chain 的终止结果。
     * @param hideMarker 命中 {@code @HideFromJS} 时的返回值。{@link MemberVisibilityQuery} 传 {@code null}
     *                   （表示从可见集合中剔除）；{@code NekoJSMemberRemapper} 传
     *                   {@code MemberRemapper.HIDE_MEMBER} 常量（满足 graal.mod.api SPI 约定）
     * @param fallThroughMarker 没有命中任何 remap 时的返回值。可见性查询传 {@link Member#getName()}；
     *                          Graal remapper chain 传 {@code MemberRemapper.FALL_THROUGH}。
     */
    public static @Nullable String remapName(Member member, String hideMarker, String fallThroughMarker) {
        RemapKey key = new RemapKey(member, hideMarker, fallThroughMarker);
        String cached = REMAP_NAME_CACHE.get(key);
        if (cached != null) return cached;
        // 结果为 null（可见性查询剔除隐藏成员）时不缓存：ConcurrentHashMap 不允许 null 值，
        // 且该路径只在脚本加载期出现，不在运行时 remapper 热路径上。
        String result = computeRemapName(member, hideMarker, fallThroughMarker);
        if (result != null) {
            String raced = REMAP_NAME_CACHE.putIfAbsent(key, result);
            if (raced != null) return raced;
        }
        return result;
    }

    private static @Nullable String computeRemapName(Member member, String hideMarker, String fallThroughMarker) {
        AccessibleObject ao = (AccessibleObject) member;

        if (ao.isAnnotationPresent(HideFromJS.class)
                || member.getDeclaringClass().isAnnotationPresent(HideFromJS.class)) {
            return hideMarker;
        }

        Remap remap = ao.getAnnotation(Remap.class);
        if (remap != null) {
            return remap.value();
        }

        String original = member.getName();

        RemapByPrefix memberPrefix = ao.getAnnotation(RemapByPrefix.class);
        if (memberPrefix != null) {
            String stripped = findAndRemovePrefix(original, memberPrefix.value());
            if (stripped != null) return stripped;
        }

        RemapByPrefix classPrefix = member.getDeclaringClass().getAnnotation(RemapByPrefix.class);
        if (classPrefix != null) {
            String stripped = findAndRemovePrefix(original, classPrefix.value());
            if (stripped != null) return stripped;
        }

        return fallThroughMarker;
    }

    // ==================== 内部原语 ====================

    private static @Nullable String findAndRemovePrefix(String name, String[] prefixes) {
        for (String prefix : prefixes) {
            if (name.length() > prefix.length() && name.startsWith(prefix)) {
                return name.substring(prefix.length());
            }
        }
        return null;
    }

    private static Set<String> collectPropertyMembers(Class<?> clazz) {
        Set<String> members = new LinkedHashSet<>();
        for (Method m : clazz.getMethods()) {
            String name = m.getName();
            if ("getClass".equals(name) || name.startsWith("neko$") || m.getDeclaringClass() == Object.class) {
                continue;
            }
            members.add(name);
            if (m.getParameterCount() == 0) {
                String prop = propertyName(name);
                if (prop != null) members.add(prop);
            }
        }
        for (Field f : clazz.getFields()) {
            members.add(f.getName());
        }
        return members;
    }

    private static Set<String> collectAllMembers(Class<?> clazz) {
        Set<String> members = new LinkedHashSet<>();
        for (Method m : clazz.getMethods()) {
            String name = m.getName();
            if ("getClass".equals(name) || name.startsWith("neko$")) {
                continue;
            }
            members.add(name);
            String prop = propertyName(name);
            if (prop != null) {
                members.add(prop);
            }
        }
        for (Field f : clazz.getFields()) {
            members.add(f.getName());
        }
        return members;
    }

    private static String propertyName(String methodName) {
        if (methodName.startsWith("get") && methodName.length() > 3) {
            return Character.toLowerCase(methodName.charAt(3)) + methodName.substring(4);
        }
        if (methodName.startsWith("is") && methodName.length() > 2) {
            return Character.toLowerCase(methodName.charAt(2)) + methodName.substring(3);
        }
        if (methodName.startsWith("has") && methodName.length() > 3) {
            return Character.toLowerCase(methodName.charAt(3)) + methodName.substring(4);
        }
        return null;
    }

    private static Field findField(Class<?> clazz, String name) {
        try {
            return clazz.getField(name);
        } catch (NoSuchFieldException e) {
            return null;
        }
    }

    private static String suggest(String key, Set<String> members) {
        if (key == null || key.isEmpty()) return null;
        String best = null;
        int bestDist = Integer.MAX_VALUE;
        for (String member : members) {
            int d = levenshtein(key, member);
            if (d < bestDist) {
                bestDist = d;
                best = member;
            }
        }
        return bestDist <= SUGGEST_MAX_DISTANCE ? best : null;
    }

    private static int levenshtein(String a, String b) {
        int n = a.length(), m = b.length();
        int[] prev = new int[m + 1];
        int[] curr = new int[m + 1];
        for (int j = 0; j <= m; j++) prev[j] = j;
        for (int i = 1; i <= n; i++) {
            curr[0] = i;
            for (int j = 1; j <= m; j++) {
                curr[j] = Math.min(
                    Math.min(prev[j] + 1, curr[j - 1] + 1),
                    prev[j - 1] + (a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1)
                );
            }
            int[] tmp = prev; prev = curr; curr = tmp;
        }
        return prev[m];
    }
}
