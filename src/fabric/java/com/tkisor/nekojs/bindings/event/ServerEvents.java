// fabric 节点孪生：共享树同名接口整文件 `//? if neoforge` 守卫（生命周期/tick 等总线直传
// NeoForge 原生事件），fabric 侧已有 FabricServerEventBindings 以同名组提供中立子集。本孪生
// 只补配方面与票 22 村民交易声明的总线，与 FabricServerEventBindings 的 SERVER_EVENTS 同组名，
// 经 EventGroupRegistry 合并成脚本侧的同一个 ServerEvents 组。
//
// 票 22（Villager Trades）：payload 用共享树同一份 common 类（每个总线跨节点只有一个 payload
// 类型，与 ItemEvents.modification 同款跨加载器写法）。fabric 当前没有交易注册表 mutation
// 适配器，装配的是 VillagerTradeUnavailableDomainOwner：声明照常收集，整批在 joint preflight
// 以显式 unavailable 原因被拒绝（reload 报结构化失败，旧 active 保留），不是静默 no-op。
package com.tkisor.nekojs.bindings.event;

import com.tkisor.nekojs.api.event.EventBusJS;
import com.tkisor.nekojs.api.event.EventGroup;
import com.tkisor.nekojs.core.villager.VillagerTradeDeclarationEventJS;
import com.tkisor.nekojs.core.villager.VillagerTradeReloadEventJS;
import com.tkisor.nekojs.wrapper.event.server.RecipeEventJS;

/** 配方脚本事件（SERVER 侧）：总线名与 NeoForge 侧 {@code ServerEvents} 一致，脚本写法跨加载器相同。 */
public interface ServerEvents {
    EventGroup GROUP = EventGroup.of("ServerEvents");

    EventBusJS<RecipeEventJS, Void> RECIPES = GROUP.server("recipes", RecipeEventJS.class);
    EventBusJS<RecipeEventJS, Void> AFTER_RECIPES = GROUP.server("afterRecipes", RecipeEventJS.class);

    /** 村民交易声明（票 22）：见文件头说明，fabric 上整批显式 unavailable。 */
    EventBusJS<VillagerTradeDeclarationEventJS, Void> TRADE_DECLARATION =
            GROUP.server("tradeDeclaration", VillagerTradeDeclarationEventJS.class);

    /** 村民交易 reload 子事件（票 22）：同上。 */
    EventBusJS<VillagerTradeReloadEventJS, Void> TRADE_RELOAD =
            GROUP.server("tradeReload", VillagerTradeReloadEventJS.class);
}
