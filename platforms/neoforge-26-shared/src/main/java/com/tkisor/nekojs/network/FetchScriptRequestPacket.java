package com.tkisor.nekojs.network;

import com.tkisor.nekojs.NekoJS;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record FetchScriptRequestPacket(String path) implements CustomPacketPayload {
    public static final Type<FetchScriptRequestPacket> TYPE = new Type<>(Identifier.fromNamespaceAndPath(NekoJS.MODID, "fetch_script_req"));
    public static final StreamCodec<FriendlyByteBuf, FetchScriptRequestPacket> STREAM_CODEC = StreamCodec.ofMember(
            FetchScriptRequestPacket::write, FetchScriptRequestPacket::new
    );

    public FetchScriptRequestPacket(FriendlyByteBuf buf) {
        this(buf.readUtf(1024));
    }
    public void write(FriendlyByteBuf buf) {
        buf.writeUtf(this.path, 1024);
    }
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}