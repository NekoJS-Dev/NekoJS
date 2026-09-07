//? if neoforge && >=26 {
package com.tkisor.nekojs.villager;

import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.trading.TradeSet;
import net.minecraft.core.registries.Registries;

/** NeoForge 26.x trade registry adapter. Native mutation remains behind the manager seam. */
public final class NeoForgeVillagerTradeRegistryAdapter implements VillagerTradeRegistryAdapter<MinecraftServer> {
    @Override
    public void reconcile(VillagerTradePlan plan, MinecraftServer server) {
        for (VillagerTradePlan.Offer offer : plan.offers()) {
            ResourceKey<TradeSet> tradeSet = ResourceKey.create(
                    Registries.TRADE_SET, Identifier.parse(offer.tradeSet()));
            VillagerTradeManager.stageAdd(new VillagerTradeManager.PendingTrade(
                    tradeSet, Identifier.parse(offer.wantsId()), offer.wantsCount(),
                    offer.additionalWantsId() == null ? null : Identifier.parse(offer.additionalWantsId()),
                    offer.additionalWantsCount(), Identifier.parse(offer.givesId()), offer.givesCount(),
                    offer.maxUses(), offer.xp(), offer.priceMultiplier()));
        }
        boolean applied = VillagerTradeManager.apply(server);
        if (!applied && !plan.isEmpty()) {
            throw new IllegalStateException("Villager trade registry reconciliation did not apply");
        }
    }

    @Override
    public void reset() {
        VillagerTradeManager.reset();
    }
}
//?}
