package com.tkisor.nekojs.core.module.esm;

public record NekoEsmBinding(String imported, String local) {
    public NekoEsmBinding {
        if (imported == null || imported.isBlank()) {
            throw new IllegalArgumentException("ESM imported binding must not be blank");
        }
        if (local == null || local.isBlank()) {
            throw new IllegalArgumentException("ESM local binding must not be blank");
        }
    }
}
