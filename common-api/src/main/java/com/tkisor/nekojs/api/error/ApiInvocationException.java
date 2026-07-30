package com.tkisor.nekojs.api.error;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public class ApiInvocationException extends RuntimeException {
    private final String code;
    private final Map<String, String> details;

    public ApiInvocationException(String code, String message) {
        this(code, message, Map.of(), null);
    }

    public ApiInvocationException(String code, String message, Map<String, String> details) {
        this(code, message, details, null);
    }

    public ApiInvocationException(
            String code,
            String message,
            Map<String, String> details,
            Throwable cause) {
        super(Objects.requireNonNull(message, "message"), cause);
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("code must not be blank");
        }
        this.code = code;
        this.details = immutableDetails(details);
    }

    public String code() {
        return code;
    }

    public Map<String, String> details() {
        return details;
    }

    private static Map<String, String> immutableDetails(Map<String, String> details) {
        Objects.requireNonNull(details, "details");
        Map<String, String> copy = new LinkedHashMap<>();
        details.forEach((key, value) -> {
            if (key == null || key.isBlank()) {
                throw new IllegalArgumentException("detail key must not be blank");
            }
            copy.put(key, Objects.requireNonNull(value, "detail value for " + key));
        });
        return Map.copyOf(copy);
    }
}
