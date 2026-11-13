package com.tkisor.nekojs.network;

import com.tkisor.nekojs.NekoJS;
import com.tkisor.nekojs.core.pack.sync.PackContentFile;
import com.tkisor.nekojs.core.pack.sync.SyncedPack;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import java.util.ArrayList;
import java.util.List;

/**
 * S2C configuration-phase pack bundle ({@code nekojs:pack_bundle}): {@code [(syncId, scope,
 * manifestJson, files[(relativePath, bytes)])]}, pushed in full right after the hash list, with no
 * request round trip.
 *
 * <p>Wire format (same as the packet of the same name in neoforge-1.21.1): varint pack count; per
 * pack UTF(syncId) + UTF(scope) + byteArray(manifest) + varint file count + per file UTF(relPath) +
 * byteArray(bytes). The decode limits defend against maliciously huge packets; {@code PackSyncClient}
 * checks the overall size separately on the business side.
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

    /** Conversion shared by every platform's push side: a list of {@link SyncedPack} becomes the wire bundle. */
    public static PackBundlePayload of(List<SyncedPack> packs) {
        List<PackEntry> entries = new ArrayList<>();
        for (SyncedPack pack : packs) {
            List<FileEntry> files = new ArrayList<>();
            for (PackContentFile file : pack.files()) {
                files.add(new FileEntry(file.relativePath(), file.bytes()));
            }
            entries.add(new PackEntry(
                pack.syncId(),
                pack.scopeName(),
                pack.manifestJson().getBytes(java.nio.charset.StandardCharsets.UTF_8),
                files));
        }
        return new PackBundlePayload(entries);
    }

    /**
     * Conversion shared by every platform's receive side: the wire bundle becomes the client pipeline's
     * pack snapshot. The pipeline computes and compares the hash.
     */
    public List<SyncedPack> toSyncedPacks() {
        List<SyncedPack> out = new ArrayList<>(packs.size());
        for (PackEntry pack : packs) {
            List<PackContentFile> files = new ArrayList<>(pack.files().size());
            for (FileEntry file : pack.files()) {
                files.add(new PackContentFile(file.relativePath(), file.bytes()));
            }
            out.add(SyncedPack.of(pack.syncId(), pack.scope(), null, pack.manifestJsonText(), files));
        }
        return out;
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
