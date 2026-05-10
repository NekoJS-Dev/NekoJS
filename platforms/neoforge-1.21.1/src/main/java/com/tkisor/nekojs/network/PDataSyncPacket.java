package com.tkisor.nekojs.network;

import com.tkisor.nekojs.NekoJS;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record PDataSyncPacket(int entityId, int revision, CompoundTag data) implements CustomPacketPayload {
    public static final Type<PDataSyncPacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(NekoJS.MODID, "pdata_sync"));

    public static final StreamCodec<RegistryFriendlyByteBuf, PDataSyncPacket> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, PDataSyncPacket::entityId,
            ByteBufCodecs.VAR_INT, PDataSyncPacket::revision,
            ByteBufCodecs.COMPOUND_TAG, PDataSyncPacket::data,
            PDataSyncPacket::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
