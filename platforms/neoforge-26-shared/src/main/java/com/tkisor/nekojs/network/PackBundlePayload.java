package com.tkisor.nekojs.network;

import com.tkisor.nekojs.NekoJS;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;

/**
 * S2C 配置阶段包 bundle（{@code nekojs:pack_bundle}）：{@code [(syncId, scope,
 * manifestJson, files[(relativePath, bytes)])]}，紧随哈希清单全量推送（无请求往返）。
 *
 * <p>线格式（与 neoforge-1.21.1 同名包一致）：varint 包数量；每包 UTF(syncId) +
 * UTF(scope) + byteArray(manifest) + varint 文件数 + 每文件 UTF(relPath) + byteArray(bytes)。
 * 解码上限防恶意巨型包（业务侧 {@code PackSyncClient} 另有整体体量校验）。
 */
public record PackBundlePayload(List<PackEntry> packs) implements CustomPacketPayload {

    public record PackEntry(String syncId, String scope, byte[] manifestJson, List<FileEntry> files) {

        public PackEntry {
            files = List.copyOf(files);
        }

        public String manifestJsonText() {
            return new String(manifestJson, java.nio.charset.StandardCharsets.UTF_8);
        }
    }

    public record FileEntry(String relativePath, byte[] bytes) {}

    private static final int MAX_SYNC_ID_LENGTH = 256;
    private static final int MAX_SCOPE_LENGTH = 32;
    private static final int MAX_PATH_LENGTH = 512;
    private static final int MAX_PACKS = 256;
    private static final int MAX_FILES = 4096;
    private static final int MAX_MANIFEST_BYTES = 512 * 1024;
    private static final int MAX_FILE_BYTES = 8 * 1024 * 1024;

    public PackBundlePayload {
        packs = List.copyOf(packs);
    }

    public static final Type<PackBundlePayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(NekoJS.MODID, "pack_bundle"));

    public static final StreamCodec<FriendlyByteBuf, PackBundlePayload> STREAM_CODEC =
            StreamCodec.of(PackBundlePayload::write, PackBundlePayload::read);

    private static PackBundlePayload read(FriendlyByteBuf buf) {
        int packCount = buf.readVarInt();
        if (packCount > MAX_PACKS) {
            throw new IllegalArgumentException("Too many packs in bundle: " + packCount);
        }
        List<PackEntry> packs = new ArrayList<>(packCount);
        for (int i = 0; i < packCount; i++) {
            String syncId = buf.readUtf(MAX_SYNC_ID_LENGTH);
            String scope = buf.readUtf(MAX_SCOPE_LENGTH);
            byte[] manifest = buf.readByteArray(MAX_MANIFEST_BYTES);
            int fileCount = buf.readVarInt();
            if (fileCount > MAX_FILES) {
                throw new IllegalArgumentException("Too many files in pack " + syncId + ": " + fileCount);
            }
            List<FileEntry> files = new ArrayList<>(fileCount);
            for (int f = 0; f < fileCount; f++) {
                files.add(new FileEntry(buf.readUtf(MAX_PATH_LENGTH), buf.readByteArray(MAX_FILE_BYTES)));
            }
            packs.add(new PackEntry(syncId, scope, manifest, files));
        }
        return new PackBundlePayload(packs);
    }

    private static void write(FriendlyByteBuf buf, PackBundlePayload payload) {
        buf.writeVarInt(payload.packs.size());
        for (PackEntry pack : payload.packs) {
            buf.writeUtf(pack.syncId(), MAX_SYNC_ID_LENGTH);
            buf.writeUtf(pack.scope(), MAX_SCOPE_LENGTH);
            buf.writeByteArray(pack.manifestJson());
            buf.writeVarInt(pack.files().size());
            for (FileEntry file : pack.files()) {
                buf.writeUtf(file.relativePath(), MAX_PATH_LENGTH);
                buf.writeByteArray(file.bytes());
            }
        }
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
