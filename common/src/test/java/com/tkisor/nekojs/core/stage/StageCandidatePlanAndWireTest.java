package com.tkisor.nekojs.core.stage;

import com.tkisor.nekojs.api.data.NekoId;
import com.tkisor.nekojs.api.stage.StableStageGatesEvent;
import com.tkisor.nekojs.api.stage.StageCustomizeEvent;
import com.tkisor.nekojs.api.stage.StageDefinition;
import com.tkisor.nekojs.api.stage.StageHandle;
import com.tkisor.nekojs.api.stage.StageScope;
import com.tkisor.nekojs.api.stage.gate.BlockGateBuilder;
import com.tkisor.nekojs.api.stage.gate.ContainerGateBuilder;
import com.tkisor.nekojs.api.stage.gate.EffectGateBuilder;
import com.tkisor.nekojs.api.stage.gate.EnchantGateBuilder;
import com.tkisor.nekojs.api.stage.gate.EntityGateBuilder;
import com.tkisor.nekojs.api.stage.gate.ItemGateBuilder;
import com.tkisor.nekojs.api.stage.gate.LootGateBuilder;
import com.tkisor.nekojs.api.stage.gate.RecipeGateBuilder;
import com.tkisor.nekojs.api.stage.gate.StageGateDeclaration;
import com.tkisor.nekojs.core.stage.gate.CompiledStageGateSnapshot;
import com.tkisor.nekojs.core.stage.gate.InlineRecipeStageRegistry;
import com.tkisor.nekojs.core.stage.gate.StageCandidatePlan;
import com.tkisor.nekojs.core.stage.gate.StageRegistryAccess;
import com.tkisor.nekojs.core.stage.gate.StageWireProtocol;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pure-common unit tests for {@link StageCandidatePlan} preflight validation and atomic commit,
 * plus {@link StageWireProtocol} full/delta/gate synchronization with {@link ClientStageMirror}.
 */
class StageCandidatePlanAndWireTest {

    private final List<ClientStageMirror> createdMirrors = new ArrayList<>();

    @AfterEach
    void tearDown() {
        for (ClientStageMirror mirror : createdMirrors) {
            StageWireProtocol.clearCachedSummary(mirror);
        }
        createdMirrors.clear();
    }

    private ClientStageMirror newIsolatedMirror() {
        ClientStageMirror mirror = new ClientStageMirror();
        createdMirrors.add(mirror);
        return mirror;
    }

