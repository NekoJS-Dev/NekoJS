package com.tkisor.nekojs.api.nbt;

/**
 * NBT 二进制编解码的资源上限。
 *
 * <p>两个字段均须为正数；{@link #DEFAULT} 为默认上限。
 *
 * @param maxCompressedBytes 压缩后字节数上限
 * @param maxDecodedBytes    解码后字节数上限
 */
public record NbtBinaryLimits(int maxCompressedBytes, long maxDecodedBytes) {
    /** 默认上限：压缩 3 MiB，解码 8 MiB。 */
    public static final NbtBinaryLimits DEFAULT = new NbtBinaryLimits(3 * 1_048_576, 8L * 1_048_576);

    public NbtBinaryLimits {
        if (maxCompressedBytes <= 0) throw new IllegalArgumentException("maxCompressedBytes must be positive");
        if (maxDecodedBytes <= 0) throw new IllegalArgumentException("maxDecodedBytes must be positive");
    }
}
