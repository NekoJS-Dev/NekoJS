package com.tkisor.nekojs.villager;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VillagerTradeLifecycleTest {
    private RecordingAdapter adapter;
    private VillagerTradeLifecycle<String> lifecycle;

    @BeforeEach
    void setUp() {
        adapter = new RecordingAdapter();
        lifecycle = new VillagerTradeLifecycle<>(adapter);
    }

    @Test
    void waitsForBothSealedPlanAndRegistryEpoch() {
        lifecycle.begin();
        lifecycle.collect().add(offer("minecraft:apple"));

        assertFalse(lifecycle.registryReady("epoch-1"));
        assertEquals(0, adapter.applied.size());

        VillagerTradePlan plan = lifecycle.seal();

        assertSame(plan, lifecycle.committedPlan());
        assertEquals(1, adapter.applied.size());
        assertEquals("epoch-1", adapter.applied.getFirst().epoch());
        assertEquals(List.of(offer("minecraft:apple")), adapter.applied.getFirst().plan().offers());
    }

    @Test
    void failedCandidateDoesNotReplaceCommittedPlan() {
        lifecycle.begin();
        lifecycle.collect().add(offer("minecraft:apple"));
        VillagerTradePlan previous = lifecycle.seal();
        lifecycle.registryReady("epoch-1");

        lifecycle.begin();
        assertThrows(IllegalArgumentException.class, () -> lifecycle.collect().add(
                new VillagerTradePlan.Offer(
                        "minecraft:farmer/level_1",
                        "minecraft:emerald",
                        0,
                        null,
                        1,
                        "minecraft:bread",
                        1,
                        12,
                        2,
                        0.05f)));

        assertSame(previous, lifecycle.committedPlan());
        assertEquals(1, adapter.applied.size());
        assertTrue(lifecycle.isCollecting());
    }

    @Test
    void emptyPlanIsStillReconciled() {
        lifecycle.begin();
        VillagerTradePlan plan = lifecycle.seal();

        assertTrue(plan.isEmpty());
        assertTrue(lifecycle.registryReady("epoch-1"));
        assertEquals(1, adapter.applied.size());
        assertTrue(adapter.applied.getFirst().plan().isEmpty());
    }

    @Test
    void repeatedEpochNotificationIsIdempotent() {
        lifecycle.begin();
        lifecycle.collect().add(offer("minecraft:apple"));
        lifecycle.seal();

        assertTrue(lifecycle.registryReady("epoch-1"));
        assertFalse(lifecycle.registryReady("epoch-1"));
        assertFalse(lifecycle.registryReady("epoch-1"));
        assertEquals(1, adapter.applied.size());
    }

    @Test
    void newEpochReconcilesAndNewPlanRevisionReconcilesSameEpoch() {
        lifecycle.begin();
        lifecycle.collect().add(offer("minecraft:apple"));
        lifecycle.seal();
        lifecycle.registryReady("epoch-1");

        assertTrue(lifecycle.registryReady("epoch-2"));
        assertEquals(2, adapter.applied.size());

        lifecycle.begin();
        lifecycle.collect().add(offer("minecraft:bread"));
        lifecycle.seal();

        assertEquals(2, adapter.applied.size());
        assertTrue(lifecycle.registryReady("epoch-2"));
        assertEquals(3, adapter.applied.size());
        assertEquals("epoch-2", adapter.applied.getLast().epoch());
        assertEquals("minecraft:bread", adapter.applied.getLast().plan().offers().getFirst().givesId());
    }

    @Test
    void stopResetsPlansEpochAndAdapterState() {
        lifecycle.begin();
        lifecycle.collect().add(offer("minecraft:apple"));
        lifecycle.seal();
        lifecycle.registryReady("epoch-1");

        lifecycle.stop();

        assertNull(lifecycle.committedPlan());
        assertFalse(lifecycle.hasSealedPlan());
        assertFalse(lifecycle.isCollecting());
        assertNull(lifecycle.registryEpoch());
        assertEquals(1, adapter.resetCalls);
        assertFalse(lifecycle.registryReady("epoch-1"));

        lifecycle.begin();
        lifecycle.seal();
        assertTrue(lifecycle.registryReady("epoch-1"));
        assertEquals(2, adapter.applied.size());
    }

    @Test
    void adapterFailureLeavesEpochUnappliedForRetry() {
        adapter.failNext = true;
        lifecycle.begin();
        lifecycle.collect().add(offer("minecraft:apple"));
        lifecycle.seal();

        assertThrows(IllegalStateException.class, () -> lifecycle.registryReady("epoch-1"));
        assertEquals(0, adapter.applied.size());
        assertTrue(lifecycle.registryReady("epoch-1"));
        assertEquals(1, adapter.applied.size());
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

    private static final class RecordingAdapter implements VillagerTradeRegistryAdapter<String> {
        private final List<Applied> applied = new ArrayList<>();
        private int resetCalls;
        private boolean failNext;

        @Override
        public void reconcile(VillagerTradePlan plan, String epoch) {
            if (failNext) {
                failNext = false;
                throw new IllegalStateException("synthetic adapter failure");
            }
            applied.add(new Applied(plan, epoch));
        }

        @Override
        public void reset() {
            resetCalls++;
        }
    }

    private record Applied(VillagerTradePlan plan, String epoch) {
    }
}
