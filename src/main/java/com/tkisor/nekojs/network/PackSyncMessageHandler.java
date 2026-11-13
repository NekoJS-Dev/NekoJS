//? if neoforge {
package com.tkisor.nekojs.network;

import com.tkisor.nekojs.core.pack.sync.PackSyncClient;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.List;

/**
 * Client-side receive handler for pack distribution payloads (shared by NeoForge 26.x and 1.21.1;
 * the payload classes are copied under the same FQCN on each platform and share the wire format).
 *
 * <p>Configuration-phase timing: NeoForge receives the packet on the network thread, then
 * {@code prepareMainThreadWork} registers a latch, then {@code enqueueWork} switches to the main
 * thread to write files, verify signatures, check trust and run scripts, while the network thread
 * blocks in {@code awaitMainThreadWork} until the main thread finishes. While the network thread is
 * blocked, the connection's later packets (registry sync, finish configuration) do not advance, which
 * guarantees the remote script artifacts are in place when vanilla validates its registry.
 *
 * <p>Address resolution: in the configuration phase {@code Minecraft#getCurrentServer()} relies on
 * the play-phase ClientPacketListener, which does not exist yet, so the hostname comes from the
 * connection's remote socket address. That is the address used for the bucket and printed in the
 * untrusted-disconnect message, so the user can copy it into {@code /nekojs trust}.
 */
public final class PackSyncMessageHandler {

    private PackSyncMessageHandler() {}

    /**
     * S2C configuration-phase hash list. Local/singleplayer memory connections are skipped, because the
     * sync semantics only apply to real remote connections.
     */
    public static void handleHashListOnClient(PackHashListPayload payload, IPayloadContext context) {
        if (context.connection().isMemoryConnection()) return;
        String address = PackSyncClient.normalizeRemoteAddress(context.connection().getRemoteAddress());
        List<PackSyncClient.HashEntry> entries = payload.toClientEntries();
        PackSyncClient.prepareMainThreadWork();
        context.enqueueWork(() -> {
            try {
                PackSyncClient.Outcome outcome = PackSyncClient.handleHashList(
                        PackSyncClientConnections.runtimeRootOrNull(), address, entries);
                if (outcome.shouldDisconnect()) {
                    context.disconnect(Component.literal(outcome.disconnect()));
                }
            } finally {
                PackSyncClient.completeMainThreadWork();
            }
        });
        PackSyncClient.awaitMainThreadWork();
    }

    /**
     * S2C configuration-phase bundle. When the pipeline rejects it (signature failure, integrity failure
     * or untrusted server), it disconnects and shows the reason.
     */
    public static void handleBundleOnClient(PackBundlePayload payload, IPayloadContext context) {
        if (context.connection().isMemoryConnection()) return;
        List<com.tkisor.nekojs.core.pack.sync.SyncedPack> packs = payload.toSyncedPacks();
        PackSyncClient.prepareMainThreadWork();
        context.enqueueWork(() -> {
            try {
                PackSyncClient.Outcome outcome = PackSyncClient.handleBundle(
                        PackSyncClientConnections.runtimeRootOrNull(), packs);
                if (outcome.shouldDisconnect()) {
                    context.disconnect(Component.literal(outcome.disconnect()));
                }
            } finally {
                PackSyncClient.completeMainThreadWork();
            }
        });
        PackSyncClient.awaitMainThreadWork();
    }
}
//?}
