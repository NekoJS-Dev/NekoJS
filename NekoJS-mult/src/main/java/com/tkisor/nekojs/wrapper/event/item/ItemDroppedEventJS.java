package com.tkisor.nekojs.wrapper.event.item;

import com.tkisor.nekojs.api.annotation.Doc;
import lombok.Getter;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * 玩家丢弃物品事件（{@code ItemEvents.dropped}）的**加载器中立**载荷。
 *
 * <p>成员对齐 NeoForge 原生 {@code ItemTossEvent} 的可契约 getter（getPlayer / getItem）。
 * fabric 侧由 {@code FabricItemEventBindingsV2} + {@code MixinServerPlayerItemDrop} 从
 * {@code ServerPlayer#drop(ItemStack, boolean, boolean)} 入口转换（Q 丢弃与物品栏
 * 拖拽丢弃共走该入口；死亡掉落的 {@code drop} 调用按
 * {@code isDeadOrDying()} 过滤排除）。
 *
 * <p><b>已知差异</b>：NeoForge 的 {@code ItemTossEvent} 可取消（取消后物品从世界消失、
 * 不再生成 ItemEntity，但仍从物品栏移除）；fabric 侧此总线为**通知型**（挂点在
 * ItemEntity 生成<b>之前</b>，取消只会静默吞掉物品而无法"保留在物品栏"，语义
 * 得不偿失），不支持返回 true 取消。NeoForge 侧的 {@code event.entity}
 * 是丢弃瞬间的 ItemEntity，fabric 侧不存在于事件对象中（payload 无该成员）。
 */
@Doc("Fired when a player tosses an item from their inventory (ItemEvents.dropped). Not cancellable on fabric.")
@Doc("Members: player / item (ItemStack).")
@Getter
public class ItemDroppedEventJS {

    @Doc("The player dropping the item.")
    private final Player player;

    @Doc("The item stack being dropped.")
    private final ItemStack item;

    public ItemDroppedEventJS(Player player, ItemStack item) {
        this.player = player;
        this.item = item;
    }
}
