package com.tkisor.nekojs.probe.ir;

import java.util.ArrayList;
import java.util.List;

/**
 * 方法 / 构造器 / getter / setter 声明 IR（mutable）。一节点多用，用标志位区分。
 *
 * <p>渲染语义（与 {@code ClassDeclGenerator} 对齐）：
 * <ul>
 *   <li>{@code isConstructor} → 构造器段</li>
 *   <li>{@code isGetter} → getter 段，渲染为 {@code get prop(): T} + 原方法名 {@code xxx(): T} 双发射；
 *       若 {@code setterParamType != null}，附带 {@code set prop(v: T)}</li>
 *   <li>{@code isSetter}（无配对 getter 的独立 setter）→ 不发射（与旧实现一致：从实例方法段排除且无 getter 配对）</li>
 *   <li>其余 → 静态/实例方法段</li>
 * </ul>
 */
public final class MethodDecl {
    public String name;
    public String renameTo;             // null = 用 name
    public final List<MethodParam> params = new ArrayList<>();
    public TypeSlot returnType;         // 构造器可为 null
    public final List<String> typeParams = new ArrayList<>();   // 方法级泛型名（无上界，与旧输出一致）
    public boolean isStatic;
    public boolean isConstructor;
    public boolean isGetter;
    public boolean isSetter;
    public String property;             // getter/setter 的属性名
    public TypeSlot setterParamType;    // getter 配对的 setter 入参类型；null = 无 setter
    public boolean hidden;
    public final List<String> docs = new ArrayList<>();

    public MethodDecl(String name) {
        this.name = name;
    }

    /** 渲染时使用的名字（renameTo 优先）。 */
    public String effectiveName() {
        return renameTo != null ? renameTo : name;
    }

    /** 方法参数（mutable，供 modify_type 参数级编辑）。 */
    public static final class MethodParam {
        public String name;
        public TypeSlot type;
        public boolean varargs;
        /** TS 可选参数：渲染为 {@code name?: type}（modify_type markOptional 设置）。 */
        public boolean optional;

        public MethodParam(String name, TypeSlot type, boolean varargs) {
            this.name = name;
            this.type = type;
            this.varargs = varargs;
        }
    }
}
