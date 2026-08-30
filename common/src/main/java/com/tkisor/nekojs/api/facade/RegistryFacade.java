package com.tkisor.nekojs.api.facade;

/**
 * Registry facade, exposed to scripts as the global object {@code Registry}.
 *
 * <p>Provides read-only access to vanilla and modded game registries. Views are live
 * (queries hit the underlying registry at call time) and safe to cache. No Minecraft
 * native objects ever cross this boundary.
 */
public interface RegistryFacade {
    /**
     * 获取指定注册表的只读视图（例如 {@code minecraft:item}）。
     * 注册表不存在时视图的 {@link RegistryView#exists()} 返回 {@code false}。
     *
     * <p>{@code registryId} must be non-null and non-blank; blank ids throw
     * {@link com.tkisor.nekojs.api.error.ApiInvocationException}. Implementations may
     * return a cached view for repeated ids.
     */
    RegistryView get(String registryId);
}
