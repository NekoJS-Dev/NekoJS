package com.tkisor.nekojs.network;

import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

/**
 * Neutral channel for server-to-client sends during the play phase. Each platform installs it once
 * at init (NeoForge = {@code PacketDistributor}, fabric = {@code ServerPlayNetworking} +
 * {@code PlayerLookup}), and the business code in the version tree ({@code ClientDataSyncJS} /
 * {@code PDataSyncService} and others) depends only on this interface.
 *
 * <p>A failed send must not interrupt script execution: when nothing is installed (early dedicated
 * server / unit tests) or no target is reachable, the send is dropped silently and no exception is
 * thrown. See {@link PlayPacketDispatchers} for installation and lookup.
 */
public interface PlayPacketDispatcher {

    /** Sends to a single player. */
    void sendToPlayer(ServerPlayer player, CustomPacketPayload payload);

    /** Sends to every online player. */
    void sendToAllPlayers(CustomPacketPayload payload);

    /** Sends to the players tracking the entity; when the entity is a player, it is included too. */
    void sendToPlayersTrackingEntityAndSelf(Entity entity, CustomPacketPayload payload);
}
