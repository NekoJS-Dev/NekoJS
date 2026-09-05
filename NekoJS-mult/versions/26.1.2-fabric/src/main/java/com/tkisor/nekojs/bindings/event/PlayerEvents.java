// fabric 节点孪生：共享树同名接口整文件 `//? if neoforge` 守卫（总线直传 NeoForge 原生
// 事件类），本孪生只包含 fabric 侧由 mixin 提供的「mixin 面」玩家事件总线；payload 为
// 共享树中立类（wrapper/event/player）。loggedIn/loggedOut/chat/tickPre/tickPost/cloned/
// respawned 已由 FabricServerEventBindings.PLAYER_EVENTS 提供，不在此重复声明——同名组
// "PlayerEvents" 经 EventGroupRegistry 按名合并（与 ServerEvents 孪生同机制），脚本侧
// 组名与总线名与 NeoForge 侧一致。共享树版事件载荷中立化后合并回单副本。
package com.tkisor.nekojs.bindings.event;

import com.tkisor.nekojs.api.ScriptType;
import com.tkisor.nekojs.api.event.DispatchKey;
import com.tkisor.nekojs.api.event.EventBusJS;
import com.tkisor.nekojs.api.event.EventGroup;
import com.tkisor.nekojs.wrapper.event.player.InventoryChangedEventJS;
import com.tkisor.nekojs.wrapper.event.player.PlayerAdvancementEventJS;
import com.tkisor.nekojs.wrapper.event.player.PlayerChangedDimensionEventJS;
import com.tkisor.nekojs.wrapper.event.player.PlayerContainerEventJS;
import com.tkisor.nekojs.wrapper.event.player.PlayerCraftedEventJS;
import com.tkisor.nekojs.wrapper.event.player.PlayerDestroyItemEventJS;
import com.tkisor.nekojs.wrapper.event.player.PlayerEntityInteractEventJS;
import net.minecraft.world.item.Item;

/** 玩家事件组（server 脚本）的 fabric mixin 面子集：容器开合、合成/烧炼/损毁、进度、实体交互、维度切换。 */
public interface PlayerEvents {
    EventGroup GROUP = EventGroup.of("PlayerEvents");

    EventBusJS<PlayerContainerEventJS, Void> CONTAINER_OPENED =
            GROUP.server("containerOpened", PlayerContainerEventJS.class);
    // inventoryOpened：containerOpened 的跨版本兼容别名。脚本侧建议迁移到 containerOpened。
    @Deprecated
    EventBusJS<PlayerContainerEventJS, Void> INVENTORY_OPENED =
            GROUP.server("inventoryOpened", PlayerContainerEventJS.class);
    EventBusJS<PlayerContainerEventJS, Void> CONTAINER_CLOSED =
            GROUP.server("containerClosed", PlayerContainerEventJS.class);
    // inventoryClosed：containerClosed 的跨版本兼容别名。脚本侧建议迁移到 containerClosed。
    @Deprecated
    EventBusJS<PlayerContainerEventJS, Void> INVENTORY_CLOSED =
            GROUP.server("inventoryClosed", PlayerContainerEventJS.class);

    // crafted / smelted：按产物物品 id 分发（与 NeoForge 侧 PlayerEvents 的 dispatchByItem 同构）
    EventBusJS<PlayerCraftedEventJS, Item> CRAFTED =
            GROUP.server("crafted", PlayerCraftedEventJS.class,
                    DispatchKey.of(Item.class, e -> e.getResult().getItem()));
    EventBusJS<PlayerCraftedEventJS, Item> SMELTED =
            GROUP.server("smelted", PlayerCraftedEventJS.class,
                    DispatchKey.of(Item.class, e -> e.getResult().getItem()));
    EventBusJS<PlayerDestroyItemEventJS, Item> DESTROYED =
            GROUP.server("destroyed", PlayerDestroyItemEventJS.class,
                    DispatchKey.of(Item.class, e -> e.getItemStack().getItem()));

    EventBusJS<PlayerAdvancementEventJS, Void> ADVANCEMENT =
            GROUP.server("advancement", PlayerAdvancementEventJS.class);

    // entityInteract：NeoForge 侧是 ICancellableEvent（可取消），fabric 无 ICancellableEvent
    // 判定链（EventGroup.server 的默认可取消性谓词在 fabric 上恒为 false），显式建可取消总线；
    // 监听器返回 true → 取消 ServerboundInteractPacket 处理（不执行 interactOn）。
    EventBusJS<PlayerEntityInteractEventJS, Void> ENTITY_INTERACT =
            GROUP.add("entityInteract", ScriptType.SERVER,
                    EventBusJS.of(PlayerEntityInteractEventJS.class, true));

    EventBusJS<PlayerChangedDimensionEventJS, Void> CHANGED_DIMENSION =
            GROUP.server("changedDimension", PlayerChangedDimensionEventJS.class);

    // inventoryChanged：按物品 id 分发，与 NeoForge 侧共享树声明同构
    // （监听器挂载见 FabricPlayerEventBindings.registerLifecycle）。
    EventBusJS<InventoryChangedEventJS, Item> INVENTORY_CHANGED =
            GROUP.server("inventoryChanged", InventoryChangedEventJS.class,
                    DispatchKey.of(Item.class, e -> e.getItem().getItem()));
}
