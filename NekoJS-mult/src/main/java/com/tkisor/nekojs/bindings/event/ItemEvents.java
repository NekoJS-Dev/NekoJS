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

/** 物品事件组（server/client 脚本）：右键、tooltip、拾取、丢弃、交互与使用完成等，按物品定向。 */
public interface ItemEvents {
    EventGroup GROUP = EventGroup.of("ItemEvents");

    EventBusJS<PlayerInteractEvent.RightClickItem, Item> RIGHT_CLICKED =
            GROUP.server("rightClicked", PlayerInteractEvent.RightClickItem.class, dispatchByItem(PlayerInteractEvent::getItemStack));

    /**
     * 运行时物品属性修改（server 脚本）：每次服务器启动（about-to-start）与
     * {@code /nekojs reload server} 时由平台侧手动 post（快照恢复模型，见
     * {@link ItemModificationEventJS}），不挂 NeoForge 总线。
     */
    EventBusJS<ItemModificationEventJS, Void> MODIFICATION =
            GROUP.server("modification", ItemModificationEventJS.class);

    EventBusJS<ItemTooltipEvent, Item> TOOLTIP =
            GROUP.client("tooltip", ItemTooltipEvent.class, dispatchByItem(ItemTooltipEvent::getItemStack));

    EventBusJS<ItemEntityPickupEvent.Pre, Item> CAN_PICK_UP =
            GROUP.server("canPickUp", ItemEntityPickupEvent.Pre.class, dispatchByPickupItem());
    // pickedUpPre：canPickUp 的冗余别名（同一 Pre 事件）。跨平台主名是 canPickUp/pickedUp
    // （部分加载器没有独立的 Pre 事件），脚本侧建议迁移到 canPickUp。
    @Deprecated
    EventBusJS<ItemEntityPickupEvent.Pre, Item> PICKED_UP_PRE =
            GROUP.server("pickedUpPre", ItemEntityPickupEvent.Pre.class, dispatchByPickupItem());
    EventBusJS<ItemEntityPickupEvent.Post, Item> PICKED_UP =
            GROUP.server("pickedUp", ItemEntityPickupEvent.Post.class, dispatchByPickupItem());
    EventBusJS<ItemTossEvent, Item> DROPPED =
            GROUP.server("dropped", ItemTossEvent.class, dispatchByItem(event -> event.getEntity().getItem()));
    EventBusJS<PlayerInteractEvent.EntityInteract, Item> ENTITY_INTERACTED =
            GROUP.server("entityInteracted", PlayerInteractEvent.EntityInteract.class, dispatchByItem(PlayerInteractEvent.EntityInteract::getItemStack));
    // 物品使用完成（吃完食物/用完盾弓等）。对标 KubeJS foodEaten，基于 LivingEntityUseItemEvent.Finish，
    // 脚本侧可用 event.item.isEdible() 等进一步过滤是否食物。
    EventBusJS<LivingEntityUseItemEvent.Finish, Item> FOOD_EATEN =
            GROUP.server("foodEaten", LivingEntityUseItemEvent.Finish.class, dispatchByItem(LivingEntityUseItemEvent::getItem));

    private static <T> DispatchKey<T, Item> dispatchByItem(Function<T, ItemStack> toStack) {
        return DispatchKey.of(Item.class, toStack.andThen(ItemStack::getItem));
    }

    private static <T extends ItemEntityPickupEvent> DispatchKey<T, Item> dispatchByPickupItem() {
        return dispatchByItem(event -> event.getItemEntity().getItem());
    }

    EventBusForgeBridge FORGE_BRIDGE = EventBusForgeBridge.create(NeoForge.EVENT_BUS)
            // RightClickItem 双逻辑侧触发：SERVER 总线只投递服务端实例（客户端交互在 Render 线程）
            .bind(RIGHT_CLICKED, e -> !e.getLevel().isClientSide())
            .bind(TOOLTIP)
            .bind(CAN_PICK_UP)
            .bind(PICKED_UP_PRE)
            .bind(PICKED_UP)
            .bind(DROPPED)
            .bind(ENTITY_INTERACTED, e -> !e.getLevel().isClientSide())
            .bind(FOOD_EATEN);
}
//?} else {
// fabric 节点孪生：共享树同名接口整文件 `//? if neoforge` 守卫（总线直传 NeoForge 原生
// 事件类）。fabric 侧 rightClicked 由 FabricItemEventBindings 提供（组实例各自注册、同名
// 合并），tooltip 由 FabricClientEventBindings 挂 ItemTooltipCallback 投递；canPickUp/
// pickedUpPre/pickedUp/dropped/foodEaten/entityInteracted 为 mixin 挂点批次
// （FabricItemEventBindingsV2 + fabric/mixin/*，中性载荷）。
package com.tkisor.nekojs.bindings.event;

