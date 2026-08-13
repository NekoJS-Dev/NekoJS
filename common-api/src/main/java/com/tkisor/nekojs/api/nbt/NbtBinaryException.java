package com.tkisor.nekojs.api.nbt;

/**
 * NBT 二进制编解码失败时抛出的受检异常，携带失败原因分类。
 */
public final class NbtBinaryException extends Exception {
    /** 失败原因分类。 */
    public enum Reason { INVALID, LIMIT, FILE_SIZE, UNSUPPORTED }

    private final Reason reason;

    /**
     * @param reason  失败原因（{@link Reason} 枚举）
     * @param message 说明
     * @param cause   底层原因，可为 {@code null}
     */
    public NbtBinaryException(Reason reason, String message, Throwable cause) {
        super(message, cause);
        this.reason = reason;
    }

    /**
     * @param reason  失败原因（{@link Reason} 枚举）
     * @param message 说明
     */
    public NbtBinaryException(Reason reason, String message) {
        this(reason, message, null);
    }

    /** 返回失败原因。 */
    public Reason reason() {
        return reason;
    }
}
