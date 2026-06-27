package com.tkisor.nekojs.network;

import com.tkisor.nekojs.NekoJS;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record SaveScriptPacket(String path, String content) implements CustomPacketPayload {
    public static final Type<SaveScriptPacket> TYPE = new Type<>(Identifier.fromNamespaceAndPath(NekoJS.MODID, "save_script"));
    public static final StreamCodec<FriendlyByteBuf, SaveScriptPacket> STREAM_CODEC = StreamCodec.ofMember(
            SaveScriptPacket::write, SaveScriptPacket::new
    );

    public SaveScriptPacket(FriendlyByteBuf buf) {
        this(buf.readUtf(1024), buf.readUtf(1048576));
    }
    public void write(FriendlyByteBuf buf) {
        buf.writeUtf(this.path, 1024);
        buf.writeUtf(this.content, 1048576);
    }
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}