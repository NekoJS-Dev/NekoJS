package com.tkisor.nekojs.api.nbt;

public final class NbtBinaryException extends Exception {
    public enum Reason { INVALID, LIMIT, FILE_SIZE, UNSUPPORTED }

    private final Reason reason;

    public NbtBinaryException(Reason reason, String message, Throwable cause) {
        super(message, cause);
        this.reason = reason;
    }

    public NbtBinaryException(Reason reason, String message) {
        this(reason, message, null);
    }

    public Reason reason() {
        return reason;
    }
}
