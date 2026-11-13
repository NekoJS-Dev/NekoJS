package com.tkisor.nekojs.api.surface;

import java.util.Map;
import java.util.Objects;

/**
 * Thrown when API resolution fails. Carries an error code and details.
 *
 * <p>Extends {@link IllegalStateException} because the runtime state cannot satisfy the resolution.
 */
public class ApiResolutionException extends IllegalStateException {

    private static final long serialVersionUID = 1L;

    private final String code;
    // The diagnostic payload is read-only at runtime and the exception is never serialized across
    // processes; the field stays non-transient so serialization cannot silently drop it
    @SuppressWarnings("serial")
    private final Map<String, String> details;

    /** @param code error code, must not be {@code null} */
    public ApiResolutionException(String code, String message) {
        this(code, message, Map.of());
    }

    /**
     * @param code    error code, must not be {@code null}
     * @param message description
     * @param details detail map, must not be {@code null} (copied into an immutable map)
     */
    public ApiResolutionException(String code, String message, Map<String, String> details) {
        super(message);
        Objects.requireNonNull(code, "code");
        Objects.requireNonNull(details, "details");
        this.code = code;
        this.details = Map.copyOf(details);
    }

    /** Returns the error code. */
    public String code() {
        return code;
    }

    /** Returns the details (an immutable map). */
    public Map<String, String> details() {
        return details;
    }
}
