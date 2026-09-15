// fabric 节点孪生：共享树同名接口整文件 `//? if neoforge` 守卫（总线直传 NeoForge 原生
// 事件类）。fabric 侧由 FabricLevelEventBindings 以中立 payload 提供子集；爆炸系与
// saved 需 mixin（见 docs/fabric-port-status.md），落地后本孪生随之扩。共享树版事件载荷
// 中立化后合并回单副本。
package com.tkisor.nekojs.bindings.event;

import com.tkisor.nekojs.api.event.EventBusJS;
import com.tkisor.nekojs.api.event.EventGroup;
import com.tkisor.nekojs.wrapper.event.level.LevelEventJS;

/** 维度（Level）事件组（server 脚本）：加载/卸载与 tick（fabric 子集）。 */
public interface LevelEvents {
    EventGroup GROUP = EventGroup.of("LevelEvents");

    EventBusJS<LevelEventJS, Void> LOADED =
            GROUP.server("loaded", LevelEventJS.class);
    EventBusJS<LevelEventJS, Void> UNLOADED =
            GROUP.server("unloaded", LevelEventJS.class);
    EventBusJS<LevelEventJS, Void> TICK_PRE =
            GROUP.server("tickPre", LevelEventJS.class);
    EventBusJS<LevelEventJS, Void> TICK_POST =
            GROUP.server("tickPost", LevelEventJS.class);
}
