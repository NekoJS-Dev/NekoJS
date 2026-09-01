// fabric 节点孪生：共享树同名接口整文件 `//? if neoforge` 守卫（生命周期/tick 等总线直传
// NeoForge 原生事件），fabric 侧已有 FabricServerEventBindings 以同名组提供中立子集。本孪生
// 只补配方面需要的两条总线（RecipeManagerMixin 孪生 fire），与 FabricServerEventBindings 的
// SERVER_EVENTS 同组名，经 EventGroupRegistry 合并成脚本侧的同一个 ServerEvents 组。
package com.tkisor.nekojs.bindings.event;

import com.tkisor.nekojs.api.event.EventBusJS;
import com.tkisor.nekojs.api.event.EventGroup;
import com.tkisor.nekojs.wrapper.event.server.RecipeEventJS;

/** 配方脚本事件（SERVER 侧）：总线名与 NeoForge 侧 {@code ServerEvents} 一致，脚本写法跨加载器相同。 */
public interface ServerEvents {
    EventGroup GROUP = EventGroup.of("ServerEvents");

    EventBusJS<RecipeEventJS, Void> RECIPES = GROUP.server("recipes", RecipeEventJS.class);
    EventBusJS<RecipeEventJS, Void> AFTER_RECIPES = GROUP.server("afterRecipes", RecipeEventJS.class);
}
