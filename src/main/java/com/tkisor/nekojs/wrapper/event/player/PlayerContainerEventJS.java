package com.tkisor.nekojs.wrapper.event.player;

import com.tkisor.nekojs.api.annotation.Doc;
import lombok.Getter;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.AbstractContainerMenu;

/**
 * Loader-neutral payload for the player container open/close events
 * ({@code PlayerEvents.containerOpened / containerClosed}, aliases inventoryOpened /
 * inventoryClosed).
 *
 * <p>The members follow the getters of NeoForge 26.x {@code PlayerContainerEvent}:
 * {@code event.player} (the PlayerEvent.getEntity level; the payload always uses player) and
 * {@code event.container} (getContainer(), the menu being opened or closed); {@code menu} is
 * the script-side alias of container.
 *
 * <p>Cases where item damage closes the container (crafted/smelted/destroyed and similar) do
 * not fire this event. It only covers a player opening a menu
 * ({@code ServerPlayer#openMenu / openHorseInventory /
 * openNautilusInventory}) or closing one ({@code ServerPlayer#doCloseContainer}, including the
 * close packet sent by the client).
 */
@Doc("Fired when a player opens or closes a container menu (PlayerEvents.containerOpened / containerClosed).")
@Doc("containerOpened also exists under the deprecated alias inventoryOpened; containerClosed under inventoryClosed.")
@Getter
public class PlayerContainerEventJS {

    @Doc("The player opening/closing the container.")
    private final ServerPlayer player;

    @Doc("The container menu being opened or closed (AbstractContainerMenu).")
    private final AbstractContainerMenu container;

    public PlayerContainerEventJS(ServerPlayer player, AbstractContainerMenu container) {
        this.player = player;
        this.container = container;
    }

    @Doc("Alias of container (the menu being opened/closed).")
    public AbstractContainerMenu getMenu() {
        return container;
    }
}
