package com.tkisor.nekojs.api.nbt;

import com.tkisor.nekojs.api.data.NbtValue;

public interface NbtBinaryCodec {
    NbtValue.CompoundValue decodeCompressed(byte[] compressed, NbtBinaryLimits limits) throws NbtBinaryException;

    byte[] encodeCompressed(NbtValue.CompoundValue root, NbtBinaryLimits limits) throws NbtBinaryException;

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
