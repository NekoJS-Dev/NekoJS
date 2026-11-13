package com.tkisor.nekojs.fabric.event;

import com.tkisor.nekojs.api.ScriptType;
import com.tkisor.nekojs.api.event.DispatchKey;
import com.tkisor.nekojs.api.event.EventBusJS;
import com.tkisor.nekojs.api.event.EventGroup;
import com.tkisor.nekojs.wrapper.event.item.ItemRightClickEventJS;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;

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
                    DispatchKey.of(Item.class, event -> event.getItemStack().getItem())));

    private FabricItemEventBindings() {}

    /** Hook entry for {@code ServerGamePacketListenerMixin}: called when the server handles a UseItem packet. */
    public static boolean postRightClicked(ServerPlayer player, net.minecraft.world.InteractionHand hand) {
        var stack = player.getItemInHand(hand);
        return RIGHT_CLICKED.post(
                new ItemRightClickEventJS(player, stack, hand, player.level()),
                stack.getItem());
    }
}
