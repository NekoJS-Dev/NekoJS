//? if >=26 {
package com.tkisor.nekojs.villager;

import com.tkisor.nekojs.bindings.event.VillagerTradesEvents;
import com.tkisor.nekojs.wrapper.event.server.VillagerTradeEventJS;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.trading.TradeSet;

/**
 * Version-tree composition root for the declarative VillagerTrades event.
 *
 * <p>The neutral lifecycle owns collection and epoch idempotence. This adapter
 * only translates the sealed plan to the existing 26.x registry manager.</p>
 */
public final class VillagerTradeScriptBridge {
    private static final VillagerTradeLifecycle<MinecraftServer> LIFECYCLE =
            new VillagerTradeLifecycle<>(new VillagerTradeRegistryAdapter<>() {
                @Override
                public void reconcile(VillagerTradePlan plan, MinecraftServer server) {
                    for (VillagerTradePlan.Offer offer : plan.offers()) {
                        ResourceKey<TradeSet> tradeSet = ResourceKey.create(
                                Registries.TRADE_SET, Identifier.parse(offer.tradeSet()));
                        VillagerTradeManager.stageAdd(new VillagerTradeManager.PendingTrade(
                                tradeSet,
                                Identifier.parse(offer.wantsId()),
                                offer.wantsCount(),
                                offer.additionalWantsId() == null ? null : Identifier.parse(offer.additionalWantsId()),
                                offer.additionalWantsCount(),
                                Identifier.parse(offer.givesId()),
                                offer.givesCount(),
                                offer.maxUses(),
                                offer.xp(),
                                offer.priceMultiplier()));
                    }
                    VillagerTradeManager.apply(server);
                }

                @Override
                public void reset() {
                    VillagerTradeManager.reset();
                }
            });

    private VillagerTradeScriptBridge() {}

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
