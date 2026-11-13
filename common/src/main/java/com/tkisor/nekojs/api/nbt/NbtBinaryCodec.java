package com.tkisor.nekojs.api.nbt;

import com.tkisor.nekojs.api.data.NbtValue;

/**
 * NBT binary codec: converts between {@link NbtValue.CompoundValue} and compressed binary, such as a
 * gzip-compressed NBT file.
 *
 * <p>A platform that supports it provides a native implementation; otherwise
 * {@link #unsupported()} returns a placeholder whose operations all throw
 * {@link NbtBinaryException.Reason#UNSUPPORTED}.
 */
public interface NbtBinaryCodec {
    /**
     * Decompresses compressed NBT binary and parses it into a compound; throws
     * {@link NbtBinaryException} on failure.
     */
    NbtValue.CompoundValue decodeCompressed(byte[] compressed, NbtBinaryLimits limits) throws NbtBinaryException;

    /**
     * Encodes a compound as compressed NBT binary; throws {@link NbtBinaryException} on failure.
     */
    byte[] encodeCompressed(NbtValue.CompoundValue root, NbtBinaryLimits limits) throws NbtBinaryException;

    /**
     * Returns a placeholder implementation whose operations all throw
     * {@link NbtBinaryException.Reason#UNSUPPORTED}.
     */
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
