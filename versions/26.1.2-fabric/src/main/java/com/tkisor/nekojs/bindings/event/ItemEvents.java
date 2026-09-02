// fabric 节点孪生：共享树同名接口整文件 `//? if neoforge` 守卫（总线直传 NeoForge 原生
// 事件类）。fabric 侧 rightClicked 由 FabricItemEventBindings 提供（组实例各自注册、同名
// 合并），tooltip 由 FabricClientEventBindings 挂 ItemTooltipCallback 投递；其余总线
// （modification/拾取/丢弃/foodEaten 等）见 docs/fabric-port-status.md 的 P1/P2 清单。
package com.tkisor.nekojs.bindings.event;

import com.tkisor.nekojs.api.event.DispatchKey;
import com.tkisor.nekojs.api.event.EventBusJS;
import com.tkisor.nekojs.api.event.EventGroup;
import com.tkisor.nekojs.wrapper.event.item.ItemTooltipEventJS;
import net.minecraft.world.item.Item;

/** 物品事件组（server/client 脚本）：右键与 tooltip（fabric 子集），按物品定向。 */
public interface ItemEvents {
    EventGroup GROUP = EventGroup.of("ItemEvents");

    /** tooltip（client 脚本）：按物品 id dispatch，lines 可变（mutate 即生效）。 */
    EventBusJS<ItemTooltipEventJS, Item> TOOLTIP =
            GROUP.client("tooltip", ItemTooltipEventJS.class,
                    DispatchKey.of(Item.class, event -> event.getItemStack().getItem()));
}
