// Fabric copy of the shared tree's ServerEvents interface, which is guarded file-wide by
// `//? if neoforge` (lifecycle, tick and similar buses pass straight through to the native
// NeoForge events). Fabric already has FabricServerEventBindings supplying a neutral subset under
// the same group name; this copy only adds the recipe-side buses and the villager trade
// declaration buses. Both use the group name SERVER_EVENTS, so EventGroupRegistry merges them
// into one ServerEvents group for scripts.
//
// Villager trades: the payloads use the same common classes from the shared tree (each bus has a
// single payload type across nodes, written the same way as ItemEvents.modification). Fabric has
// no adapter that mutates the trade registry, so it wires VillagerTradeUnavailableDomainOwner:
// declarations are still collected, and the whole batch is rejected during preflight with an
// explicit unavailable reason (the reload reports a structured failure and the previous active
// set is kept), rather than being silently ignored.
package com.tkisor.nekojs.bindings.event;

import com.tkisor.nekojs.api.event.EventBusJS;
import com.tkisor.nekojs.api.event.EventGroup;
import com.tkisor.nekojs.core.villager.VillagerTradeDeclarationEventJS;
import com.tkisor.nekojs.core.villager.VillagerTradeReloadEventJS;
import com.tkisor.nekojs.wrapper.event.server.RecipeEventJS;

/** Recipe script events (SERVER side): the bus names match {@code ServerEvents} on NeoForge, so
 * scripts look the same on both loaders. */
public interface ServerEvents {
    EventGroup GROUP = EventGroup.of("ServerEvents").expose(api -> api.versioned());

    EventBusJS<RecipeEventJS, Void> RECIPES = GROUP.server("recipes", RecipeEventJS.class);
    EventBusJS<RecipeEventJS, Void> AFTER_RECIPES = GROUP.server("afterRecipes", RecipeEventJS.class);

    /** Villager trade declarations: see the file header; on Fabric the whole batch is explicitly
     * unavailable. */
    EventBusJS<VillagerTradeDeclarationEventJS, Void> TRADE_DECLARATION =
            GROUP.server("tradeDeclaration", VillagerTradeDeclarationEventJS.class);

    /** Villager trade reload sub-event: same as above. */
    EventBusJS<VillagerTradeReloadEventJS, Void> TRADE_RELOAD =
            GROUP.server("tradeReload", VillagerTradeReloadEventJS.class);
}
