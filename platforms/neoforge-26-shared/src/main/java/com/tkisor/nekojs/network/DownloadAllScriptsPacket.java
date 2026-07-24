package com.tkisor.nekojs.network;

import com.tkisor.nekojs.NekoJS;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import java.util.Map;

public record DownloadAllScriptsPacket(Map<String, String> files) implements CustomPacketPayload {
    public static final Type<DownloadAllScriptsPacket> TYPE = new Type<>(Identifier.fromNamespaceAndPath(NekoJS.MODID, "download_all_scripts"));
    private static final int MAX_FILE_SIZE = 8388608;

    public static final StreamCodec<FriendlyByteBuf, DownloadAllScriptsPacket> STREAM_CODEC = StreamCodec.ofMember(
            DownloadAllScriptsPacket::write, DownloadAllScriptsPacket::new
    );

    public DownloadAllScriptsPacket(FriendlyByteBuf buf) {
        this(buf.readMap(b -> b.readUtf(1024), b -> b.readUtf(MAX_FILE_SIZE)));
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeMap(this.files, (b, path) -> b.writeUtf(path, 1024), (b, content) -> b.writeUtf(content, MAX_FILE_SIZE));
    }

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}