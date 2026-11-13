package com.tkisor.nekojs.probe.events;

import com.tkisor.nekojs.api.surface.ApiTypeRef;

/**
 * A global declaration collected by the {@code probe.add_global} event: a name plus a language-neutral type.
 * Each backend renders it in its own language (TS -> {@code declare const Name: T;}, Python -> {@code Name: T}).
 */
public record GlobalDecl(String name, ApiTypeRef type) {
    public GlobalDecl {
        if (name == null || name.isBlank()) throw new IllegalArgumentException("global name must not be blank");
        if (type == null) throw new IllegalArgumentException("global type must not be null");
    }
}
