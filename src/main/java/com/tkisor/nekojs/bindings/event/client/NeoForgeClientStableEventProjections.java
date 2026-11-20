//~ mc_legacy_api
//? if neoforge {
package com.tkisor.nekojs.bindings.event.client;

import com.tkisor.nekojs.api.event.StableClientEvent;
import com.tkisor.nekojs.api.event.StableClientInputEvent;
import com.tkisor.nekojs.api.event.StableCommandRegistrationEvent;
import com.tkisor.nekojs.api.event.StablePlayerEvent;
import com.tkisor.nekojs.bindings.event.NeoForgeStableEventProjections;
import com.tkisor.nekojs.wrapper.entity.StableEntities;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;

/**
 * Client-only NeoForge projections for portable event snapshots and live player roles.
 */
public final class NeoForgeClientStableEventProjections {
    private NeoForgeClientStableEventProjections() {}

    public static StableClientEvent stage(String stage) {
        return new StableClientEvent(stage);
    }

    public static StablePlayerEvent clientPlayer(Player player) {
        return clientPlayer(player, null);
    }

    private static StablePlayerEvent clientPlayer(Player player, Player originalPlayer) {
        var original = StableEntities.wrap(originalPlayer == player ? null : originalPlayer);
        if (player == null) {
            return StablePlayerEvent.forClient("", "", "", 0.0, 0.0, 0.0, null, original, null);
        }
        return StablePlayerEvent.forClient(
                player.getScoreboardName(),
                player.getStringUUID(),
                NeoForgeStableEventProjections.dimensionOf(player.level()),
                player.getX(),
                player.getY(),
                player.getZ(),
                StableEntities.wrap(player),
                original,
                null);
    }

    public static StablePlayerEvent clientLogin(ClientPlayerNetworkEvent.LoggingIn event) {
        return clientPlayer(event.getPlayer());
    }

    public static StablePlayerEvent clientLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        return clientPlayer(event.getPlayer());
    }

    public static StablePlayerEvent clientClone(ClientPlayerNetworkEvent.Clone event) {
        return clientPlayer(event.getNewPlayer(), event.getOldPlayer());
    }

    public static StableCommandRegistrationEvent clientCommandRegistry(RegisterClientCommandsEvent event) {
        return new StableCommandRegistrationEvent("CLIENT");
    }

    public static StableClientInputEvent clientInteractionKey(InputEvent.InteractionKeyMappingTriggered event) {
        Minecraft client = Minecraft.getInstance();
        return new StableClientInputEvent(
                event.isAttack(),
                event.isUseItem(),
                event.isPickBlock(),
                event.getHand() == null ? "" : event.getHand().name(),
                StableEntities.wrap(client == null ? null : client.player));
    }
}
//?}
