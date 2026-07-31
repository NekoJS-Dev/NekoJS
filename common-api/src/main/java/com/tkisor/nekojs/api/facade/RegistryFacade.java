package com.tkisor.nekojs.api.facade;

public interface RegistryFacade {
    /**
     * 获取指定注册表的只读视图（例如 {@code minecraft:item}）。
     * 注册表不存在时视图的 {@link RegistryView#exists()} 返回 {@code false}。
     */
    RegistryView get(String registryId);
}
