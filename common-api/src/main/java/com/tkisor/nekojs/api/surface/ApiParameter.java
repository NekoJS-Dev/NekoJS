package com.tkisor.nekojs.api.surface;

import java.util.Objects;

public record ApiParameter(String name, ApiTypeRef type, boolean optional, boolean varargs) {
    public ApiParameter {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(type, "type");
    }
}
