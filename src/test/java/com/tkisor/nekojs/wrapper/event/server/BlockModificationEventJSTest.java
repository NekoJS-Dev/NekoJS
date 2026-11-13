// TODO(fabric): the fabric side has no matching implementation yet; remove this whole-file guard
// once the port is done
//? if neoforge {
//? if >=26 {
package com.tkisor.nekojs.wrapper.event.server;

import com.tkisor.nekojs.api.ScriptType;
import com.tkisor.nekojs.api.event.EventBusJS;
import com.tkisor.nekojs.core.lifecycle.CandidateDomainCollector;
import com.tkisor.nekojs.core.modification.ModificationCandidatePlan;
import com.tkisor.nekojs.core.state.CandidateStatePlan;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * How the {@code BlockEvents.modification} event works: collect, build an inert plan, then Adapter
 * preflight, baseline restore and apply. The tests drive the Java API directly and never go through
 * JS. They cover a full write of all six properties (a copy of Properties, a copy of Block and a
 * copy of every BlockState), restoring the baseline of a state-dependent light function, restore
 * semantics for an empty plan, and errors during preflight and collection.
 *
 * <p>Each test holds its own {@link ModificationDomainOwner}. It stands in for the root-granted
 * domain owner and is instantiated directly: baselines are held per instance, and close in
 * {@code @AfterEach} restores and clears them, so other tests in the same JVM stay unaffected.
 *
 * <p>Environment: the 26.x {@code BuiltInRegistries} registration requires
 * {@code Bootstrap#bootStrap()} to run first; it sets the flag before triggering static registry
 * registration, so the order works out. On a bare JVM without the FML Loader vanilla bootstrap
 * cannot finish, so the test is skipped with an assumption instead of failing. The ModDev test
 * environment runs it normally; HolderAdapterTest documents the same limitation.
 */
class BlockModificationEventJSTest {

    private ModificationDomainOwner owner;

    @BeforeEach
    void requireVanillaRegistriesAndOwner() {
        // Shared probe: Bootstrap.bootStrap() sets its flag without throwing, so the Items class must
        // be initialised explicitly to expose a JVM without the FML Loader (see VanillaRegistryProbe)
        Assumptions.assumeTrue(com.tkisor.nekojs.testfixture.VanillaRegistryProbe.available(),
                "vanilla registries unavailable in this JVM (no FML loader?)");
        owner = new ModificationDomainOwner();
    }

    @AfterEach
    void restoreAllBlocks() {
        // Closing the owner restores the baselines it holds and clears them, so independent instances
        // do not affect each other
        if (owner != null) {
            owner.close();
        }
    }

    /** Collects one plan, then runs Adapter preflight and apply: the smallest direct form of the
     * production commit path. */
    private void collectAndApply(java.util.function.Consumer<BlockModificationEventJS> collector) {
        ModificationCandidatePlan plan = new ModificationCandidatePlan(owner);
        BlockModificationEventJS event = new BlockModificationEventJS(plan);
        collector.accept(event);
        plan.preflight();
        plan.publish();
    }

    @Test
    void candidateCollectionBeforeServerBindingStaysInert() {
        CandidateStatePlan[] captured = new CandidateStatePlan[1];

        owner.collect(new CandidateDomainCollector.Handle() {
            @Override
            public graal.graalvm.polyglot.Context candidateContext() {
                return null;
            }

            @Override
            public com.tkisor.nekojs.api.ScriptType scriptType() {
                return ScriptType.SERVER;
            }

            @Override
            public java.util.List<EventBusJS.PendingListener> listenersOf(EventBusJS<?, ?> bus) {
                return java.util.List.of();
            }

            @Override
            public void execute(EventBusJS.PendingListener listener, Object event) {
                throw new AssertionError("unbound server collection must not dispatch modification listeners");
            }

            @Override
            public void registerPlan(CandidateStatePlan plan) {
                captured[0] = plan;
            }
        });

        assertTrue(captured[0] instanceof ModificationCandidatePlan);
        assertTrue(((ModificationCandidatePlan) captured[0]).isEmpty());
    }


    @Test
    void modifyStoneHardnessThenEmptyPlanRestoresOriginal() {
        float original = Blocks.STONE.defaultBlockState().getDestroySpeed(null, null);

        collectAndApply(event -> event.modify("minecraft:stone", block -> block.setHardness(2.0f)));

        assertEquals(2.0f, Blocks.STONE.defaultBlockState().getDestroySpeed(null, null), 0.0f);
        // The Properties declaration source updates too: defaultDestroyTime reads Properties.destroyTime
        assertEquals(2.0f, Blocks.STONE.defaultDestroyTime(), 0.0f);

        // A no-declaration replay: applying an empty plan restores the baseline, so the values go back
        // to the originals. This no longer depends on restore-all-first inside the event.
        collectAndApply(event -> {});
        assertEquals(original, Blocks.STONE.defaultBlockState().getDestroySpeed(null, null), 0.0f);
        assertEquals(original, Blocks.STONE.defaultDestroyTime(), 0.0f);
        assertEquals(ModificationDomainOwner.Outcome.RESTORED, owner.lastDiagnostics().outcome(),
                "诊断区分 restored（空计划的成功形态）");
    }

    @Test
    void allSixPropertiesRoundTrip() {
        float originalHardness = Blocks.STONE.defaultBlockState().getDestroySpeed(null, null);
        float originalResistance = Blocks.STONE.getExplosionResistance();
        int originalLight = Blocks.STONE.defaultBlockState().getLightEmission();
        boolean originalRequiresTool = Blocks.STONE.defaultBlockState().requiresCorrectToolForDrops();
        float originalFriction = Blocks.STONE.getFriction();
        float originalJumpFactor = Blocks.STONE.getJumpFactor();

        collectAndApply(event -> event.modify("stone", block -> { // An id without a namespace prefix parses too
            block.setHardness(3.0f);
            block.setResistance(9.0f);
            block.setLightLevel(15);
            block.setRequiresTool(true);
            block.setFriction(0.98f);
            block.setJumpFactor(1.5f);
        }));

        assertEquals(3.0f, Blocks.STONE.defaultBlockState().getDestroySpeed(null, null), 0.0f);
        assertEquals(9.0f, Blocks.STONE.getExplosionResistance(), 0.0f);
        assertEquals(15, Blocks.STONE.defaultBlockState().getLightEmission());
        assertEquals(true, Blocks.STONE.defaultBlockState().requiresCorrectToolForDrops());
        assertEquals(0.98f, Blocks.STONE.getFriction(), 0.0f);
        assertEquals(1.5f, Blocks.STONE.getJumpFactor(), 0.0f);
        assertEquals(ModificationDomainOwner.Outcome.APPLIED, owner.lastDiagnostics().outcome());

        collectAndApply(event -> {});

        assertEquals(originalHardness, Blocks.STONE.defaultBlockState().getDestroySpeed(null, null), 0.0f);
        assertEquals(originalResistance, Blocks.STONE.getExplosionResistance(), 0.0f);
        assertEquals(originalLight, Blocks.STONE.defaultBlockState().getLightEmission());
        assertEquals(originalRequiresTool, Blocks.STONE.defaultBlockState().requiresCorrectToolForDrops());
        assertEquals(originalFriction, Blocks.STONE.getFriction(), 0.0f);
        assertEquals(originalJumpFactor, Blocks.STONE.getJumpFactor(), 0.0f);
    }

    @Test
    @SuppressWarnings("deprecation") // getLightEmission: NeoForge adds a context-taking overload; this reads the current value without one
    void multiStateWritesHitEveryStateAndRestoreLightFunction() {
        // Redstone lamp: its light function returns 15 or 0 depending on the LIT state (a multi-state block)
        Set<Integer> originalLights = lightValuesOf(Blocks.REDSTONE_LAMP);
        assertEquals(Set.of(0, 15), originalLights);

        collectAndApply(event -> event.modify("minecraft:redstone_lamp", block -> block.setLightLevel(7)));
        assertEquals(Set.of(7), lightValuesOf(Blocks.REDSTONE_LAMP));

        // Fence: a multi-state block with no light function, where requiresTool must be written into
        // every state
        collectAndApply(event -> event.modify("minecraft:oak_fence", block -> block.setRequiresTool(true)));
        assertTrue(Blocks.OAK_FENCE.getStateDefinition().getPossibleStates().stream()
                .allMatch(BlockState::requiresCorrectToolForDrops), "every state must require the tool");

        collectAndApply(event -> {});
        // What is restored is the original per-state light function, not a constant
        assertEquals(Set.of(0, 15), lightValuesOf(Blocks.REDSTONE_LAMP));
        assertTrue(Blocks.OAK_FENCE.getStateDefinition().getPossibleStates().stream()
                .noneMatch(BlockState::requiresCorrectToolForDrops), "requiresTool must revert on every state");
    }

    @Test
    @SuppressWarnings("deprecation") // The view getters go through getLightEmission without a context internally
    void viewGettersReadCurrentValueUntilSet() {
        float[] seen = new float[1];
        collectAndApply(event -> event.modify("minecraft:stone", block -> {
            assertEquals(1.5f, block.getHardness(), 0.0f); // Read the original value: stone hardness is 1.5, and live values are readable during collection
            assertEquals(0, block.getLightLevel());
            block.setLightLevel(12);
            assertEquals(12, block.getLightLevel()); // After a set, the getter reads the pending value
            seen[0] = block.getHardness();
        }));
        assertEquals(1.5f, seen[0], 0.0f, "unset properties must read the live value");
    }

    @Test
    void invalidValuesRejectedAtPreflightNotApplied() {
        ModificationCandidatePlan plan = new ModificationCandidatePlan(owner);
        BlockModificationEventJS event = new BlockModificationEventJS(plan);
        event.modify("minecraft:stone", block -> block.setHardness(-1.0f));
        assertThrows(IllegalArgumentException.class, plan::preflight);
        assertEquals(ModificationDomainOwner.Outcome.INITIAL, owner.lastDiagnostics().outcome(),
                "预检拒绝时不得有任何应用（声明已记录，但整批被拒——无部分修改）");
        assertEquals(1.5f, Blocks.STONE.defaultBlockState().getDestroySpeed(null, null), 0.0f,
                "live block untouched when the batch is rejected");

        ModificationCandidatePlan plan2 = new ModificationCandidatePlan(owner);
        BlockModificationEventJS event2 = new BlockModificationEventJS(plan2);
        event2.modify("minecraft:stone", block -> block.setLightLevel(16));
        assertThrows(IllegalArgumentException.class, plan2::preflight);
    }

    @Test
    void unknownBlockIdThrowsAtCollection() {
        ModificationCandidatePlan plan = new ModificationCandidatePlan(owner);
        BlockModificationEventJS event = new BlockModificationEventJS(plan);
        String message = assertThrows(IllegalArgumentException.class,
                () -> event.modify("minecraft:does_not_exist", block -> {})).getMessage();
        assertEquals("Unknown block: minecraft:does_not_exist", message);
    }

    @SuppressWarnings("deprecation")
    private static Set<Integer> lightValuesOf(Block block) {
        return block.getStateDefinition().getPossibleStates().stream()
                .map(BlockState::getLightEmission)
                .collect(Collectors.toUnmodifiableSet());
    }
}
//?}
//?}
