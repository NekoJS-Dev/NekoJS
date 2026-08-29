package com.tkisor.nekojs.wrapper.event.item;

import com.tkisor.nekojs.api.annotation.Doc;
import lombok.Getter;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * 玩家右键物品事件（{@code ItemEvents.rightClicked}）的加载器中立载荷。
 *
 * <p>NeoForge 侧直传原生 {@code PlayerInteractEvent.RightClickItem}（成员同形：
 * getPlayer/getItemStack/getHand/level）；fabric 侧由 {@code FabricItemEventBindings} 从
 * {@code UseItemCallback} 转换（itemStack 从 {@code player.getItemInHand(hand)} 取）。
 *
 * <p>可取消：监听器返回 {@code true} 时，NeoForge 侧取消原生事件，fabric 侧回调返回
 * {@code CONSUME}（跳过后续处理）。
 */
@Doc("Fired when a player right-clicks while holding an item (ItemEvents.rightClicked).")
@Doc("Return true to consume the interaction.")
@Getter
public class ItemRightClickEventJS {

    @Doc("The clicking player.")
    private final Player player;

    @Doc("The item stack in the used hand.")
    private final ItemStack itemStack;

    @Doc("The hand used (main hand or off hand).")
    private final InteractionHand hand;

    @Doc("The level the interaction happened in.")
    private final Level level;

    public ItemRightClickEventJS(Player player, ItemStack itemStack, InteractionHand hand, Level level) {
        this.player = player;
        this.itemStack = itemStack;
        this.hand = hand;
        this.level = level;
    }
}
