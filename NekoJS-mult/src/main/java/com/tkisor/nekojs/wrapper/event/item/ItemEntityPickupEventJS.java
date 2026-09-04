package com.tkisor.nekojs.wrapper.event.item;

import com.tkisor.nekojs.api.annotation.Doc;
import lombok.Getter;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * 物品实体拾取事件（{@code ItemEvents.canPickUp / pickedUpPre / pickedUp}）的**加载器中立**载荷。
 *
 * <p>成员名对齐 NeoForge 原生 {@code ItemEntityPickupEvent} 的 getter
 * （getPlayer / getItemEntity / getItem）。fabric 侧由
 * {@code FabricItemEventBindingsV2} + {@code MixinItemEntityPickup} 从
 * {@code ItemEntity#playerTouch} 转换（Pre 在方法入口、Post 在拾取成功后）。
 *
 * <p>取消语义：{@code canPickUp} / {@code pickedUpPre}（别名）可取消——监听器
 * {@code return true} 阻止本次拾取（对应 NeoForge Pre 的取消，fabric 侧等价于
 * 跳过整个 {@code playerTouch} 流程）；{@code pickedUp} 为成功后通知，不可取消。
 */
@Doc("Fired when a player is about to pick up / has picked up an item entity (ItemEvents.canPickUp / pickedUpPre / pickedUp).")
@Doc("canPickUp / pickedUpPre are cancellable: return true to prevent the pickup. pickedUp is not cancellable.")
@Getter
public class ItemEntityPickupEventJS {

    @Doc("The player touching the item.")
    private final Player player;

    @Doc("The item entity that is being / was picked up.")
    private final ItemEntity itemEntity;

    public ItemEntityPickupEventJS(Player player, ItemEntity itemEntity) {
        this.player = player;
        this.itemEntity = itemEntity;
    }

    @Doc("The item stack on the item entity.")
    public ItemStack getItem() {
        return itemEntity.getItem();
    }
}
