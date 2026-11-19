package com.tkisor.nekojs.fabric.event;

import com.tkisor.nekojs.api.event.StableClientEvent;
import com.tkisor.nekojs.api.event.StableClientInputEvent;
import com.tkisor.nekojs.api.event.StableCommandRegistrationEvent;
import com.tkisor.nekojs.api.event.StablePlayerEvent;
import com.tkisor.nekojs.wrapper.event.client.ClientCommandRegistryEventJS;
import com.tkisor.nekojs.wrapper.event.client.ClientPlayerNetworkEventJS;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;

/**
 * Client-side projections from Fabric client events and wrappers to portable stable event snapshots.
 */
public final class FabricClientStableEventProjections {
    private FabricClientStableEventProjections() {}

    /** Projects a named client lifecycle or tick stage into a {@link StableClientEvent}. */
    public static StableClientEvent stage(String stage) {
        return new StableClientEvent(stage);
    }

    /** Projects the local client {@link Player} into a {@link StablePlayerEvent} snapshot. */
    public static StablePlayerEvent clientPlayer(Player player) {
        if (player == null) {
            return StablePlayerEvent.forClient("", "", "", 0.0, 0.0, 0.0);
        }
        return StablePlayerEvent.forClient(
                player.getScoreboardName(),
                player.getStringUUID(),
                FabricStableEventProjections.dimensionOf(player.level()),
                player.getX(),
                player.getY(),
                player.getZ());
    }

    /** Projects a {@link ClientPlayerNetworkEventJS} login/logout wrapper into a {@link StablePlayerEvent}. */
    public static StablePlayerEvent clientNetwork(ClientPlayerNetworkEventJS event) {
        return clientPlayer(event == null ? null : event.getPlayer());
    }

    /** Projects a {@link ClientCommandRegistryEventJS} wrapper into a {@link StableCommandRegistrationEvent}. */
    public static StableCommandRegistrationEvent clientCommandRegistry(ClientCommandRegistryEventJS event) {
        return new StableCommandRegistrationEvent("CLIENT");
    }

    /** Projects client interaction key input flags and hand into a {@link StableClientInputEvent}. */
    public static StableClientInputEvent clientInteractionKey(
            boolean attack, boolean useItem, boolean pickBlock, InteractionHand hand) {
        return new StableClientInputEvent(attack, useItem, pickBlock, hand == null ? "" : hand.name());
    }
}
