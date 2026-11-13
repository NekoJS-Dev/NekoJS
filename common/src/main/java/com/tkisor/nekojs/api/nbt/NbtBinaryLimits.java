package com.tkisor.nekojs.api.nbt;

/**
 * Resource limits for NBT binary encoding and decoding.
 *
 * <p>Both fields must be positive; {@link #DEFAULT} holds the default limits.
 *
 * @param maxCompressedBytes maximum size in bytes after compression
 * @param maxDecodedBytes    maximum size in bytes after decoding
 */
public record NbtBinaryLimits(int maxCompressedBytes, long maxDecodedBytes) {
    /** Default limits: 3 MiB compressed, 8 MiB decoded. */
    public static final NbtBinaryLimits DEFAULT = new NbtBinaryLimits(3 * 1_048_576, 8L * 1_048_576);

    public NbtBinaryLimits {
        if (maxCompressedBytes <= 0) throw new IllegalArgumentException("maxCompressedBytes must be positive");
        if (maxDecodedBytes <= 0) throw new IllegalArgumentException("maxDecodedBytes must be positive");
    }
}
