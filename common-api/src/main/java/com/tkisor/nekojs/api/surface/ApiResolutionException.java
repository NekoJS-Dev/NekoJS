package com.tkisor.nekojs.api.surface;

import java.util.Map;
import java.util.Objects;

public class ApiResolutionException extends IllegalStateException {

    private final String code;
    private final Map<String, String> details;

    public ApiResolutionException(String code, String message) {
        this(code, message, Map.of());
    }

    public ApiResolutionException(String code, String message, Map<String, String> details) {
        super(message);
        Objects.requireNonNull(code, "code");
        Objects.requireNonNull(details, "details");
        this.code = code;
        this.details = Map.copyOf(details);
    }

    public String code() {
        return code;
    }

    public Map<String, String> details() {
        return details;
    }
}
