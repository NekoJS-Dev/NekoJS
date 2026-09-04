package com.tkisor.nekojs.fabric.event;

import com.tkisor.nekojs.bindings.event.ItemEvents;
import com.tkisor.nekojs.wrapper.event.item.ItemDroppedEventJS;
import com.tkisor.nekojs.wrapper.event.item.ItemEntityPickupEventJS;
import com.tkisor.nekojs.wrapper.event.item.ItemUseFinishedEventJS;
import com.tkisor.nekojs.wrapper.event.item.PlayerEntityInteractEventJS;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * 物品事件面的 fabric 桥 v2：canPickUp / pickedUpPre / pickedUp / dropped / foodEaten /
 * entityInteracted——总线定义在节点孪生 {@link ItemEvents}（组 "ItemEvents" 与 v1 的
 * rightClicked、client 的 tooltip 同名合并），本类只提供各 mixin 调用的投递入口。
 *
 * <p>均挂 mixin（fabric-api 无对应现成回调，见各类 mixin 的 javap 实证注释）：
 * <ul>
 *   <li>{@code MixinItemEntityPickup}（ItemEntity#playerTouch）→ 拾取 Pre/Post</li>
 *   <li>{@code MixinServerPlayerItemDrop}（ServerPlayer#drop(ItemStack,boolean,boolean)）→ dropped</li>
 *   <li>{@code MixinLivingEntityUseItemFinish}（completeUsingItem 的 INVOKE 点）→ foodEaten</li>
 *   <li>{@code MixinServerGamePacketListenerItemInteract}（handleInteract 的 INVOKE 点）→ entityInteracted</li>
 * </ul>
 */
public final class FabricItemEventBindingsV2 {

    private FabricItemEventBindingsV2() {}

    /**
     * 拾取 Pre：{@code MixinItemEntityPickup} 在 {@code playerTouch} 入口调用。
     *
     * @return true 表示某监听器取消了拾取（mixin 应 {@code ci.cancel()} 跳过整个拾取流程）
     */
    public static boolean postCanPickUp(Player player, ItemEntity itemEntity) {
        boolean anyListeners = ItemEvents.CAN_PICK_UP.hasListeners()
                || ItemEvents.PICKED_UP_PRE.hasListeners();
        if (!anyListeners) {
            return false;
        }
        ItemEntityPickupEventJS payload = new ItemEntityPickupEventJS(player, itemEntity);
        Item item = payload.getItem().getItem();
        boolean cancelled = ItemEvents.CAN_PICK_UP.post(payload, item);
        if (!cancelled) {
            cancelled = ItemEvents.PICKED_UP_PRE.post(payload, item);
        }
        return cancelled;
    }

    /** 拾取 Post：{@code MixinItemEntityPickup} 在 {@code playerTouch} 的 {@code onItemPickup} 调用点触发。 */
    public static void postPickedUp(Player player, ItemEntity itemEntity) {
        if (!ItemEvents.PICKED_UP.hasListeners()) {
            return;
        }
        ItemEntityPickupEventJS payload = new ItemEntityPickupEventJS(player, itemEntity);
        ItemEvents.PICKED_UP.post(payload, payload.getItem().getItem());
    }

    /** dropped：{@code MixinServerPlayerItemDrop} 在 {@code ServerPlayer#drop(ItemStack,boolean,boolean)} 入口调用。 */
    public static void postDropped(Player player, ItemStack stack) {
        if (!ItemEvents.DROPPED.hasListeners() || stack.isEmpty()) {
            return;
        }
        ItemEvents.DROPPED.post(new ItemDroppedEventJS(player, stack), stack.getItem());
    }

    /** foodEaten：{@code MixinLivingEntityUseItemFinish} 在物品使用完成时调用。 */
    public static void postFoodEaten(LivingEntity entity, ItemStack stack, InteractionHand hand) {
        if (!ItemEvents.FOOD_EATEN.hasListeners() || stack.isEmpty()) {
            return;
        }
        ItemEvents.FOOD_EATEN.post(new ItemUseFinishedEventJS(entity, stack, hand), stack.getItem());
    }

    /**
     * entityInteracted：{@code MixinServerGamePacketListenerItemInteract} 在
     * {@code handleInteract} 的 {@code interactOn} 调用点触发。
     *
     * @return true 表示监听器取消了交互（mixin 应 {@code ci.cancel()} 跳过原版交互）
     */
    public static boolean postEntityInteracted(ServerPlayer player, Entity target, ItemStack stack, InteractionHand hand) {
        if (!ItemEvents.ENTITY_INTERACTED.hasListeners()) {
            return false;
        }
        return ItemEvents.ENTITY_INTERACTED.post(
                new PlayerEntityInteractEventJS(player, target, stack, hand),
                stack.getItem());
    }
}
