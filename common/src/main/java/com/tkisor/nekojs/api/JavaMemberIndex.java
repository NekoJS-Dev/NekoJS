package com.tkisor.nekojs.api;

import com.tkisor.nekojs.api.annotation.HideFromJS;
import com.tkisor.nekojs.api.annotation.Remap;
import com.tkisor.nekojs.api.annotation.RemapByPrefix;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.AccessibleObject;
import java.lang.reflect.Field;
import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.util.LinkedHashSet;
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
     * 无副作用的成员名重映射，按优先级：
     * {@code @HideFromJS}（成员级或类级）&gt; {@code @Remap}（成员级）&gt;
     * {@code @RemapByPrefix}（成员级）&gt; {@code @RemapByPrefix}（类级）&gt; 原名。
     *
     * <p>两个调用方 historically 有两处细微差异，通过参数参数化，避免逻辑写两遍：
     * @param hideMarker 命中 {@code @HideFromJS} 时的返回值。{@link MemberVisibilityQuery} 传 {@code null}
     *                   （表示从可见集合中剔除）；{@code NekoJSMemberRemapper} 传
     *                   {@code MemberRemapper.HIDE_MEMBER} 常量（满足 graal.mod.api SPI 约定）
     * @param strict     {@code @RemapByPrefix} 剥离前缀时是否要求 {@code name.length() > prefix.length()}
     *                   （{@link MemberVisibilityQuery} 传 true，避免空名；{@code NekoJSMemberRemapper} 传 false）
     */
    public static @Nullable String remapName(Member member, String hideMarker, boolean strict) {
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
            String stripped = findAndRemovePrefix(original, memberPrefix.value(), strict);
            if (stripped != null) return stripped;
        }

        RemapByPrefix classPrefix = member.getDeclaringClass().getAnnotation(RemapByPrefix.class);
        if (classPrefix != null) {
            String stripped = findAndRemovePrefix(original, classPrefix.value(), strict);
            if (stripped != null) return stripped;
        }

        return original;
    }

    // ==================== 内部原语 ====================

    private static @Nullable String findAndRemovePrefix(String name, String[] prefixes, boolean strict) {
        for (String prefix : prefixes) {
            if (name.startsWith(prefix)) {
                if (strict && name.length() <= prefix.length()) continue;
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
