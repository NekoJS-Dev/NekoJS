package com.tkisor.nekojs.api.facade;

import com.tkisor.nekojs.api.annotation.ContractReceiver;
import java.util.Objects;

/**
 * Read-only metadata snapshot of a single mod. Its script-side type name is {@code ModInfo}, set by
 * {@code @ContractReceiver("ModInfo")}.
 *
 * <p>Returned by {@link PlatformFacade#getInfo(String)}. All three fields are non-null, non-blank
 * strings. The record is immutable.
 *
 * @param id      mod id
 * @param name    mod display name
 * @param version mod version string
 */
@ContractReceiver("ModInfo")
public record ModInfoValue(String id, String name, String version) {
    /** Compact constructor: rejects {@code null} or blank components. */
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
