//? if neoforge {
package com.tkisor.nekojs.bindings.event;

import com.tkisor.nekojs.api.event.EventBusForgeBridge;
import com.tkisor.nekojs.api.event.EventBusJS;
import com.tkisor.nekojs.api.event.EventGroup;
import com.tkisor.nekojs.api.event.DispatchKey;
import com.tkisor.nekojs.wrapper.event.player.InventoryChangedEventJS;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.ServerChatEvent;
import net.neoforged.neoforge.event.entity.player.*;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.util.function.Function;

/** 玩家事件组（server 脚本）：登录登出、聊天、tick、重生、合成/烧炼与物品栏变化等（合成类按物品定向）。 */
public interface PlayerEvents {
    EventGroup GROUP = EventGroup.of("PlayerEvents");

    EventBusJS<PlayerEvent.PlayerLoggedInEvent, Void> LOGGED_IN =
            GROUP.server("loggedIn", PlayerEvent.PlayerLoggedInEvent.class);
    EventBusJS<PlayerEvent.PlayerLoggedOutEvent, Void> LOGGED_OUT =
            GROUP.server("loggedOut", PlayerEvent.PlayerLoggedOutEvent.class);
    EventBusJS<ServerChatEvent, Void> CHAT =
            GROUP.server("chat", ServerChatEvent.class);
    EventBusJS<PlayerTickEvent.Post, Void> TICK_POST =
            GROUP.server("tickPost", PlayerTickEvent.Post.class);
    EventBusJS<PlayerTickEvent.Pre, Void> TICK_PRE =
            GROUP.server("tickPre", PlayerTickEvent.Pre.class);
    EventBusJS<PlayerEvent.Clone, Void> CLONED =
            GROUP.server("cloned", PlayerEvent.Clone.class);
    EventBusJS<PlayerEvent.PlayerRespawnEvent, Void> RESPAWNED =
            GROUP.server("respawned", PlayerEvent.PlayerRespawnEvent.class);
    EventBusJS<PlayerEvent.PlayerChangedDimensionEvent, Void> CHANGED_DIMENSION =
            GROUP.server("changedDimension", PlayerEvent.PlayerChangedDimensionEvent.class);
    EventBusJS<AdvancementEvent.AdvancementEarnEvent, Void> ADVANCEMENT =
            GROUP.server("advancement", AdvancementEvent.AdvancementEarnEvent.class);
    EventBusJS<PlayerContainerEvent.Open, Void> CONTAINER_OPENED =
            GROUP.server("containerOpened", PlayerContainerEvent.Open.class);
    // inventoryOpened：containerOpened 的跨版本兼容别名。脚本侧建议迁移到 containerOpened。
    @Deprecated
    EventBusJS<PlayerContainerEvent.Open, Void> INVENTORY_OPENED =
            GROUP.server("inventoryOpened", PlayerContainerEvent.Open.class);
    EventBusJS<PlayerContainerEvent.Close, Void> CONTAINER_CLOSED =
            GROUP.server("containerClosed", PlayerContainerEvent.Close.class);
    // inventoryClosed：containerClosed 的跨版本兼容别名。脚本侧建议迁移到 containerClosed。
    @Deprecated
    EventBusJS<PlayerContainerEvent.Close, Void> INVENTORY_CLOSED =
            GROUP.server("inventoryClosed", PlayerContainerEvent.Close.class);
    EventBusJS<PlayerInteractEvent.EntityInteract, Void> ENTITY_INTERACT =
            GROUP.server("entityInteract", PlayerInteractEvent.EntityInteract.class);
    EventBusJS<PlayerEvent.ItemCraftedEvent, Item> CRAFTED =
            GROUP.server("crafted", PlayerEvent.ItemCraftedEvent.class, dispatchByItem(PlayerEvent.ItemCraftedEvent::getCrafting));
    EventBusJS<PlayerEvent.ItemSmeltedEvent, Item> SMELTED =
            GROUP.server("smelted", PlayerEvent.ItemSmeltedEvent.class, dispatchByItem(PlayerEvent.ItemSmeltedEvent::getSmelting));
    EventBusJS<PlayerDestroyItemEvent, Item> DESTROYED =
            GROUP.server("destroyed", PlayerDestroyItemEvent.class, dispatchByItem(PlayerDestroyItemEvent::getOriginal));

    /** 玩家物品栏变化：按物品 id 分发（{@code PlayerEvents.inventoryChanged('minecraft:stone', ...)}）。 */
    EventBusJS<InventoryChangedEventJS, Item> INVENTORY_CHANGED =
            GROUP.server("inventoryChanged", InventoryChangedEventJS.class, dispatchByInventoryItem());

    private static <T> DispatchKey<T, Item> dispatchByItem(Function<T, ItemStack> toStack) {
        return DispatchKey.of(Item.class, toStack.andThen(ItemStack::getItem));
    }

    private static DispatchKey<InventoryChangedEventJS, Item> dispatchByInventoryItem() {
        return DispatchKey.of(Item.class, e -> e.getItem().getItem());
    }

    EventBusForgeBridge FORGE_BRIDGE = EventBusForgeBridge.create(NeoForge.EVENT_BUS)
            .bind(LOGGED_IN)
            .bind(LOGGED_OUT)
            .bind(CHAT)
            // PlayerTickEvent 双逻辑侧触发：SERVER 总线只投递服务端实例（客户端玩家 tick
            // 在 Render 线程，进入 SERVER Context 会被 Graal 拒绝多线程访问）
            .bind(TICK_POST, e -> !e.getEntity().level().isClientSide())
            .bind(TICK_PRE, e -> !e.getEntity().level().isClientSide())
            .bind(CLONED)
            .bind(RESPAWNED)
            .bind(CHANGED_DIMENSION)
            .bind(ADVANCEMENT)
            .bind(CONTAINER_OPENED)
            .bind(INVENTORY_OPENED)
            .bind(CONTAINER_CLOSED)
            .bind(INVENTORY_CLOSED)
            // EntityInteract 双逻辑侧触发：SERVER 总线只投递服务端实例
            .bind(ENTITY_INTERACT, e -> !e.getLevel().isClientSide())
            .bind(CRAFTED)
            .bind(SMELTED)
            .bind(DESTROYED);
}
//?} else {
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
//?}
