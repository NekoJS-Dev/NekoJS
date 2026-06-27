package com.tkisor.nekojs.network;

import com.tkisor.nekojs.NekoJS;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import java.util.Map;

public record UploadAllScriptsPacket(Map<String, String> files) implements CustomPacketPayload {
    public static final Type<UploadAllScriptsPacket> TYPE = new Type<>(Identifier.fromNamespaceAndPath(NekoJS.MODID, "upload_all_scripts"));
    private static final int MAX_FILE_SIZE = 8388608;

    public static final StreamCodec<FriendlyByteBuf, UploadAllScriptsPacket> STREAM_CODEC = StreamCodec.ofMember(
            UploadAllScriptsPacket::write, UploadAllScriptsPacket::new
    );

    public UploadAllScriptsPacket(FriendlyByteBuf buf) {
        this(buf.readMap(b -> b.readUtf(1024), b -> b.readUtf(MAX_FILE_SIZE)));
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeMap(this.files, (b, path) -> b.writeUtf(path, 1024), (b, content) -> b.writeUtf(content, MAX_FILE_SIZE));
    }

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}