package com.tkisor.nekojs.core.api.facade;

import com.tkisor.nekojs.api.data.NekoId;
import com.tkisor.nekojs.api.facade.IdFacade;

import java.util.Objects;

public final class DefaultIdFacade implements IdFacade {
    @Override
    public NekoId of(String value) {
        return NekoId.of(Objects.requireNonNull(value, "value"));
    }

    @Override
    public NekoId of(String namespace, String path) {
        return NekoId.of(namespace, path);
    }

    @Override
    public String namespace(NekoId id) {
        return Objects.requireNonNull(id, "id").namespace();
    }

    @Override
    public String path(NekoId id) {
        return Objects.requireNonNull(id, "id").path();
    }

    @Override
    public String asString(NekoId id) {
        return Objects.requireNonNull(id, "id").asString();
    }
}
