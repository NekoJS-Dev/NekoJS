package com.tkisor.nekojs.core.api.facade;

import com.tkisor.nekojs.api.error.ApiErrorCodes;
import com.tkisor.nekojs.api.error.ApiInvocationException;
import com.tkisor.nekojs.api.facade.RegistryView;
import com.tkisor.nekojs.api.registry.RegistryQueryService;

import java.util.List;
import java.util.Objects;

public final class DefaultRegistryView implements RegistryView {
    private final RegistryQueryService service;
    private final String registryId;

    DefaultRegistryView(RegistryQueryService service, String registryId) {
        this.service = Objects.requireNonNull(service, "service");
        this.registryId = registryId;
    }

    @Override
    public boolean exists() {
        return service.hasRegistry(registryId);
    }

    @Override
    public List<String> all() {
        return service.all(registryId);
    }

    @Override
    public boolean has(String id) {
        return service.has(registryId, requireId(id));
    }

    @Override
    public List<String> tag(String tagId) {
        return service.tag(registryId, requireId(tagId));
    }

    @Override
    public List<String> dataMapIds() {
        return service.dataMapIds(registryId);
    }

    @Override
    public String dataMapValue(String dataMapTypeId, String id) {
        return service.dataMapValue(registryId, requireId(dataMapTypeId), requireId(id));
    }

    private static String requireId(String id) {
        Objects.requireNonNull(id, "id");
        if (id.isBlank()) {
            throw new ApiInvocationException(ApiErrorCodes.TYPE_MISMATCH, "Id cannot be blank");
        }
        return id;
    }
}