import com.tkisor.nekojs.api.ScriptType;
import com.tkisor.nekojs.api.event.DispatchKey;
import com.tkisor.nekojs.api.event.EventBusJS;
import com.tkisor.nekojs.api.event.EventGroup;
import com.tkisor.nekojs.wrapper.event.item.ItemDroppedEventJS;
import com.tkisor.nekojs.wrapper.event.item.ItemEntityPickupEventJS;
import com.tkisor.nekojs.wrapper.event.item.ItemTooltipEventJS;
import com.tkisor.nekojs.wrapper.event.item.ItemUseFinishedEventJS;
import com.tkisor.nekojs.wrapper.event.item.PlayerEntityInteractEventJS;
import com.tkisor.nekojs.wrapper.event.server.ItemModificationEventJS;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/** 物品事件组（server/client 脚本）：右键、tooltip、拾取、丢弃、交互与使用完成等，按物品定向。 */
public interface ItemEvents {
    EventGroup GROUP = EventGroup.of("ItemEvents");

    /** tooltip（client 脚本）：按物品 id dispatch，lines 可变（mutate 即生效）。 */
    EventBusJS<ItemTooltipEventJS, Item> TOOLTIP =
            GROUP.client("tooltip", ItemTooltipEventJS.class,
                    DispatchKey.of(Item.class, event -> event.getItemStack().getItem()));

    // ---- server 侧 · mixin 挂点批次（见 FabricItemEventBindingsV2 / fabric/mixin/*）----

    /** 拾取前判定（server 脚本）：按物品 id dispatch；return true 阻止本次拾取。 */
    EventBusJS<ItemEntityPickupEventJS, Item> CAN_PICK_UP =
            GROUP.add("canPickUp", ScriptType.SERVER, EventBusJS.of(
                    ItemEntityPickupEventJS.class, true,
                    DispatchKey.of(Item.class, event -> event.getItem().getItem())));

    /** 可取消的 canPickUp 冗余别名（同一 Pre 事件）；跨平台主名 canPickUp。 */
    @Deprecated
    EventBusJS<ItemEntityPickupEventJS, Item> PICKED_UP_PRE =
            GROUP.add("pickedUpPre", ScriptType.SERVER, EventBusJS.of(
                    ItemEntityPickupEventJS.class, true,
                    DispatchKey.of(Item.class, event -> event.getItem().getItem())));

    /** 拾取成功（server 脚本）：按物品 id dispatch，通知型（不可取消）。 */
    EventBusJS<ItemEntityPickupEventJS, Item> PICKED_UP =
            GROUP.server("pickedUp", ItemEntityPickupEventJS.class,
                    DispatchKey.of(Item.class, event -> event.getItem().getItem()));

    /** 玩家丢弃物品（server 脚本）：按物品 id dispatch，通知型（fabric 侧不支持取消）。 */
    EventBusJS<ItemDroppedEventJS, Item> DROPPED =
            GROUP.server("dropped", ItemDroppedEventJS.class,
                    DispatchKey.of(Item.class, event -> event.getItem().getItem()));

    /** 物品使用完成（吃完食物/用完盾弓等）：按物品 id dispatch；event.isFood() 判是否食物。 */
    EventBusJS<ItemUseFinishedEventJS, Item> FOOD_EATEN =
            GROUP.server("foodEaten", ItemUseFinishedEventJS.class,
                    DispatchKey.of(Item.class, event -> event.getItem().getItem()));

    /** 玩家右击实体（server 脚本）：按交互时主手物品 id dispatch；return true 取消交互。 */
    EventBusJS<PlayerEntityInteractEventJS, Item> ENTITY_INTERACTED =
            GROUP.add("entityInteracted", ScriptType.SERVER, EventBusJS.of(
                    PlayerEntityInteractEventJS.class, true,
                    DispatchKey.of(Item.class, event -> event.getItem().getItem())));

    // modification（posted-object 模式）：服务器启动与 /nekojs reload server 时
    // 平台侧手动 post（ItemModificationEventJS.fire），不挂任何总线事件。
    EventBusJS<ItemModificationEventJS, Void> MODIFICATION =
            GROUP.server("modification", ItemModificationEventJS.class);
}
//?}
