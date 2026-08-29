package com.tkisor.nekojs.core.api.json;

public final class JsonValueException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public enum Reason { INVALID_JSON, LIMIT_EXCEEDED }

    private final Reason reason;

    private JsonValueException(Reason reason, String message) {
        super(message);
        this.reason = reason;
    }

    public Reason reason() {
        return reason;
    }

    public static JsonValueException invalid(String message) {
        return new JsonValueException(Reason.INVALID_JSON, message);
    }

    public static JsonValueException limit(String message) {
        return new JsonValueException(Reason.LIMIT_EXCEEDED, message);
    }
}
