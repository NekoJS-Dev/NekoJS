package com.tkisor.nekojs.wrapper.event.server;

import com.tkisor.nekojs.api.annotation.Doc;
import lombok.Getter;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;

/**
 * Loader-neutral event object for {@code ServerEvents.datapackSync}.
 *
 * <p>NeoForge passes its native {@code OnDatapackSyncEvent} directly and does not use this class.
 * On Fabric, {@code FabricServerEventBindings} adapts {@code ServerLifecycleEvents.SYNC_DATA_PACK_CONTENTS}
 * into this wrapper; {@code player} is {@code null} when syncing to all players after a reload,
 * matching NeoForge's reload behavior.
 *
 * <p>Notification-only and not cancellable. Typically used to send packets or reset client caches
 * after a datapack reload.
 */
@Doc("Fired when datapack-synced content should be (re)sent to players.")
@Doc("player is null when syncing to all players after a reload.")
@Getter
public class DatapackSyncEventJS {

    @Doc("The player being synced to, or null when syncing all players (reload).")
    private final ServerPlayer player;

    @Doc("The server's player list.")
    private final PlayerList playerList;

    public DatapackSyncEventJS(ServerPlayer player, PlayerList playerList) {
        this.player = player;
        this.playerList = playerList;
    }
}
