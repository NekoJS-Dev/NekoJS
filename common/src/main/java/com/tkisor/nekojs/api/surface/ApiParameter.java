package com.tkisor.nekojs.api.surface;

import java.util.Objects;

/**
 * One parameter of an API signature: name, type, optional flag and varargs flag.
 *
 * @param name     parameter name, must not be {@code null}
 * @param type     parameter type, must not be {@code null}
 * @param optional whether the parameter is optional
 * @param varargs  whether this is a varargs parameter (must come last)
 */
public record ApiParameter(String name, ApiTypeRef type, boolean optional, boolean varargs) {
    public ApiParameter {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(type, "type");
    }
}
