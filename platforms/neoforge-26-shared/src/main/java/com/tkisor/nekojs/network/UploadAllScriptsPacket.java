package com.tkisor.nekojs.network;

import com.tkisor.nekojs.NekoJS;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

public record UploadAllScriptsPacket(Map<String, String> files) implements CustomPacketPayload {
    public static final Type<UploadAllScriptsPacket> TYPE = new Type<>(Identifier.fromNamespaceAndPath(NekoJS.MODID, "upload_all_scripts"));

    public static final StreamCodec<FriendlyByteBuf, UploadAllScriptsPacket> STREAM_CODEC = StreamCodec.ofMember(
            UploadAllScriptsPacket::write, UploadAllScriptsPacket::new
    );

    public UploadAllScriptsPacket(FriendlyByteBuf buf) {
        this(readFiles(buf));
    }

    private static Map<String, String> readFiles(FriendlyByteBuf buf) {
        int count = buf.readVarInt();
        if (count < 0 || count > ScriptSyncService.MAX_SYNC_FILES) {
            throw new IllegalStateException("Invalid script batch entry count: " + count + " (max " + ScriptSyncService.MAX_SYNC_FILES + ")");
        }
        Map<String, String> files = new HashMap<>(Math.max(16, count * 2));
        long totalSize = 0L;
        for (int i = 0; i < count; i++) {
            String path = buf.readUtf(1024);
            String content = buf.readUtf(ScriptSyncService.MAX_BATCH_SCRIPT_SIZE);
            int size = content.getBytes(StandardCharsets.UTF_8).length;
            if (size > ScriptSyncService.MAX_BATCH_SCRIPT_SIZE) {
                throw new IllegalStateException("Script content too large: " + path + " (" + size + " bytes, max " + ScriptSyncService.MAX_BATCH_SCRIPT_SIZE + ")");
            }
            totalSize += size;
            if (totalSize > ScriptSyncService.MAX_BATCH_TOTAL_SIZE) {
                throw new IllegalStateException("Script batch total size too large (max " + ScriptSyncService.MAX_BATCH_TOTAL_SIZE + " bytes)");
            }
            files.put(path, content);
        }
        return files;
    }

    public void write(FriendlyByteBuf buf) {
        if (this.files.size() > ScriptSyncService.MAX_SYNC_FILES) {
            throw new IllegalArgumentException("Script batch entry count exceeds limit: " + this.files.size() + " (max " + ScriptSyncService.MAX_SYNC_FILES + ")");
        }
        long totalSize = 0L;
        for (Map.Entry<String, String> entry : this.files.entrySet()) {
            int size = entry.getValue().getBytes(StandardCharsets.UTF_8).length;
            if (size > ScriptSyncService.MAX_BATCH_SCRIPT_SIZE) {
                throw new IllegalArgumentException("Script content too large: " + entry.getKey() + " (" + size + " bytes, max " + ScriptSyncService.MAX_BATCH_SCRIPT_SIZE + ")");
            }
            totalSize += size;
            if (totalSize > ScriptSyncService.MAX_BATCH_TOTAL_SIZE) {
                throw new IllegalArgumentException("Script batch total size too large: " + totalSize + " bytes (max " + ScriptSyncService.MAX_BATCH_TOTAL_SIZE + ")");
            }
        }
        buf.writeVarInt(this.files.size());
        for (Map.Entry<String, String> entry : this.files.entrySet()) {
            buf.writeUtf(entry.getKey(), 1024);
            buf.writeUtf(entry.getValue(), ScriptSyncService.MAX_BATCH_SCRIPT_SIZE);
        }
    }

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}