package com.tkisor.nekojs.network;

import com.tkisor.nekojs.NekoJS;
import com.tkisor.nekojs.core.pack.sync.PackContentFile;
import com.tkisor.nekojs.core.pack.sync.PackSyncServer;
import com.tkisor.nekojs.core.pack.sync.SyncedPack;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.PlayerEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * Server-side push for 1.12.2 (degraded mode): there is no configuration
 * phase, so hashes + bundle are sent on the play channel right after login
 * ({@code PlayerLoggedInEvent}, i.e. after the FML handshake completed and the
 * player entity exists). Local (integrated/LAN-memory) connections are skipped
 * — pack sync targets real remote clients only.
 */
public final class PackSyncServerPusher {

    private PackSyncServerPusher() {}

    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (!PackSyncServer.enabled()) return;
        if (!(event.player instanceof EntityPlayerMP player)) return;
        if (player.connection == null || player.connection.netManager.isLocalChannel()) return;

        try {
            List<SyncedPack> packs = PackSyncServer.collectSyncPacks();
            List<PackHashListPacket.Entry> hashes = new ArrayList<>();
            for (SyncedPack pack : packs) {
                hashes.add(new PackHashListPacket.Entry(pack.syncId(), pack.hash()));
            }
            NekoJSNetwork.CHANNEL.sendTo(new PackHashListPacket(hashes), player);
            if (!PackSyncServer.hashOnly() && !hashes.isEmpty()) {
                NekoJSNetwork.CHANNEL.sendTo(toBundlePacket(packs), player);
            }
        } catch (Exception e) {
            NekoJS.LOGGER.error("Failed to push script pack sync to {}", player.getName(), e);
        }
    }

    private static PackBundlePacket toBundlePacket(List<SyncedPack> packs) {
        List<PackBundlePacket.Pack> entries = new ArrayList<>();
        for (SyncedPack pack : packs) {
            List<PackBundlePacket.FileEntry> files = new ArrayList<>();
            for (PackContentFile file : pack.files()) {
                files.add(new PackBundlePacket.FileEntry(file.relativePath(), file.bytes()));
            }
            entries.add(new PackBundlePacket.Pack(pack.syncId(), pack.scopeName(), pack.manifestJson(), files));
        }
        return new PackBundlePacket(entries);
    }
}
