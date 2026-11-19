package com.tkisor.nekojs.wrapper.event.client;

import com.tkisor.nekojs.api.annotation.Doc;
import com.tkisor.nekojs.api.annotation.Param;
import com.tkisor.nekojs.api.annotation.Return;
import net.minecraft.world.entity.player.Player;

/**
 * Loader-neutral payload for client player connection events ({@code ClientEvents.loggedIn} and
 * {@code ClientEvents.loggedOut}).
 *
 * <p>Uses only the common {@link Player} class so it remains safe to load across all loaders and
 * runtime environments.
 */
@Doc("Fired on the client when the local player logs in to or logs out from a world or server (ClientEvents.loggedIn / ClientEvents.loggedOut).")
public class ClientPlayerNetworkEventJS {

    @Doc("The local client player associated with this connection event, or null when unavailable on disconnect.")
    private final Player player;

    /**
     * Creates a client player network event payload.
     *
     * @param player the local client player associated with this connection transition, or {@code null}
     */
    @Doc("Creates a client player network event payload.")
    @Param(name = "player", value = "the local client player associated with this connection transition, or null")
    public ClientPlayerNetworkEventJS(Player player) {
        this.player = player;
    }

    /**
     * Returns the local client player associated with this event.
     *
     * @return the local client player, or {@code null} when unavailable on disconnect
     */
    @Doc("Gets the local client player associated with this connection event.")
    @Return("the local client player, or null when unavailable on disconnect")
    public Player getPlayer() {
        return player;
    }
}
