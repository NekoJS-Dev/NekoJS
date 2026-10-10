package com.tkisor.nekojs.probe.ir;

import java.util.ArrayList;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Mutable method metadata shared by declaration backends, including constructors and Bean accessors.
 * Accessor flags retain the real method name and the optional Bean property's read/write types.
 * Each backend decides how to represent these aliases alongside real fields and overloads.
 */
public final class MethodDecl {
    public String name;
    public String renameTo;             // null = 用 name
    public final List<MethodParam> params = new ArrayList<>();
    public TypeSlot returnType;         // 构造器可为 null
    public final List<String> typeParams = new ArrayList<>();
    /** Bound slots retain import dependencies for method type parameter declarations. */
    public final Map<String, List<TypeSlot>> typeParameterBounds = new LinkedHashMap<>();
    public boolean isStatic;
    public boolean isConstructor;
    public boolean isGetter;
    public boolean isSetter;
    public String property;             // getter/setter 的属性名
    public TypeSlot setterParamType;    // getter 配对的 setter 入参类型；null = 无 setter
    public boolean hidden;
    public final List<String> docs = new ArrayList<>();
    /** 手写重载（{@code @Overload} 注解），渲染为同名附加声明。 */
    public final List<Overload> overloads = new ArrayList<>();

    public MethodDecl(String name) {
        this.name = name;
    }

    /**
     * 一条手写重载声明。参数条目与返回类型是 TypeScript 片段原样输出（{@code name: Type}），
     * 不走 {@link TypeSlot} 反射链——这些签名本来就出现在反射表达不了的位置。
     */
    public static final class Overload {
        public final List<String> params;
        /** 空串 = 沿用方法的反射返回类型（构造器无返回值语义）。 */
        public final String returns;
        public final List<String> docs;

        public Overload(List<String> params, String returns, List<String> docs) {
            this.params = List.copyOf(params);
            this.returns = returns == null ? "" : returns;
            this.docs = List.copyOf(docs);
        }
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
