package com.tkisor.nekojs.api;

import com.tkisor.nekojs.api.annotation.HideFromJS;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.*;
import java.util.*;

/**
 * 成员可见性查询工具。
 * <p>
 * 外部 mod（如 .d.ts 生成器）可通过此类查询任意 Java 类在 NekoJS 沙盒中
 * 对 JavaScript 暴露的成员名称和可见性。
 * 结果使用 {@link JSBinding} 封装，方便区分 public/非public 以及其他修饰符。
 * </p>
 */
public final class MemberVisibilityQuery {

    private MemberVisibilityQuery() {}

    // ==================== 数据结构 ====================

    /**
     * 封装成员在 JS 侧的绑定信息与元数据
     *
     * @param jsName 映射后暴露给 JS 的名称
     * @param member 原始的 Java 反射成员
     * @param <T>    Member 的具体类型（Method, Field, Constructor）
     */
    public record JSBinding<T extends Member>(String jsName, T member) {

        public boolean isPublic() {
            return Modifier.isPublic(member.getModifiers());
        }

        public boolean isProtected() {
            return Modifier.isProtected(member.getModifiers());
        }

        public boolean isPrivate() {
            return Modifier.isPrivate(member.getModifiers());
        }

        public boolean isStatic() {
            return Modifier.isStatic(member.getModifiers());
        }

        /** 字段是否为 final（可用于生成 .d.ts 中的 readonly） */
        public boolean isFinal() {
            return Modifier.isFinal(member.getModifiers());
        }
    }

    // ==================== 类级别 ====================

    /**
     * 判断一个类在 JS 侧是否完全不可见。
     */
    public static boolean isClassVisible(Class<?> clazz) {
        return !clazz.isAnnotationPresent(HideFromJS.class);
    }

    // ==================== 批量查询（遍历继承链） ====================

    /**
     * 获取类中所有对 JS 暴露的实例方法（含继承的，含非 public）。
     * key 为 remap 后的名称。如有名称冲突，子类方法覆盖父类方法。
     *
     * @param clazz 要查询的 Java 类
     * @return remap后方法名 → JSBinding 的不可变映射
     */
    public static Map<String, JSBinding<Method>> getVisibleMethods(Class<?> clazz) {
        Map<String, JSBinding<Method>> result = new LinkedHashMap<>();

        List<Class<?>> hierarchy = getHierarchy(clazz);

        for (Class<?> cls : hierarchy) {
            for (Method m : cls.getDeclaredMethods()) {
                // 跳过桥接方法和合成方法
                if (m.isBridge() || m.isSynthetic()) continue;

                String jsName = remapMember(m);
                if (jsName != null) {
                    result.put(jsName, new JSBinding<>(jsName, m));
                }
            }
        }

        return Collections.unmodifiableMap(result);
    }

    /**
     * 获取类中所有对 JS 暴露的静态字段（含继承的，含非 public）。
     * key 为 remap 后的名称。子类不覆盖父类同名字段。
     *
     * @param clazz 要查询的 Java 类
     * @return remap后字段名 → JSBinding 的不可变映射
     */
    public static Map<String, JSBinding<Field>> getVisibleFields(Class<?> clazz) {
        Map<String, JSBinding<Field>> result = new LinkedHashMap<>();

        Class<?> current = clazz;
        while (current != null && current != Object.class) {
            for (Field f : current.getDeclaredFields()) {
                if (!Modifier.isStatic(f.getModifiers()) || f.isSynthetic()) continue;

                String jsName = remapMember(f);
                if (jsName != null) {
                    // 子类不覆盖父类同名字段
                    result.putIfAbsent(jsName, new JSBinding<>(jsName, f));
                }
            }
            current = current.getSuperclass();
        }

        return Collections.unmodifiableMap(result);
    }

    /**
     * 获取类中所有对 JS 暴露的构造器（含非 public）。
     *
     * @param clazz 要查询的 Java 类
     * @return 可见构造器绑定的不可变列表
     */
    public static List<JSBinding<Constructor<?>>> getVisibleConstructors(Class<?> clazz) {
        if (!isClassVisible(clazz)) {
            return Collections.emptyList();
        }

        List<JSBinding<Constructor<?>>> result = new ArrayList<>();
        // getDeclaredConstructors() 包含 private/protected/public
        for (Constructor<?> ctor : clazz.getDeclaredConstructors()) {
            if (!ctor.isSynthetic()) {
                // 构造器无需 remap，名称固定为其类名（或在JS中用 new 调用）
                String name = clazz.getSimpleName();
                result.add(new JSBinding<>(name, ctor));
            }
        }
        return Collections.unmodifiableList(result);
    }

    // ==================== 辅助与内部逻辑 ====================

    /**
     * 获取类的继承链：从顶层父类（不含 Object）到当前类。
     * 保证父类在前，子类在后，方便 Map 覆盖操作。
     */
    private static List<Class<?>> getHierarchy(Class<?> clazz) {
        List<Class<?>> hierarchy = new ArrayList<>();
        Class<?> current = clazz;
        while (current != null && current != Object.class) {
            hierarchy.add(current);
            current = current.getSuperclass();
        }
        Collections.reverse(hierarchy);
        return hierarchy;
    }

    /** 委托 {@link JavaMemberIndex#remapName}：hideMarker=null（隐藏即从可见集合剔除），strict=true（前缀剥离需非空）。 */
    private static @Nullable String remapMember(Member member) {
        return JavaMemberIndex.remapName(member, null, true);
    }
}