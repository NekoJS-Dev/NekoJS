package com.tkisor.nekojs.core.api.facade;

import com.tkisor.nekojs.api.error.ApiErrorCodes;
import com.tkisor.nekojs.api.error.ApiInvocationException;
import com.tkisor.nekojs.api.facade.RegistryFacade;
import com.tkisor.nekojs.api.facade.RegistryView;
import com.tkisor.nekojs.api.registry.RegistryQueryService;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public final class DefaultRegistryFacade implements RegistryFacade {
    /**
     * Caps the view cache. Scripts supply registryId themselves, including for their own
     * registries, so the key space is unknown. {@link DefaultRegistryView} is stateless, so a
     * simple access-ordered LRU cap keeps the hot path free of new views (a script calling
     * {@code Registry.get(...)} every tick reuses the cached view) and bounds memory at the
     * same time.
     */
    private static final int MAX_CACHED_VIEWS = 256;

    private final RegistryQueryService service;
    // A DefaultRegistryView holds only two final fields, service and registryId, and delegates
    // every method to service, so views with the same (service, registryId) behave identically
    // and can be reused. The cache belongs to one instance, so service stays fixed.
    private final Map<String, RegistryView> viewCache =
            Collections.synchronizedMap(new LinkedHashMap<>(64, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, RegistryView> eldest) {
                    return size() > MAX_CACHED_VIEWS;
                }
            });

    public DefaultRegistryFacade(RegistryQueryService service) {
        this.service = Objects.requireNonNull(service, "service");
    }

    @Override
    public RegistryView get(String registryId) {
        String id = requireRegistryId(registryId);
        return viewCache.computeIfAbsent(id, key -> new DefaultRegistryView(service, key));
    }

    private static String requireRegistryId(String registryId) {
        Objects.requireNonNull(registryId, "registryId");
        if (registryId.isBlank()) {
            throw new ApiInvocationException(ApiErrorCodes.TYPE_MISMATCH, "Registry id cannot be blank");
        }
        return registryId;
    }
}
