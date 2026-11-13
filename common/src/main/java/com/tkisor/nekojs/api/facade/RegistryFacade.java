package com.tkisor.nekojs.api.facade;

/**
 * Registry facade, exposed to scripts as the global object {@code Registry}.
 *
 * <p>Provides read-only access to vanilla and modded game registries. Views are live (queries hit
 * the underlying registry at call time) and safe to cache. No Minecraft native object is ever
 * exposed through this interface.
 */
public interface RegistryFacade {
    /**
     * Returns a read-only view of the given registry, for example {@code minecraft:item}.
     * If the registry does not exist, the view's {@link RegistryView#exists()} returns {@code false}.
     *
     * <p>{@code registryId} must be non-null and non-blank; blank ids throw
     * {@link com.tkisor.nekojs.api.error.ApiInvocationException}. Implementations may
     * return a cached view for repeated ids.
     */
    RegistryView get(String registryId);
}
