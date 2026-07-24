package com.tkisor.nekojs.network;

import com.tkisor.nekojs.NekoJS;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record OpenWorkspacePacket() implements CustomPacketPayload {
    public static final Type<OpenWorkspacePacket> TYPE = new Type<>(Identifier.fromNamespaceAndPath(NekoJS.MODID, "open_workspace"));

    public static final StreamCodec<FriendlyByteBuf, OpenWorkspacePacket> STREAM_CODEC = StreamCodec.unit(new OpenWorkspacePacket());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}