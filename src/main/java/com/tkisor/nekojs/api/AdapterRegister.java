package com.tkisor.nekojs.api;

/**
 * JS类型适配器注册接口
 * <p>
 * 用于注册自定义的JavaScript类型适配器，实现Java类型与JavaScript类型之间的转换
 * </p>
 * 
 * @author tkisor
 * @since 1.0
 */
@FunctionalInterface
public interface AdapterRegister {
    /**
     * 注册JS类型适配器
     * 
     * @param <T> 要适配的Java类型
     * @param adapter JS类型适配器实例
     */
    <T> void register(JSTypeAdapter<T> adapter);
}