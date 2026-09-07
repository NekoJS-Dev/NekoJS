//? if fabric {
package com.tkisor.nekojs.villager;

import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.trading.TradeSet;
import net.minecraft.core.registries.Registries;

/** Fabric 26.x trade registry adapter. Its native epoch is still exposed through the vanilla manager seam. */
public final class FabricVillagerTradeRegistryAdapter implements VillagerTradeRegistryAdapter<MinecraftServer> {
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
        VillagerTradeManager.apply(server);
    }

    @Override
    public void reset() {
        VillagerTradeManager.reset();
    }
}
//?}
