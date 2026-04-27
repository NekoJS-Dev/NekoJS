package com.tkisor.nekojs.api;

import com.tkisor.nekojs.api.annotation.HideFromJS;
import com.tkisor.nekojs.api.annotation.Remap;
import com.tkisor.nekojs.api.annotation.RemapByPrefix;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.*;
import java.util.*;

/**
 * 成员可见性查询工具。
 * <p>
 * 外部 mod（如 .d.ts 生成器）可通过此类查询任意 Java 类在 NekoJS 沙盒中
 * 对 JavaScript 暴露的成员名称和可见性，结果已应用 {@link Remap @Remap}、
 * {@link RemapByPrefix @RemapByPrefix}、{@link HideFromJS @HideFromJS} 规则。
 * </p>
 *
 * <p>规则与 {@code NekoJSMemberRemapper} 完全一致，但返回 {@code null}
 * 表示隐藏，而非 GraalVM 内部常量，适合外部调用。</p>
 *
 * @author ZZZank, Tki_sor
 * @since 1.1
 */
public final class MemberVisibilityQuery {

    private MemberVisibilityQuery() {}

    // ==================== 类级别 ====================

    /**
     * 判断一个类在 JS 侧是否完全不可见。
     * 如果类本身被 {@link HideFromJS @HideFromJS} 标记，返回 {@code false}。
     */
    public static boolean isClassVisible(Class<?> clazz) {
        return !clazz.isAnnotationPresent(HideFromJS.class);
    }

    // ==================== 单成员查询 ====================

    /**
     * 查询某个 Java 方法在 JS 侧的可见名称。
     *
     * @return remap 后的方法名；如果被隐藏则返回 {@code null}
     */
    public static @Nullable String getJSName(Method method) {
        return remapMember(method);
    }

    /**
     * 查询某个 Java 字段在 JS 侧的可见名称。
     *
     * @return remap 后的字段名；如果被隐藏则返回 {@code null}
     */
    public static @Nullable String getJSName(Field field) {
        return remapMember(field);
    }

    // ==================== 批量查询（遍历继承链） ====================

    /**
     * 获取类中所有对 JS 可见的实例方法（含继承的 public 方法），
     * key 为 remap 后的名称。
     * <p>
     * 如有名称冲突，子类方法覆盖父类方法。
     * </p>
     *
     * @param clazz 要查询的 Java 类
     * @return remap后方法名 → Method 的不可变映射
     */
    public static Map<String, Method> getVisibleMethods(Class<?> clazz) {
        Map<String, Method> result = new LinkedHashMap<>();

        // 从父类向子类遍历，子类覆盖父类同名方法
        List<Class<?>> hierarchy = new ArrayList<>();
        Class<?> current = clazz;
        while (current != null && current != Object.class) {
            hierarchy.add(current);
            current = current.getSuperclass();
        }
        Collections.reverse(hierarchy); // 父类在前，子类在后

        for (Class<?> cls : hierarchy) {
            for (Method m : cls.getDeclaredMethods()) {
                // 跳过非 public 和桥接方法（桥接方法由编译器生成）
                if (!Modifier.isPublic(m.getModifiers()) || m.isBridge()) {
                    continue;
                }
                String jsName = remapMember(m);
                if (jsName != null) {
                    result.put(jsName, m);
                }
            }
        }

        return Collections.unmodifiableMap(result);
    }

    /**
     * 获取类中所有对 JS 可见的静态字段（含从父类继承的 public static 字段）。
     * key 为 remap 后的名称。
     * <p>
     * 子类不覆盖父类同名字段（因为 Java 中字段不参与多态）。
     * </p>
     *
     * @param clazz 要查询的 Java 类
     * @return remap后字段名 → Field 的不可变映射
     */
    public static Map<String, Field> getVisibleFields(Class<?> clazz) {
        Map<String, Field> result = new LinkedHashMap<>();

        Class<?> current = clazz;
        while (current != null && current != Object.class) {
            for (Field f : current.getDeclaredFields()) {
                if (!Modifier.isPublic(f.getModifiers()) || !Modifier.isStatic(f.getModifiers())) {
                    continue;
                }
                String jsName = remapMember(f);
                if (jsName != null) {
                    result.putIfAbsent(jsName, f); // 子类不覆盖父类同名字段
                }
            }
            current = current.getSuperclass();
        }

        return Collections.unmodifiableMap(result);
    }

    /**
     * 获取类中所有对 JS 可见的 public 构造器。
     * <p>
     * 构造器不受 {@link Remap @Remap} / {@link RemapByPrefix @RemapByPrefix} 影响，
     * 但声明类若被 {@link HideFromJS @HideFromJS} 标记，则返回空列表。
     * </p>
     *
     * @param clazz 要查询的 Java 类
     * @return 可见构造器的不可变列表
     */
    public static List<Constructor<?>> getVisibleConstructors(Class<?> clazz) {
        if (!isClassVisible(clazz)) {
            return Collections.emptyList();
        }

        List<Constructor<?>> result = new ArrayList<>();
        for (Constructor<?> ctor : clazz.getConstructors()) {
            // getConstructors() 只返回 public，无需再检查 Modifier
            if (!ctor.isSynthetic()) {
                result.add(ctor);
            }
        }
        return Collections.unmodifiableList(result);
    }

    // ==================== 内部：remap 逻辑 ====================
    // 规则与 NekoJSMemberRemapper.remapImpl() 完全一致

    private static @Nullable String remapMember(Member member) {
        AccessibleObject ao = (AccessibleObject) member;

        // 1. 成员或声明类上有 @HideFromJS → 隐藏
        if (ao.isAnnotationPresent(HideFromJS.class)
            || member.getDeclaringClass().isAnnotationPresent(HideFromJS.class)) {
            return null;
        }

        // 2. 成员上有 @Remap("newName") → 直接返回新名称
        Remap remap = ao.getAnnotation(Remap.class);
        if (remap != null) {
            return remap.value();
        }

        String original = member.getName();

        // 3. 成员上有 @RemapByPrefix → 去掉匹配的前缀
        RemapByPrefix remapByPrefix = ao.getAnnotation(RemapByPrefix.class);
        if (remapByPrefix != null) {
            String stripped = findAndRemovePrefix(original, remapByPrefix.value());
            if (stripped != null) {
                return stripped;
            }
        }

        // 4. 声明类上有 @RemapByPrefix → 去掉匹配的前缀
        remapByPrefix = member.getDeclaringClass().getAnnotation(RemapByPrefix.class);
        if (remapByPrefix != null) {
            String stripped = findAndRemovePrefix(original, remapByPrefix.value());
            if (stripped != null) {
                return stripped;
            }
        }

        // 5. 无规则匹配 → 保持原名
        return original;
    }

    private static @Nullable String findAndRemovePrefix(String name, String[] prefixes) {
        for (String prefix : prefixes) {
            if (name.startsWith(prefix)) {
                // 前缀不能等于整个名字（避免去掉前缀后变成空字符串）
                if (name.length() > prefix.length()) {
                    return name.substring(prefix.length());
                }
                return null; // 整个名字就是前缀，去掉后为空 → 视为隐藏
            }
        }
        return null;
    }
}