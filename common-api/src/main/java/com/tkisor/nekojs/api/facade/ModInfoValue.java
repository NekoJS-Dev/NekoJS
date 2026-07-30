package com.tkisor.nekojs.api.facade;

import java.util.Objects;

public record ModInfoValue(String id, String name, String version) {
    public ModInfoValue {
        id = requireText(id, "id");
        name = requireText(name, "name");
        version = requireText(version, "version");
    }

    private static String requireText(String value, String field) {
        Objects.requireNonNull(value, field);
        if (value.isBlank()) {
            throw new IllegalArgumentException(field + " cannot be blank");
        }
        return value;
    }
}
