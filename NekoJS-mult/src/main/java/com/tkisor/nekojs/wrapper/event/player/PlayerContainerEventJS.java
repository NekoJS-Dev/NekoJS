package com.tkisor.nekojs.wrapper.event.player;

import com.tkisor.nekojs.api.annotation.Doc;
import lombok.Getter;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.AbstractContainerMenu;

/**
 * 玩家打开/关闭容器事件（{@code PlayerEvents.containerOpened / containerClosed}，
 * 别名 inventoryOpened/inventoryClosed）的加载器中立载荷。
 *
 * <p>成员对齐 NeoForge 26.x {@code PlayerContainerEvent} 的 getter 形态：
 * {@code event.player}（PlayerEvent.getEntity 层面，载荷统一为 player）、
 * {@code event.container}（getContainer()，打开/关闭的那个菜单）；{@code menu} 是
 * container 的脚本侧别名。
 *
 * <p>crafted/smelted/destroyed 等「物品损坏造成容器关闭」的场景不会触发本事件——
 * 它只对应玩家主动打开（{@code ServerPlayer#openMenu / openHorseInventory /
 * openNautilusInventory}）与主动关闭（{@code ServerPlayer#doCloseContainer}，
 * 含客户端发来的关闭包）。
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
