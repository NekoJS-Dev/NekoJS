package com.tkisor.nekojs.network;

import com.tkisor.nekojs.NekoJS;
import com.tkisor.nekojs.core.pack.sync.PackSyncClient;
import com.tkisor.nekojs.core.pack.sync.SyncedPack;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import java.util.ArrayList;
import java.util.List;

/**
 * S2C configuration-phase pack hash list ({@code nekojs:pack_hashes}): {@code [(syncId, hash)]}.
 * An empty list is valid too: the client uses it to clear the remote packs and reload client scripts.
 *
 * <p>Wire format (same as the packet of the same name in neoforge-1.21.1): varint count + per entry
 * UTF(syncId) + UTF(hash).
 */
public record PackHashListPayload(List<HashEntry> entries) implements CustomPacketPayload {

    public record HashEntry(String syncId, String hash) {}

    private static final int MAX_SYNC_ID_LENGTH = 256;
    private static final int MAX_HASH_LENGTH = 128;
    private static final int MAX_ENTRIES = 256;

    public PackHashListPayload {
        entries = List.copyOf(entries);
    }

    /** Shared by every platform's push side: a list of pack snapshots becomes the wire hash list. */
    public static PackHashListPayload of(List<SyncedPack> packs) {
        List<HashEntry> entries = new ArrayList<>();
        for (SyncedPack pack : packs) {
            entries.add(new HashEntry(pack.syncId(), pack.hash()));
        }
        return new PackHashListPayload(entries);
    }

    /** Shared by every platform's receive side: wire entries become client pipeline entries. */
    public List<PackSyncClient.HashEntry> toClientEntries() {
        List<PackSyncClient.HashEntry> out = new ArrayList<>(entries.size());
        for (HashEntry entry : entries) {
            out.add(new PackSyncClient.HashEntry(entry.syncId(), entry.hash()));
        }
        return out;
    }

    public static final Type<PackHashListPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(NekoJS.MODID, "pack_hashes"));

    public static final StreamCodec<FriendlyByteBuf, PackHashListPayload> STREAM_CODEC =
            StreamCodec.of(PackHashListPayload::write, PackHashListPayload::read);

    private static PackHashListPayload read(FriendlyByteBuf buf) {
        int count = buf.readVarInt();
        if (count > MAX_ENTRIES) {
            throw new IllegalArgumentException("Too many pack hash entries: " + count);
        }
        List<HashEntry> entries = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            entries.add(new HashEntry(buf.readUtf(MAX_SYNC_ID_LENGTH), buf.readUtf(MAX_HASH_LENGTH)));
        }
        return new PackHashListPayload(entries);
    }

    private static void write(FriendlyByteBuf buf, PackHashListPayload payload) {
        buf.writeVarInt(payload.entries.size());
        for (HashEntry entry : payload.entries) {
            buf.writeUtf(entry.syncId(), MAX_SYNC_ID_LENGTH);
            buf.writeUtf(entry.hash(), MAX_HASH_LENGTH);
        }
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
