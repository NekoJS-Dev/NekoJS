package com.tkisor.nekojs.api.nbt;

public record NbtBinaryLimits(int maxCompressedBytes, long maxDecodedBytes) {
    public static final NbtBinaryLimits DEFAULT = new NbtBinaryLimits(3 * 1_048_576, 8L * 1_048_576);

    public NbtBinaryLimits {
        if (maxCompressedBytes <= 0) throw new IllegalArgumentException("maxCompressedBytes must be positive");
        if (maxDecodedBytes <= 0) throw new IllegalArgumentException("maxDecodedBytes must be positive");
    }
}
