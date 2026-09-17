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

    // modification（posted-object 模式）：票 39 起由平台 domain owner 在服务器启动收集点
    // （about-to-start）与 SERVER reload 的候选 DOMAIN_PLAN 阶段 post（收集进 inert 计划、
    // commit 点由 Adapter 应用），不挂任何总线事件。
    EventBusJS<ItemModificationEventJS, Void> MODIFICATION =
            GROUP.server("modification", ItemModificationEventJS.class);
}
