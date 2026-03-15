package com.tkisor.nekojs.api;

@FunctionalInterface
public interface BindingsRegister {
    /**
     * 将一个 Java 对象注册为 JS 全局变量
     * @param name JS 中的变量名 (如 "Item")
     * @param object Java 对象实例 (如 new ItemStatic())
     */
    void register(String name, Object object);
}