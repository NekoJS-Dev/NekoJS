package com.tkisor.nekojs.probe.ir;

import java.util.ArrayList;
import java.util.List;

/**
 * 字段声明 IR（mutable）。涵盖普通字段、静态常量、枚举常量（{@code isEnumConstant}）。
 */
public final class FieldDecl {
    public String name;
    public String renameTo;        // null = 用 name
    public TypeSlot type;
    public boolean isStatic;
    public boolean isFinal;
    public boolean isEnumConstant;
    public boolean hidden;
    public final List<String> docs = new ArrayList<>();

    public FieldDecl(String name, TypeSlot type) {
        this.name = name;
        this.type = type;
    }

    /** 渲染时使用的名字（renameTo 优先）。 */
    public String effectiveName() {
        return renameTo != null ? renameTo : name;
    }
}
