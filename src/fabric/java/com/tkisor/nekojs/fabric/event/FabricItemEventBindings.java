package com.tkisor.nekojs.fabric.event;

import com.tkisor.nekojs.api.ScriptType;
import com.tkisor.nekojs.api.event.DispatchKey;
import com.tkisor.nekojs.api.event.EventBusJS;
import com.tkisor.nekojs.api.event.EventGroup;
import com.tkisor.nekojs.api.event.StableItemEvent;
import com.tkisor.nekojs.bindings.event.ServerLevelEventThread;
import com.tkisor.nekojs.wrapper.event.item.ItemRightClickEventJS;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * Fabric bridge v1 for item events: rightClicked, dispatched by item id. Its semantics match the
 * <b>server-side</b> instance of {@code PlayerInteractEvent.RightClickItem} on NeoForge.
 *
 * <p>fabric-api's {@code UseItemCallback} is a client-only event on the client interaction chain,
 * so it cannot feed the SERVER bus. The server equivalent is
 * {@code ServerGamePacketListener#handleUseItem}, which handles the client's UseItem packet;
 * {@code ServerGamePacketListenerMixin} hooks it in.
 *
 * <p>The payload is the neutral {@link ItemRightClickEventJS} (player/itemStack/hand/level, with
 * member names matching the vanilla event getters). Cancellable: a listener returning
 * {@code true} cancels this use, that is, the packet handling.
 */
public final class FabricItemEventBindings {

    /** Same name as bindings/event/ItemEvents on the NeoForge side (a subset on fabric). */
    public static final EventGroup ITEM_EVENTS = EventGroup.of("ItemEvents");

    private static final EventBusJS<ItemRightClickEventJS, Item> RIGHT_CLICKED =
            ITEM_EVENTS.add("rightClicked", ScriptType.SERVER, EventBusJS.of(
                    ItemRightClickEventJS.class, true,
                    DispatchKey.of(Item.class, event -> event.getItemStack().getItem())), api -> {
                api.versioned().when(event -> !(event instanceof EligibleItemUse));
                api.stable()
                        .when(event -> event instanceof EligibleItemUse)
                        .view(StableItemEvent.class, FabricStableEventProjections::itemRightClick)
                        .selector(String.class, FabricStableEventProjections::resolveItemSelector)
                        .cancellable()
                        .scriptTypes(ScriptType.SERVER);
            });

    private FabricItemEventBindings() {}

    /** Hook entry for {@code ServerGamePacketListenerMixin}: called when the server handles a UseItem packet. */
    public static boolean postRightClicked(ServerPlayer player, net.minecraft.world.InteractionHand hand) {
        if (!RIGHT_CLICKED.hasListeners() || !ServerLevelEventThread.isCurrent(player.level())) {
            return false;
        }
        var stack = player.getItemInHand(hand);
        return RIGHT_CLICKED.post(
                new ItemRightClickEventJS(player, stack, hand, player.level()),
                stack.getItem());
    }

    public static boolean postEligibleRightClick(ServerPlayer player, InteractionHand hand) {
        if (!RIGHT_CLICKED.hasStableModuleListeners(ScriptType.SERVER) || !ServerLevelEventThread.isCurrent(player.level())) {
            return false;
        }
        var stack = player.getItemInHand(hand);
        return RIGHT_CLICKED.postStableModuleCancellable(
                new EligibleItemUse(player, stack, hand), stack.getItem(), ScriptType.SERVER);
    }

    private static final class EligibleItemUse extends ItemRightClickEventJS {
        private EligibleItemUse(ServerPlayer player, ItemStack stack, InteractionHand hand) {
            super(player, stack, hand, player.level());
        }
    }
}
