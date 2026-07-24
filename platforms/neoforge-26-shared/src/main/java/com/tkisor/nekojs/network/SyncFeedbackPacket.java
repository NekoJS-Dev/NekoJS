package com.tkisor.nekojs.network;

import com.tkisor.nekojs.NekoJS;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record SyncFeedbackPacket(boolean success, String message) implements CustomPacketPayload {
    public static final Type<SyncFeedbackPacket> TYPE = new Type<>(Identifier.fromNamespaceAndPath(NekoJS.MODID, "sync_feedback"));

    public static final StreamCodec<FriendlyByteBuf, SyncFeedbackPacket> STREAM_CODEC = StreamCodec.ofMember(
            SyncFeedbackPacket::write, SyncFeedbackPacket::new
    );

    public SyncFeedbackPacket(FriendlyByteBuf buf) {
        this(buf.readBoolean(), buf.readUtf(1024));
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeBoolean(this.success);
        buf.writeUtf(this.message, 1024);
    }

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}