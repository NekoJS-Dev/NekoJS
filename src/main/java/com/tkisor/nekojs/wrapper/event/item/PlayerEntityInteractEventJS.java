package com.tkisor.nekojs.wrapper.event.item;

import com.tkisor.nekojs.api.annotation.Doc;
import lombok.Getter;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * 玩家与实体交互事件（{@code ItemEvents.entityInteracted}）的**加载器中立**载荷。
 *
 * <p>语义对齐 NeoForge {@code PlayerInteractEvent.EntityInteract}：玩家右击实体
 * （不包含攻击——26.1.2 攻击走独立的 {@code ServerboundAttackPacket}）时触发，
 * 按交互时<b>主手物品</b> dispatch。fabric 侧由 {@code FabricItemEventBindingsV2} +
 * {@code MixinServerGamePacketListenerItemInteract} 从
 * {@code ServerGamePacketListenerImpl#handleInteract} 的
 * {@code ServerPlayer.interactOn} 调用点转换（实体存在性/世界边界/交互范围校验均已通过）。
 *
 * <p>取消语义：监听器 {@code return true} = 放弃本次交互（fabric 侧取消整包处理，
 * 不再执行原版交互；对应 NeoForge 的 setCanceled）。
 */
@Doc("Fired when a player interacts (right-click) with an entity (ItemEvents.entityInteracted). Dispatched by the item in the main hand.")
@Doc("Cancellable: return true to cancel the interaction.")
@Getter
public class PlayerEntityInteractEventJS {

    @Doc("The player interacting.")
    private final Player player;

    @Doc("The entity being interacted with.")
    private final Entity target;

    @Doc("The item stack held in the interacting hand.")
    private final ItemStack item;

    @Doc("The interacting hand.")
    private final InteractionHand hand;

    public PlayerEntityInteractEventJS(Player player, Entity target, ItemStack item, InteractionHand hand) {
        this.player = player;
        this.target = target;
        this.item = item;
        this.hand = hand;
    }
}
