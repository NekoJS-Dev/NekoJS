package com.tkisor.nekojs.network;

import com.tkisor.nekojs.NekoJS;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record FetchAllScriptsRequestPacket() implements CustomPacketPayload {
    public static final Type<FetchAllScriptsRequestPacket> TYPE = new Type<>(Identifier.fromNamespaceAndPath(NekoJS.MODID, "fetch_all_scripts_req"));
    public static final StreamCodec<FriendlyByteBuf, FetchAllScriptsRequestPacket> STREAM_CODEC = StreamCodec.ofMember(
            FetchAllScriptsRequestPacket::write, FetchAllScriptsRequestPacket::new
    );

    public FetchAllScriptsRequestPacket(FriendlyByteBuf buf) { this(); }
    public void write(FriendlyByteBuf buf) {}
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}