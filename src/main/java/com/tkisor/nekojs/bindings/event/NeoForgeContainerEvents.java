package com.tkisor.nekojs.bindings.event;

import com.tkisor.nekojs.stage.StageGateEnforcer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.neoforged.neoforge.event.entity.player.PlayerContainerEvent;

public final class NeoForgeContainerEvents {
    private static final ThreadLocal<ServerPlayer> DENIED_OPEN = new ThreadLocal<>();

    private NeoForgeContainerEvents() {}

    public static void closeDeniedOpen(ServerPlayer player) {
        var previous = DENIED_OPEN.get();
        DENIED_OPEN.set(player);
        try {
            player.closeContainer();
        } finally {
            if (previous == null) DENIED_OPEN.remove();
            else DENIED_OPEN.set(previous);
        }
    }

    static boolean isStableClose(PlayerContainerEvent.Close event) {
        return event.getEntity() instanceof ServerPlayer player && DENIED_OPEN.get() != player;
    }

    public static void openedNautilus(ServerPlayer player) {
        AbstractContainerMenu menu = player.containerMenu;
        if (menu == player.inventoryMenu) return;
        if (!StageGateEnforcer.allowOpenContainer(player, menu)) {
            closeDeniedOpen(player);
            return;
        }
        if (PlayerEvents.CONTAINER_OPENED.hasListeners()) {
            PlayerEvents.CONTAINER_OPENED.post(new NautilusOpen(player, menu));
        }
    }

    static final class NautilusOpen extends PlayerContainerEvent.Open {
        private NautilusOpen(ServerPlayer player, AbstractContainerMenu menu) {
            super(player, menu);
        }
    }
}
