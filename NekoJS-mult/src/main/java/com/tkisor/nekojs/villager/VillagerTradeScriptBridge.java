//? if >=26 {
package com.tkisor.nekojs.villager;

import com.tkisor.nekojs.bindings.event.VillagerTradesEvents;
import com.tkisor.nekojs.wrapper.event.server.VillagerTradeEventJS;
import net.minecraft.server.MinecraftServer;

/** Version-tree composition root for the declarative VillagerTrades event. */
public final class VillagerTradeScriptBridge {
    private static final VillagerTradeLifecycle<MinecraftServer> LIFECYCLE =
            new VillagerTradeLifecycle<>(platformAdapter());

    private VillagerTradeScriptBridge() {}

    private static VillagerTradeRegistryAdapter<MinecraftServer> platformAdapter() {
//? if neoforge {
        return new NeoForgeVillagerTradeRegistryAdapter();
//?} else {
/*        return new FabricVillagerTradeRegistryAdapter();
*///?}
    }

    /** Starts the contribution revision before SERVER scripts are reloaded. */
    public static void beginServerReload() {
        VillagerTradeManager.beginReload();
        LIFECYCLE.begin();
    }

    /** Posts the declaration event and reconciles the complete plan to the epoch. */
    public static void reconcile(MinecraftServer server) {
        if (!LIFECYCLE.isCollecting()) {
            LIFECYCLE.begin();
        }
        VillagerTradeEventJS event = new VillagerTradeEventJS(LIFECYCLE.collect());
        VillagerTradesEvents.MODIFY.post(event);
        LIFECYCLE.seal();
        LIFECYCLE.registryReady(server);
    }

    /** Clears lifecycle and native adapter state on server stop. */
    public static void stop() {
        LIFECYCLE.stop();
    }
}
//?}