    @Test
    void preflightRejectsDuplicatesAndUnknownRegistryIdsWithoutMutatingActiveState() {
        StageCustomizeEvent initialCustomize = new StageCustomizeEvent();
        initialCustomize.stage("baseline_stage").displayName("Baseline");
        StageStore store = new StageStore();
        store.setDefinitions(initialCustomize.buildDefinitions());

        CompiledStageGateSnapshot baselineSnapshot = CompiledStageGateSnapshot.EMPTY;
        AtomicReference<CompiledStageGateSnapshot> activeSnapshot = new AtomicReference<>(baselineSnapshot);
        InlineRecipeStageRegistry inlineRegistry = new InlineRecipeStageRegistry();

        StrictRegistryAccess strictRegistries = new StrictRegistryAccess(
                Set.of(NekoId.of("minecraft", "diamond")),
                Set.of(NekoId.of("minecraft", "obsidian")),
                Set.of(NekoId.of("minecraft", "chest")),
                Set.of(NekoId.of("minecraft", "horse")),
                Set.of(NekoId.of("minecraft", "strength")),
                Set.of(NekoId.of("minecraft", "sharpness"))
        );

        // 1. Duplicate StageDefinition names in StageCustomizeEvent and StageCandidatePlan
        StageCustomizeEvent dupCustomize = new StageCustomizeEvent();
        dupCustomize.stage("iron_age").displayName("First");
        dupCustomize.stage("iron_age").displayName("Second");
        IllegalArgumentException customizeDup = assertThrows(
                IllegalArgumentException.class,
                dupCustomize::buildDefinitions
        );
        assertTrue(customizeDup.getMessage().contains("[NEKO-4071]"), customizeDup.getMessage());

        StageDefinition defA = new StageCustomizeEvent().stage("dup_stage").displayName("A").toDefinition();
        StageDefinition defB = new StageCustomizeEvent().stage("dup_stage").displayName("B").toDefinition();
        StageCandidatePlan dupDefPlan = new StageCandidatePlan(
                List.of(defA, defB),
                List.of(),
                Set.of(),
                store,
                inlineRegistry,
                strictRegistries,
                activeSnapshot::set
        );
        assertPreflightRejectedLeavingBaselineIntact(dupDefPlan, store, activeSnapshot, baselineSnapshot);

        // 2. Duplicate StageGateDeclaration rule IDs in StableStageGatesEvent and StageCandidatePlan
        StableStageGatesEvent dupGatesEvent = new StableStageGatesEvent();
        dupGatesEvent.item(NekoId.of("test", "same_id"), "iron_age").item(NekoId.of("minecraft", "diamond"));
        dupGatesEvent.block(NekoId.of("test", "same_id"), "iron_age").block(NekoId.of("minecraft", "obsidian"));
        IllegalArgumentException eventDup = assertThrows(
                IllegalArgumentException.class,
                dupGatesEvent::buildDeclarations
        );
        assertTrue(eventDup.getMessage().contains("[NEKO-4071]"), eventDup.getMessage());

        StageGateDeclaration rule1 = new ItemGateBuilder(NekoId.of("test", "dup_rule"), "iron_age")
                .item(NekoId.of("minecraft", "diamond"))
                .buildDeclaration();
        StageGateDeclaration rule2 = new BlockGateBuilder(NekoId.of("test", "dup_rule"), "iron_age")
                .block(NekoId.of("minecraft", "obsidian"))
                .buildDeclaration();
        StageCandidatePlan dupRulePlan = new StageCandidatePlan(
                List.of(),
                List.of(rule1, rule2),
                Set.of(),
                store,
                inlineRegistry,
                strictRegistries,
                activeSnapshot::set
        );
        assertPreflightRejectedLeavingBaselineIntact(dupRulePlan, store, activeSnapshot, baselineSnapshot);

        // 3. Unknown explicit registry IDs across gate kinds and whitelistedContainers
        List<StageGateDeclaration> invalidDeclarations = List.of(
                new ItemGateBuilder(NekoId.of("test", "bad_item"), "s")
                        .item(NekoId.of("minecraft", "missing_item"))
                        .buildDeclaration(),
                new BlockGateBuilder(NekoId.of("test", "bad_block"), "s")
                        .block(NekoId.of("minecraft", "missing_block"))
                        .buildDeclaration(),
                new RecipeGateBuilder(NekoId.of("test", "bad_recipe_output"), "s")
                        .outputItem(NekoId.of("minecraft", "missing_output"))
                        .buildDeclaration(),
                new ContainerGateBuilder(NekoId.of("test", "bad_menu"), "s")
                        .menu(NekoId.of("minecraft", "missing_menu"))
                        .buildDeclaration(),
                new EntityGateBuilder(NekoId.of("test", "bad_entity"), "s")
                        .entity(NekoId.of("minecraft", "missing_entity"))
                        .buildDeclaration(),
                new EffectGateBuilder(NekoId.of("test", "bad_effect"), "s")
                        .effect(NekoId.of("minecraft", "missing_effect"))
                        .buildDeclaration(),
                new EnchantGateBuilder(NekoId.of("test", "bad_enchant"), "s")
                        .enchant(NekoId.of("minecraft", "missing_enchant"))
                        .buildDeclaration(),
                new LootGateBuilder(NekoId.of("test", "bad_loot_item"), "s")
                        .item(NekoId.of("minecraft", "missing_loot_item"))
                        .buildDeclaration(),
                new LootGateBuilder(NekoId.of("test", "bad_loot_replacement"), "s")
                        .item(NekoId.of("minecraft", "diamond"))
                        .replaceWith(NekoId.of("minecraft", "missing_replacement"))
                        .buildDeclaration(),
                new LootGateBuilder(NekoId.of("test", "bad_loot_entity"), "s")
                        .fromEntity(NekoId.of("minecraft", "missing_source_entity"))
                        .buildDeclaration()
        );

        for (StageGateDeclaration badDecl : invalidDeclarations) {
            StageCandidatePlan badTargetPlan = new StageCandidatePlan(
                    List.of(),
                    List.of(badDecl),
                    Set.of(),
                    store,
                    inlineRegistry,
                    strictRegistries,
                    activeSnapshot::set
            );
            assertPreflightRejectedLeavingBaselineIntact(badTargetPlan, store, activeSnapshot, baselineSnapshot);
        }

        StageCandidatePlan badWhitelistPlan = new StageCandidatePlan(
                List.of(),
                List.of(),
                Set.of(NekoId.of("minecraft", "unknown_chest_menu")),
                store,
                inlineRegistry,
                strictRegistries,
                activeSnapshot::set
        );
        assertPreflightRejectedLeavingBaselineIntact(badWhitelistPlan, store, activeSnapshot, baselineSnapshot);

        // 4. Collection-time failure poison
        StageCandidatePlan poisonedPlan = new StageCandidatePlan(
                store, inlineRegistry, strictRegistries, activeSnapshot::set);
        poisonedPlan.fail("script evaluation threw an exception", null);
        IllegalStateException poisonedError = assertThrows(IllegalStateException.class, poisonedPlan::preflight);
        assertTrue(poisonedError.getMessage().contains("[NEKO-4071]"), poisonedError.getMessage());
        assertEquals(Set.of("baseline_stage"), store.definitions().keySet());
        assertSame(baselineSnapshot, activeSnapshot.get());
    }

