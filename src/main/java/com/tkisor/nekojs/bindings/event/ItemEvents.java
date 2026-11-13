//? if neoforge {
package com.tkisor.nekojs.bindings.event;

import com.tkisor.nekojs.api.event.EventBusForgeBridge;
import com.tkisor.nekojs.api.event.EventBusJS;
import com.tkisor.nekojs.api.event.EventGroup;
import com.tkisor.nekojs.api.event.DispatchKey;
import com.tkisor.nekojs.wrapper.event.server.ItemModificationEventJS;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.item.ItemTossEvent;
import net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent;
import net.neoforged.neoforge.event.entity.player.ItemEntityPickupEvent;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

import java.util.function.Function;

/**
 * Item event group (server and client scripts): right click, tooltip, pickup, drop, interaction
 * and finished use, targeted by item.
 */
public interface ItemEvents {
    EventGroup GROUP = EventGroup.of("ItemEvents");

    EventBusJS<PlayerInteractEvent.RightClickItem, Item> RIGHT_CLICKED =
            GROUP.server("rightClicked", PlayerInteractEvent.RightClickItem.class, dispatchByItem(PlayerInteractEvent::getItemStack));

    /**
     * Runtime item property modification (server scripts, posted-object mode). The platform domain
     * owner posts it at the server startup collection point (about-to-start) and at the DOMAIN_PLAN
     * stage of {@code /nekojs reload server}: events are collected into an inert plan, and at the
     * commit point the platform or version adapter restores the baseline and applies the whole plan
     * (see {@link ItemModificationEventJS} for how events are collected). It is not registered on
     * the NeoForge bus.
     */
    EventBusJS<ItemModificationEventJS, Void> MODIFICATION =
            GROUP.server("modification", ItemModificationEventJS.class);

    EventBusJS<ItemTooltipEvent, Item> TOOLTIP =
            GROUP.client("tooltip", ItemTooltipEvent.class, dispatchByItem(ItemTooltipEvent::getItemStack));

    EventBusJS<ItemEntityPickupEvent.Pre, Item> CAN_PICK_UP =
            GROUP.server("canPickUp", ItemEntityPickupEvent.Pre.class, dispatchByPickupItem());
    // pickedUpPre: a redundant alias for canPickUp (the same Pre event). The cross-platform main
    // names are canPickUp/pickedUp (some loaders have no separate Pre event), so scripts should
    // migrate to canPickUp.
    @Deprecated
    EventBusJS<ItemEntityPickupEvent.Pre, Item> PICKED_UP_PRE =
            GROUP.server("pickedUpPre", ItemEntityPickupEvent.Pre.class, dispatchByPickupItem());
    EventBusJS<ItemEntityPickupEvent.Post, Item> PICKED_UP =
            GROUP.server("pickedUp", ItemEntityPickupEvent.Post.class, dispatchByPickupItem());
    EventBusJS<ItemTossEvent, Item> DROPPED =
            GROUP.server("dropped", ItemTossEvent.class, dispatchByItem(event -> event.getEntity().getItem()));
    EventBusJS<PlayerInteractEvent.EntityInteract, Item> ENTITY_INTERACTED =
            GROUP.server("entityInteracted", PlayerInteractEvent.EntityInteract.class, dispatchByItem(PlayerInteractEvent.EntityInteract::getItemStack));
    // Item use finished (finished eating food, using up a shield or bow, and so on). Matches KubeJS
    // foodEaten and is based on LivingEntityUseItemEvent.Finish. Scripts can tell food apart with
    // event.item.isEdible() and similar checks.
    EventBusJS<LivingEntityUseItemEvent.Finish, Item> FOOD_EATEN =
            GROUP.server("foodEaten", LivingEntityUseItemEvent.Finish.class, dispatchByItem(LivingEntityUseItemEvent::getItem));

    private static <T> DispatchKey<T, Item> dispatchByItem(Function<T, ItemStack> toStack) {
        return DispatchKey.of(Item.class, toStack.andThen(ItemStack::getItem));
    }

    private static <T extends ItemEntityPickupEvent> DispatchKey<T, Item> dispatchByPickupItem() {
        return dispatchByItem(event -> event.getItemEntity().getItem());
    }

    EventBusForgeBridge FORGE_BRIDGE = EventBusForgeBridge.create(NeoForge.EVENT_BUS)
            // RightClickItem fires on both logical sides: the SERVER bus delivers only server instances
            // (client interaction runs on the Render thread).
            .bind(RIGHT_CLICKED, e -> !e.getLevel().isClientSide())
            .bind(TOOLTIP)
            .bind(CAN_PICK_UP)
            .bind(PICKED_UP_PRE)
            .bind(PICKED_UP)
            .bind(DROPPED)
            .bind(ENTITY_INTERACTED, e -> !e.getLevel().isClientSide())
            .bind(FOOD_EATEN);
}
//?}
