package com.tkisor.nekojs.api.error;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Thrown when an API call fails. It carries a stable error code and structured details.
 *
 * <p>Extends {@link RuntimeException}. The error code must not be empty; details are validated
 * and copied into an immutable map. Scripts read the code through {@code err.code} (see
 * {@link ApiErrorCodes} for the values).
 */
public class ApiInvocationException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    private final String code;
    // The diagnostic payload is read-only at runtime and the exception is never serialized across a
    // process boundary. The field is kept, rather than made transient, so serialization does not
    // silently drop it
    @SuppressWarnings("serial")
    private final Map<String, String> details;

    /**
     * @param code     error code, must not be empty
     * @param message  description, must not be {@code null}
     */
    public ApiInvocationException(String code, String message) {
        this(code, message, Map.of(), null);
    }

    /**
     * @param code     error code, must not be empty
     * @param message  description, must not be {@code null}
     * @param details  details, must not be {@code null}
     */
    public ApiInvocationException(String code, String message, Map<String, String> details) {
        this(code, message, details, null);
    }

    /**
     * @param code     error code, must not be empty
     * @param message  description, must not be {@code null}
     * @param details  details, must not be {@code null}
     * @param cause    underlying cause, may be {@code null}
     */
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

    /** Returns the error code. */
    public String code() {
        return code;
    }

    /** Returns the details (an immutable map). */
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
