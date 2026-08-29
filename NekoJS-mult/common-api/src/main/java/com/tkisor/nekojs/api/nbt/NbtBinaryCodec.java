package com.tkisor.nekojs.api.nbt;

import com.tkisor.nekojs.api.data.NbtValue;

/**
 * NBT 二进制编解码器，负责 {@link NbtValue.CompoundValue} 与压缩二进制（如 gzip 的 NBT 文件）之间的转换。
 *
 * <p>平台在支持时提供原生实现；不支持时使用 {@link #unsupported()} 返回一个所有操作
 * 都抛 {@link NbtBinaryException.Reason#UNSUPPORTED} 的占位实现。
 */
public interface NbtBinaryCodec {
    /** 解压并解析压缩 NBT 二进制为 compound；失败抛 {@link NbtBinaryException}。 */
    NbtValue.CompoundValue decodeCompressed(byte[] compressed, NbtBinaryLimits limits) throws NbtBinaryException;

    /** 把 compound 编码为压缩 NBT 二进制；失败抛 {@link NbtBinaryException}。 */
    byte[] encodeCompressed(NbtValue.CompoundValue root, NbtBinaryLimits limits) throws NbtBinaryException;

    /** 返回所有操作均抛 {@link NbtBinaryException.Reason#UNSUPPORTED} 的占位实现。 */
    static NbtBinaryCodec unsupported() {
        return new NbtBinaryCodec() {
            @Override
            public NbtValue.CompoundValue decodeCompressed(byte[] compressed, NbtBinaryLimits limits) throws NbtBinaryException {
                throw new NbtBinaryException(NbtBinaryException.Reason.UNSUPPORTED, "Native NBT binary codec is unavailable");
            }

            @Override
            public byte[] encodeCompressed(NbtValue.CompoundValue root, NbtBinaryLimits limits) throws NbtBinaryException {
                throw new NbtBinaryException(NbtBinaryException.Reason.UNSUPPORTED, "Native NBT binary codec is unavailable");
            }
        };
    }
}
