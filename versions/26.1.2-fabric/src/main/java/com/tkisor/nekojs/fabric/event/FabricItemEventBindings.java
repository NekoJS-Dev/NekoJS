package com.tkisor.nekojs.fabric.event;

import com.tkisor.nekojs.api.ScriptType;
import com.tkisor.nekojs.api.event.DispatchKey;
import com.tkisor.nekojs.api.event.EventBusJS;
import com.tkisor.nekojs.api.event.EventGroup;
import com.tkisor.nekojs.wrapper.event.item.ItemRightClickEventJS;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;

/**
 * 物品事件面的 fabric 桥 v1：rightClicked（按物品 id dispatch，语义对齐 NeoForge
 * {@code PlayerInteractEvent.RightClickItem} 的<b>服务端实例</b>投递）。
 *
 * <p>fabric-api 的 {@code UseItemCallback} 是纯客户端事件（挂在客户端交互链上），不能喂
 * SERVER 总线——服务端等价入口是 {@code ServerGamePacketListener#handleUseItem}
 * （客户端 UseItem 包的服务端处理），由 {@code ServerGamePacketListenerMixin} 钩进来。
 *
 * <p>payload 为中立 {@link ItemRightClickEventJS}（player/itemStack/hand/level，成员名对齐
 * 原生事件 getter）。可取消：监听器返回 {@code true} 时取消本次使用（cancelling the
 * packet handling）。
 */
public final class FabricItemEventBindings {

    /** 与 NeoForge 侧 bindings/event/ItemEvents 同名（fabric 子集）。 */
    public static final EventGroup ITEM_EVENTS = EventGroup.of("ItemEvents");

    private static final EventBusJS<ItemRightClickEventJS, Item> RIGHT_CLICKED =
            ITEM_EVENTS.add("rightClicked", ScriptType.SERVER, EventBusJS.of(
                    ItemRightClickEventJS.class, true,
                    DispatchKey.of(Item.class, event -> event.getItemStack().getItem())));

    private FabricItemEventBindings() {}

    /** ServerGamePacketListenerMixin 钩子入口：服务端处理 UseItem 包时调用。 */
    public static boolean postRightClicked(ServerPlayer player, net.minecraft.world.InteractionHand hand) {
        var stack = player.getItemInHand(hand);
        return RIGHT_CLICKED.post(
                new ItemRightClickEventJS(player, stack, hand, player.level()),
                stack.getItem());
    }
}
