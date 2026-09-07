package com.tkisor.nekojs.villager;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VillagerTradePlanTest {
    @Test
    void preservesDeclarationOrderAndValidDuplicates() {
        VillagerTradePlan.Offer first = offer("minecraft:apple");
        VillagerTradePlan.Offer second = offer("minecraft:bread");
        VillagerTradePlan plan = VillagerTradePlan.builder()
                .add(first)
                .add(first)
                .add(second)
                .seal();

        assertEquals(List.of(first, first, second), plan.offers());
        assertEquals(3, plan.size());
        assertFalse(plan.isEmpty());
    }

    @Test
    void emptyBuilderSealsToAnEmptyPlan() {
        VillagerTradePlan plan = VillagerTradePlan.builder().seal();

        assertEquals(VillagerTradePlan.empty(), plan);
        assertTrue(plan.isEmpty());
        assertEquals(0, plan.size());
    }

    @Test
    void sealedPlanAndOffersAreImmutable() {
        VillagerTradePlan.Builder builder = VillagerTradePlan.builder();
        builder.add(offer("minecraft:apple"));
        VillagerTradePlan plan = builder.seal();

        assertThrows(UnsupportedOperationException.class,
                () -> plan.offers().add(offer("minecraft:bread")));
        assertThrows(IllegalStateException.class,
                () -> builder.add(offer("minecraft:bread")));
        assertEquals(plan, builder.seal());
    }

    @Test
    void normalizesAbsentSecondaryCost() {
        VillagerTradePlan.Offer offer = new VillagerTradePlan.Offer(
                "minecraft:farmer/level_1",
                "minecraft:emerald",
                1,
                null,
                0,
                "minecraft:apple",
                5,
                12,
                2,
                0.05f);

        assertEquals(1, offer.additionalWantsCount());
        assertEquals("minecraft:emerald", offer.costId());
        assertEquals(5, offer.resultCount());
    }

    @Test
    void rejectsMalformedIdsAndOfferFields() {
        assertThrows(IllegalArgumentException.class,
                () -> offerWith("farmer/level_1", "minecraft:apple", 1, 2, 0.05f));
        assertThrows(IllegalArgumentException.class,
                () -> offerWith("minecraft:farmer/level_1", "apple", 1, 2, 0.05f));
        assertThrows(IllegalArgumentException.class,
                () -> offerWith("minecraft:farmer/level_1", "minecraft:apple", 0, 2, 0.05f));
        assertThrows(IllegalArgumentException.class,
                () -> offerWith("minecraft:farmer/level_1", "minecraft:apple", 1, 0, 0.05f));
        assertThrows(IllegalArgumentException.class,
                () -> offerWith("minecraft:farmer/level_1", "minecraft:apple", 1, 2, -0.01f));
        assertThrows(IllegalArgumentException.class,
                () -> offerWith("minecraft:farmer/level_1", "minecraft:apple", 1, 2, 1.01f));
    }

    @Test
    void canonicalIdentifierCheckRequiresNamespace() {
        assertTrue(VillagerTradePlan.isCanonicalIdentifier("minecraft:wandering_trader/level_1"));
        assertFalse(VillagerTradePlan.isCanonicalIdentifier("wandering_trader/level_1"));
        assertFalse(VillagerTradePlan.isCanonicalIdentifier("minecraft:"));
        assertFalse(VillagerTradePlan.isCanonicalIdentifier("minecraft:farmer level_1"));
    }

    private static VillagerTradePlan.Offer offer(String result) {
        return new VillagerTradePlan.Offer(
                "minecraft:farmer/level_1",
                "minecraft:emerald",
                1,
                null,
                1,
                result,
                1,
                12,
                2,
                0.05f);
    }

    private static VillagerTradePlan.Offer offerWith(
            String tradeSet,
            String result,
            int resultCount,
            int maxUses,
            float priceMultiplier) {
        return new VillagerTradePlan.Offer(
                tradeSet,
                "minecraft:emerald",
                1,
                null,
                1,
                result,
                resultCount,
                maxUses,
                2,
                priceMultiplier);
    }
}
