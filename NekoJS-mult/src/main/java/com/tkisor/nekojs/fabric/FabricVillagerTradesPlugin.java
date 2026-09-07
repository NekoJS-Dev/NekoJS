//? if fabric {
package com.tkisor.nekojs.fabric;

import com.tkisor.nekojs.api.NekoJSPlugin;
import com.tkisor.nekojs.api.event.EventGroupRegistry;
import com.tkisor.nekojs.bindings.event.VillagerTradesEvents;

/** Explicit Fabric built-in registration for the loader-neutral VillagerTrades event. */
public final class FabricVillagerTradesPlugin implements NekoJSPlugin, com.tkisor.nekojs.core.plugin.EventsPoint.Contributor {
    @Override
    public void registerEvents(EventGroupRegistry registry) {
        registry.register(VillagerTradesEvents.GROUP);
    }
}
//?}
