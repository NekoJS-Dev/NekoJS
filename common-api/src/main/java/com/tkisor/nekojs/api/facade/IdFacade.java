package com.tkisor.nekojs.api.facade;

import com.tkisor.nekojs.api.data.NekoId;

public interface IdFacade {
    NekoId of(String value);

    NekoId of(String namespace, String path);

    String namespace(NekoId id);

    String path(NekoId id);

    String asString(NekoId id);
}
