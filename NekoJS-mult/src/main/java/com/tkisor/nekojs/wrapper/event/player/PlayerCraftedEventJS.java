package com.tkisor.nekojs.wrapper.event.player;

import com.tkisor.nekojs.api.annotation.Doc;
import lombok.Getter;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;

/**
 * 玩家合成/烧炼产物事件（{@code PlayerEvents.crafted / smelted}，均按物品 id 分发）的
 * 加载器中立载荷。
 *
 * <p>NeoForge 侧对应原生 {@code PlayerEvent.ItemCraftedEvent}（getCrafting/getInventory）
 * 与 {@code PlayerEvent.ItemSmeltedEvent}（getSmelting/getAmountRemoved）；本载荷统一为
 * {@code result}（产出的物品栈，合成=crafting、烧炼=smelting）与可空的 {@code container}
 * （crafted 为合成输入容器 {@code CraftingContainer}，smelted 无对应成员为 null）。
 *
 * <p>注意：NeoForge 26.x 在 {@code ResultSlot#checkTakeAchievements}（crafted）与
 * {@code FurnaceResultSlot#checkTakeAchievements}（smelted）内触发，fabric 侧挂点
 * 与之一致——只覆盖工作台（含手机工作台）与熔炉/高炉/烟熏炉的取件动作。
 */
@Doc("Fired when a player takes a crafted or smelted item (PlayerEvents.crafted / smelted, dispatched by item id).")
@Doc("event.result is the produced stack; event.container (crafted only) is the crafting input container, null for smelted.")
@Getter
public class PlayerCraftedEventJS {

    @Doc("The player who crafted or smelted the item.")
    private final ServerPlayer player;

    @Doc("The produced item stack (crafting output / furnace output).")
    private final ItemStack result;

    @Doc("The crafting input container (crafted only); null for smelted (furnace has no equivalent member).")
    private final Container container;

    public PlayerCraftedEventJS(ServerPlayer player, ItemStack result, Container container) {
        this.player = player;
        this.result = result;
        this.container = container;
    }
}