    private static void assertPreflightRejectedLeavingBaselineIntact(
            StageCandidatePlan plan,
            StageStore store,
            AtomicReference<CompiledStageGateSnapshot> activeSnapshot,
            CompiledStageGateSnapshot expectedSnapshot
    ) {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, plan::preflight);
        assertTrue(ex.getMessage().contains("[NEKO-4071]"), ex.getMessage());
        assertFalse(plan.isPublished());
        assertEquals(Set.of("baseline_stage"), store.definitions().keySet(),
                "Failed preflight must leave StageStore definitions untouched");
        assertSame(expectedSnapshot, activeSnapshot.get(),
                "Failed preflight must leave active CompiledStageGateSnapshot untouched");
    }

    @Test
    void publishAtomicallyInstallsDefinitionsAndCompiledSnapshotWhileDiscardLeavesStateUntouched() {
        StageStore store = new StageStore();
        InlineRecipeStageRegistry inlineRegistry = new InlineRecipeStageRegistry();
        NekoId inlineRecipeId = NekoId.of("modpack", "alloy_smelt");
        inlineRegistry.put(inlineRecipeId, "alloy_age", StageScope.PLAYER);

        AtomicReference<CompiledStageGateSnapshot> publishedSnapshot =
                new AtomicReference<>(CompiledStageGateSnapshot.EMPTY);

        StageCustomizeEvent customize = new StageCustomizeEvent();
        customize.stage("alloy_age").displayName("Alloy Age").showTitle(true);

        StableStageGatesEvent gates = new StableStageGatesEvent();
        NekoId chestMenu = NekoId.of("minecraft", "generic_9x3");
        NekoId alloyIngot = NekoId.of("modpack", "alloy_ingot");
        gates.whitelistContainer(chestMenu);
        gates.item(NekoId.of("test", "alloy_ingot_gate"), "alloy_age").item(alloyIngot);

        StageCandidatePlan discardedPlan = new StageCandidatePlan(
                customize.buildDefinitions(),
                gates.buildDeclarations(),
                gates.whitelistedContainers(),
                store,
                inlineRegistry,
                StageRegistryAccess.PERMISSIVE,
                publishedSnapshot::set
        );
        assertEquals(StageCandidatePlan.DOMAIN, discardedPlan.domain());
        discardedPlan.preflight();
        assertTrue(store.definitions().isEmpty(), "Preflight alone must not mutate StageStore");
        assertSame(CompiledStageGateSnapshot.EMPTY, publishedSnapshot.get());

        discardedPlan.discard();
        discardedPlan.publish();
        assertFalse(discardedPlan.isPublished());
        assertTrue(store.definitions().isEmpty());
        assertSame(CompiledStageGateSnapshot.EMPTY, publishedSnapshot.get());

        StageCandidatePlan livePlan = new StageCandidatePlan(
                customize.buildDefinitions(),
                gates.buildDeclarations(),
                gates.whitelistedContainers(),
                store,
                inlineRegistry,
                StageRegistryAccess.PERMISSIVE,
                publishedSnapshot::set
        );
        livePlan.preflight();
        livePlan.publish();

        assertTrue(livePlan.isPublished());
        StageDefinition installedDef = store.definition("alloy_age");
        assertNotNull(installedDef);
        assertEquals("Alloy Age", installedDef.displayName());
        assertTrue(installedDef.showTitle());

        CompiledStageGateSnapshot committed = publishedSnapshot.get();
        assertNotSame(CompiledStageGateSnapshot.EMPTY, committed);
        assertEquals(Set.of(chestMenu), committed.whitelistedContainers());
        assertFalse(committed.canUseItem(alloyIngot, StageHandle.EMPTY, StageHandle.EMPTY));
        assertFalse(committed.canAssembleRecipe(
                inlineRecipeId,
                null,
                null,
                StageHandle.EMPTY,
                StageHandle.EMPTY
        ));
    }

    @Test
    void wireProtocolSyncsFullDeltasAndGateSummariesUpdatingClientMirrorAndViewerListeners() {
        NekoId diamondSword = NekoId.of("minecraft", "diamond_sword");
        NekoId netherStar = NekoId.of("minecraft", "nether_star");
        NekoId swordRecipe = NekoId.of("minecraft", "diamond_sword_recipe");
        NekoId starAltarRecipe = NekoId.of("modpack", "star_altar");

        StableStageGatesEvent gates = new StableStageGatesEvent();
        gates.item(NekoId.of("test", "sword_gate"), "combat_2")
                .item(diamondSword)
                .scope(StageScope.PLAYER)
                .hideInViewer()
                .lockedName("Sealed Blade");
        gates.item(NekoId.of("test", "star_gate"), "ascension")
                .item(netherStar)
                .scope(StageScope.SERVER)
                .hideInViewer();
        gates.recipe(NekoId.of("test", "sword_recipe_gate"), "combat_2")
                .recipeId(swordRecipe)
                .scope(StageScope.PLAYER)
                .hideInViewer();

        InlineRecipeStageRegistry inlineRecipes = new InlineRecipeStageRegistry();
        inlineRecipes.put(starAltarRecipe, "ascension", StageScope.SERVER);

        CompiledStageGateSnapshot snapshot = CompiledStageGateSnapshot.compile(
                gates.buildDeclarations(),
                Set.of(),
                inlineRecipes.snapshot(),
                StageRegistryAccess.PERMISSIVE
        );

        ClientStageMirror mirror = newIsolatedMirror();
        mirror.setLocalPlayer("00000000-0000-0000-0000-0000000000b2", "Steve");

        AtomicInteger primaryRefreshes = new AtomicInteger();
        AtomicInteger extraRefreshes = new AtomicInteger();
        mirror.setViewerRefreshListener(primaryRefreshes::incrementAndGet);
        mirror.addViewerRefreshListener(extraRefreshes::incrementAndGet);

        // 1. Full sync with initial stages + compiled gate summary
        String fullJson = StageWireProtocol.encodeFullSync(
                Set.of("starter"),
                Map.of("temp_sprint", 2L),
                Set.of("world_boot"),
                Map.of(),
                snapshot
        );
        StageWireProtocol.applyOnClient(fullJson, mirror);

        assertTrue(mirror.playerView().has("starter"));
        assertEquals(2L, mirror.playerView().remainingTicks("temp_sprint"));
        assertTrue(mirror.serverView().has("world_boot"));
        assertEquals(Set.of(diamondSword, netherStar), mirror.hiddenItems());
        assertEquals(Set.of(swordRecipe, starAltarRecipe), mirror.hiddenRecipes());
        assertEquals("Sealed Blade", mirror.lockedItemName(diamondSword));
        assertEquals(List.of("combat_2"), mirror.requiredStagesForItem(diamondSword));
        assertTrue(primaryRefreshes.get() >= 1);
        assertEquals(primaryRefreshes.get(), extraRefreshes.get());

        // Client temporary timer floors at 1L until authoritative server remove arrives
        mirror.tickClient();
        assertEquals(1L, mirror.playerView().remainingTicks("temp_sprint"));
        mirror.tickClient();
        assertEquals(1L, mirror.playerView().remainingTicks("temp_sprint"));

        // 2. Player ADD delta unlocks combat_2 -> diamond_sword and swordRecipe unhide automatically!
        int beforeAddRefresh = primaryRefreshes.get();
        StageWireProtocol.applyOnClient(
                StageWireProtocol.encodeDelta("player", "add", "combat_2", 0L),
                mirror
        );
        assertTrue(mirror.playerView().has("combat_2"));
        assertFalse(mirror.isItemHidden(diamondSword));
        assertTrue(mirror.isItemHidden(netherStar));
        assertNull(mirror.lockedItemName(diamondSword));
        assertFalse(mirror.isRecipeHidden(swordRecipe));
        assertTrue(mirror.isRecipeHidden(starAltarRecipe));
        assertTrue(primaryRefreshes.get() > beforeAddRefresh,
                "Unlocking a gated stage via delta must fire viewer refresh listeners");

        // 3. Player REMOVE delta re-locks combat_2 -> diamond_sword and swordRecipe hide again!
        StageWireProtocol.applyOnClient(
                StageWireProtocol.encodeDelta("player", "remove", "combat_2", 0L),
                mirror
        );
        assertFalse(mirror.playerView().has("combat_2"));
        assertTrue(mirror.isItemHidden(diamondSword));
        assertEquals("Sealed Blade", mirror.lockedItemName(diamondSword));
        assertTrue(mirror.isRecipeHidden(swordRecipe));

        // 4. Player CLEAR and REPLACE deltas
        StageWireProtocol.applyOnClient(
                StageWireProtocol.encodeDelta("player", "clear", "", 0L),
                mirror
        );
        assertEquals(List.of(), mirror.playerView().getAll());

        StageWireProtocol.applyOnClient(
                StageWireProtocol.encodeDelta(
                        "player",
                        "replace",
                        "",
                        0L,
                        Set.of("combat_2"),
                        Map.of("temp_luck", 60L)
                ),
                mirror
        );
        assertTrue(mirror.playerView().has("combat_2"));
        assertEquals(60L, mirror.playerView().remainingTicks("temp_luck"));
        assertFalse(mirror.isItemHidden(diamondSword));
        assertFalse(mirror.isRecipeHidden(swordRecipe));

        // 5. Standalone encodeGateSummary + Server ADD / REMOVE / CLEAR / REPLACE deltas
        ClientStageMirror secondMirror = newIsolatedMirror();
        AtomicInteger secondRefresh = new AtomicInteger();
        secondMirror.setViewerRefreshListener(secondRefresh::incrementAndGet);

        StageWireProtocol.applyOnClient(StageWireProtocol.encodeGateSummary(snapshot), secondMirror);
        assertEquals(Set.of(diamondSword, netherStar), secondMirror.hiddenItems());
        assertEquals(Set.of(swordRecipe, starAltarRecipe), secondMirror.hiddenRecipes());
        assertEquals(1, secondRefresh.get());

        StageWireProtocol.applyOnClient(
                StageWireProtocol.encodeDelta("server", "add", "ascension", 0L),
                secondMirror
        );
        assertTrue(secondMirror.serverView().has("ascension"));
        assertEquals(Set.of(diamondSword), secondMirror.hiddenItems());
        assertEquals(Set.of(swordRecipe), secondMirror.hiddenRecipes());
        assertTrue(secondRefresh.get() >= 2);

        StageWireProtocol.applyOnClient(
                StageWireProtocol.encodeDelta("server", "remove", "ascension", 0L),
                secondMirror
        );
        assertTrue(secondMirror.isItemHidden(netherStar));
        assertTrue(secondMirror.isRecipeHidden(starAltarRecipe));

        StageWireProtocol.applyOnClient(
                StageWireProtocol.encodeDelta(
                        "server",
                        "replace",
                        "",
                        0L,
                        Set.of("ascension"),
                        Map.of("server_festival", 200L)
                ),
                secondMirror
        );
        assertTrue(secondMirror.serverView().has("ascension"));
        assertEquals(200L, secondMirror.serverView().remainingTicks("server_festival"));
        assertFalse(secondMirror.isItemHidden(netherStar));

        StageWireProtocol.applyOnClient(
                StageWireProtocol.encodeDelta("server", "clear", "", 0L),
                secondMirror
        );
        assertEquals(List.of(), secondMirror.serverView().getAll());
        assertTrue(secondMirror.isItemHidden(netherStar));

        // Malformed wire payloads fail with [NEKO-4072]
        IllegalArgumentException badJson = assertThrows(
                IllegalArgumentException.class,
                () -> StageWireProtocol.applyOnClient("{invalid", secondMirror)
        );
        assertTrue(badJson.getMessage().contains("[NEKO-4072]"), badJson.getMessage());
    }

    private record StrictRegistryAccess(
            Set<NekoId> items,
            Set<NekoId> blocks,
            Set<NekoId> menus,
            Set<NekoId> entities,
            Set<NekoId> effects,
            Set<NekoId> enchantments
    ) implements StageRegistryAccess {

        @Override
        public boolean itemExists(NekoId id) {
            return items.contains(id);
        }

        @Override
        public boolean blockExists(NekoId id) {
            return blocks.contains(id);
        }

        @Override
        public boolean menuExists(NekoId id) {
            return menus.contains(id);
        }

        @Override
        public boolean entityTypeExists(NekoId id) {
            return entities.contains(id);
        }

        @Override
        public boolean effectExists(NekoId id) {
            return effects.contains(id);
        }

        @Override
        public boolean enchantmentExists(NekoId id) {
            return enchantments.contains(id);
        }

        @Override
        public Set<NekoId> expandItemTag(NekoId tagId) {
            return Set.of();
        }

        @Override
        public Set<NekoId> expandBlockTag(NekoId tagId) {
            return Set.of();
        }

        @Override
        public Set<NekoId> expandEntityTypeTag(NekoId tagId) {
            return Set.of();
        }

        @Override
        public Set<NekoId> itemsInNamespace(String namespace) {
            return Set.of();
        }

        @Override
        public Set<NekoId> blocksInNamespace(String namespace) {
            return Set.of();
        }

        @Override
        public Set<NekoId> entitiesInNamespace(String namespace) {
            return Set.of();
        }
    }
}
