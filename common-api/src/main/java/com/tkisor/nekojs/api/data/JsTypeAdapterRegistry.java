package com.tkisor.nekojs.api.data;

import java.util.Collection;

public interface JsTypeAdapterRegistry {
    <T> void register(JsTypeAdapter<T> adapter);

    Collection<JsTypeAdapter<?>> view();
}
