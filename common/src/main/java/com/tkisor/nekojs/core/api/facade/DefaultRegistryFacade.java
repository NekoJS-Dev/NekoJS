package com.tkisor.nekojs.core.api.facade;

import com.tkisor.nekojs.api.error.ApiErrorCodes;
import com.tkisor.nekojs.api.error.ApiInvocationException;
import com.tkisor.nekojs.api.facade.RegistryFacade;
import com.tkisor.nekojs.api.facade.RegistryView;
import com.tkisor.nekojs.api.registry.RegistryQueryService;

import java.util.Objects;

public final class DefaultRegistryFacade implements RegistryFacade {
    private final RegistryQueryService service;

    public DefaultRegistryFacade(RegistryQueryService service) {
        this.service = Objects.requireNonNull(service, "service");
    }

    @Override
    public RegistryView get(String registryId) {
        return new DefaultRegistryView(service, requireRegistryId(registryId));
    }

    private static String requireRegistryId(String registryId) {
        Objects.requireNonNull(registryId, "registryId");
        if (registryId.isBlank()) {
            throw new ApiInvocationException(ApiErrorCodes.TYPE_MISMATCH, "Registry id cannot be blank");
        }
        return registryId;
    }
}
