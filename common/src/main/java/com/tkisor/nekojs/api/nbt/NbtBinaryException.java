package com.tkisor.nekojs.api.nbt;

/**
 * Checked exception thrown when NBT binary encoding or decoding fails. It carries a
 * {@link Reason} that classifies the failure.
 */
public final class NbtBinaryException extends Exception {
    private static final long serialVersionUID = 1L;

    /** Classifies why encoding or decoding failed. */
    public enum Reason { INVALID, LIMIT, FILE_SIZE, UNSUPPORTED }

    private final Reason reason;

    /**
     * @param reason  why encoding or decoding failed, as a {@link Reason} value
     * @param message the detail message
     * @param cause   the underlying cause, or {@code null}
     */
    public NbtBinaryException(Reason reason, String message, Throwable cause) {
        super(message, cause);
        this.reason = reason;
    }

    /**
     * @param reason  why encoding or decoding failed, as a {@link Reason} value
     * @param message the detail message
     */
    public NbtBinaryException(Reason reason, String message) {
        this(reason, message, null);
    }

    /** Returns the failure reason. */
    public Reason reason() {
        return reason;
    }
}
