package com.tkisor.nekojs.api.data;

import lombok.Getter;

public class Binding {
    @Getter
    private final String name;

    @Getter
    private final Object object;

    @Getter
    private final Class<?> type;

    @Getter
    private final boolean isStaticClass;

    private Binding(String name, Object object, boolean isStaticClass) {
        this.name = name;
        this.object = object;
        this.type = isStaticClass ? (Class<?>) object : object.getClass();
        this.isStaticClass = isStaticClass;
    }

    /**
     * 绑定一个实例对象 (可以调用非静态方法)
     */
    public static Binding of(String name, Object object) {
        if (object instanceof Class<?>) {
            throw new IllegalArgumentException("请使用 Binding.of(name, Class) 来绑定静态类");
        }
        return new Binding(name, object, false);
    }

    /**
     * 绑定一个类 (用于调用静态方法)
     */
    public static Binding of(String name, Class<?> type) {
        return new Binding(name, type, true);
    }
}