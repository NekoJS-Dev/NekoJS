package com.tkisor.nekojs.network;

import com.tkisor.nekojs.NekoJS;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record ShowErrorScreenPacket(String scriptId, String errorDetails) implements CustomPacketPayload {

    public static final Type<ShowErrorScreenPacket> TYPE = new Type<>(Identifier.fromNamespaceAndPath(NekoJS.MODID, "show_error"));

    public static final StreamCodec<FriendlyByteBuf, ShowErrorScreenPacket> STREAM_CODEC = StreamCodec.ofMember(
            ShowErrorScreenPacket::write,
            ShowErrorScreenPacket::new
    );

    public ShowErrorScreenPacket(FriendlyByteBuf buf) {
        this(buf.readUtf(), buf.readUtf(32767));
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeUtf(this.scriptId);
        buf.writeUtf(this.errorDetails, 32767);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}