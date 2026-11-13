package com.tkisor.nekojs.wrapper.event.server;

import com.tkisor.nekojs.api.ScriptType;
import com.tkisor.nekojs.core.lifecycle.NekoReloadException;
import com.tkisor.nekojs.core.lifecycle.ReloadPhase;
import com.tkisor.nekojs.testfixture.VanillaRegistryProbe;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * End-to-end fixture for the item half of the modification flow. All five nodes share this
 * code, and the test runs only where a vanilla registry is available. A real script collects
 * declarations through the {@code ItemEvents.modification} event surface, and the platform
 * {@link ModificationDomainOwner} Adapter applies them to the real {@code Item} at commit, so
 * the assertions read platform-observable results instead of a private static Map.
 *
 * <p>Covers: the collection point of the initial generation (about-to-start), candidate-phase
 * inertness (live components keep the old active value until commit), a successful reload
 * applying the new complete plan, removing a declaration restoring the NekoJS baseline
 * (RESTORED), a pre-check rejection keeping the whole batch on the old active value
 * (STATE_PLAN), a collection error failing the whole batch (DOMAIN_PLAN), and property writes
 * and explicit setters producing equivalent results from the script side.
 *
 * <p>Environment gate: needs a vanilla registry ({@link VanillaRegistryProbe}). A bare JVM
 * reports the test as skipped; ModDev and dev environments run it for real.
 */
class Ticket39ModificationScriptE2ETest {

    private Ticket39ModificationScriptHarness harness;

    @BeforeAll
    static void initPlatform() {
        Ticket39ModificationScriptHarness.ensurePlatformInitialized();
    }

    @BeforeEach
    void setUp() throws Exception {
        Assumptions.assumeTrue(VanillaRegistryProbe.available(),
                "vanilla registries unavailable in this JVM (no FML loader?)");
        Ticket39ModificationScriptHarness.clearServerScripts();
        harness = new Ticket39ModificationScriptHarness(Map.of());
    }

    @AfterEach
    void tearDown() throws Exception {
        if (harness != null) {
            harness.close();
            harness = null;
        }
        Ticket39ModificationScriptHarness.clearServerScripts();
    }

    private static int maxStackSizeOfDiamond() {
        return Items.DIAMOND.components().getOrDefault(DataComponents.MAX_STACK_SIZE, 1);
    }

    private void loadServerScripts() {
        harness.root.scriptManagerOf(ScriptType.SERVER).loadScripts();
    }

    @Test
    void startupCollectionPointAppliesScriptDeclarationOnceForTheInitialGeneration() throws Exception {
        assertTrue(VanillaRegistryProbe.available());
        harness.writeServerScript("mod.js", """
                ItemEvents.modification(event => {
                  event.modify('minecraft:diamond', item => {
                    item.maxStackSize = 16
                  })
                })
                """);
        // The initial load is not transactional: it registers the active listeners only and collects
        // nothing. The platform triggers collection later.
        loadServerScripts();
        assertEquals(64, maxStackSizeOfDiamond(), "initial non-transactional load must not apply modifications");

        harness.applyInitialPlan();

        assertEquals(16, maxStackSizeOfDiamond(), "startup collection point applies the collected plan");
        assertEquals(ModificationDomainOwner.Outcome.APPLIED, harness.owner.lastDiagnostics().outcome());
        assertEquals("startup", harness.owner.lastDiagnostics().source());
    }

    @Test
    void candidateStaysInertUntilCommitAndReloadAppliesTheNewPlan() throws Exception {
        // Run one successful commit first, so the active plan sets maxStackSize to 16.
        harness.writeServerScript("mod.js", """
                ItemEvents.modification(event => event.modify('minecraft:diamond', item => item.setMaxStackSize(16)))
                """);
        loadServerScripts();
        harness.applyInitialPlan();
        assertEquals(16, maxStackSizeOfDiamond());

        // Second round: during candidate construction (after domain collection, before commit)
        // the live component must still hold the old active value. The probe collector registers
        // after the real owner, so it sees the live value with this round's declarations already
        // collected but not yet applied.
        List<Integer> seenDuringCandidate = new ArrayList<>();
        harness.root.registerDomainCollector(new com.tkisor.nekojs.core.lifecycle.CandidateDomainCollector() {
            @Override public String domain() { return "e2e-probe"; }
            @Override public ScriptType scriptType() { return ScriptType.SERVER; }
            @Override public void collect(Handle handle) {
                seenDuringCandidate.add(maxStackSizeOfDiamond());
            }
        });
        harness.writeServerScript("mod.js", """
                ItemEvents.modification(event => event.modify('minecraft:diamond', item => item.setMaxStackSize(32)))
                """);

        harness.root.reload(ScriptType.SERVER);

        assertEquals(List.of(16), seenDuringCandidate,
                "candidate collection must not touch the live item components (inert until commit)");
        assertEquals(32, maxStackSizeOfDiamond(), "commit applies the new plan");
        assertEquals(ModificationDomainOwner.Outcome.APPLIED, harness.owner.lastDiagnostics().outcome());
    }

    @Test
    void removedDeclarationRestoresNekoJsBaselineInsteadOfStayingStale() throws Exception {
        harness.writeServerScript("mod.js", """
                ItemEvents.modification(event => event.modify('minecraft:diamond', item => item.setMaxStackSize(16)))
                """);
        loadServerScripts();
        harness.applyInitialPlan();
        assertEquals(16, maxStackSizeOfDiamond());

        // The script declares nothing now: a successful reload first restores the baseline NekoJS
        // holds, then applies the new complete plan, which is empty.
        harness.writeServerScript("mod.js", "global.noModification = true\n");
        harness.root.reload(ScriptType.SERVER);

        assertEquals(64, maxStackSizeOfDiamond(),
                "removed declaration restores the NekoJS-held baseline (no silent stale)");
        assertEquals(ModificationDomainOwner.Outcome.RESTORED, harness.owner.lastDiagnostics().outcome(),
                "diagnostics distinguish restored from applied");
    }

    @Test
    void rejectedBatchKeepsOldActivePlanWithoutPartialApply() throws Exception {
        harness.writeServerScript("mod.js", """
                ItemEvents.modification(event => event.modify('minecraft:diamond', item => item.setMaxStackSize(16)))
                """);
        loadServerScripts();
        harness.applyInitialPlan();
        assertEquals(16, maxStackSizeOfDiamond());
        ModificationDomainOwner.Diagnostics before = harness.owner.lastDiagnostics();

        // The pre-check rejects a value outside 1..99: the whole batch is not committed and the
        // old active value keeps serving.
        harness.writeServerScript("mod.js", """
                ItemEvents.modification(event => {
                  event.modify('minecraft:diamond', item => item.setMaxStackSize(500))
                })
                """);
        NekoReloadException failure = assertThrows(NekoReloadException.class,
                () -> harness.root.reload(ScriptType.SERVER));

        assertEquals(ReloadPhase.STATE_PLAN, failure.report().phase(),
                "range validation joins the joint preflight: " + failure.report());
        assertEquals(16, maxStackSizeOfDiamond(), "old active plan keeps serving after a blocked batch");
        assertEquals(before, harness.owner.lastDiagnostics(),
                "a blocked batch leaves the last applied generation untouched (no partial apply)");
    }

    @Test
    void collectionErrorFailsWholeBatchAndNextGoodReloadStillCommits() throws Exception {
        harness.writeServerScript("mod.js", """
                ItemEvents.modification(event => event.modify('minecraft:diamond', item => item.setMaxStackSize(16)))
                """);
        loadServerScripts();
        harness.applyInitialPlan();
        assertEquals(16, maxStackSizeOfDiamond());

        harness.writeServerScript("mod.js", """
                ItemEvents.modification(event => {
                  event.modify('minecraft:diamond', item => item.setMaxStackSize(32))
                  event.modify('minecraft:no_such_item', item => item.setMaxStackSize(1))
                })
                """);
        NekoReloadException failure = assertThrows(NekoReloadException.class,
                () -> harness.root.reload(ScriptType.SERVER));

        assertEquals(ReloadPhase.DOMAIN_PLAN, failure.report().phase(),
                "collection-time error fails the candidate in the collection phase: " + failure.report());
        assertTrue(failure.report().domain().startsWith("domain-collect:" + ModificationDomainOwner.DOMAIN)
                        || failure.report().domain().contains(ModificationDomainOwner.DOMAIN),
                "domain=" + failure.report().domain());
        assertEquals(16, maxStackSizeOfDiamond(),
                "neither declaration of the failed batch is applied (whole batch fails, old active kept)");

        // Candidate resources are dropped with the failure, so the next ordinary reload still
        // commits.
        harness.writeServerScript("mod.js", """
                ItemEvents.modification(event => event.modify('minecraft:diamond', item => item.setMaxStackSize(48)))
                """);
        harness.root.reload(ScriptType.SERVER);
        assertEquals(48, maxStackSizeOfDiamond(), "a later candidate commits normally (no pending listener leak)");
    }

    @Test
    void propertyWriteAndExplicitSetterScriptsProduceTheSameAppliedResult() throws Exception {
        harness.writeServerScript("mod.js", """
                ItemEvents.modification(event => {
                  event.modify('minecraft:diamond', item => {
                    item.maxStackSize = 24
                    item.rarity = 'epic'
                  })
                })
                """);
        loadServerScripts();
        harness.applyInitialPlan();
        int viaProperty = maxStackSizeOfDiamond();
        String viaPropertyRarity = Items.DIAMOND.components()
                .getOrDefault(DataComponents.RARITY, net.minecraft.world.item.Rarity.COMMON).getSerializedName();

        // Reset to the baseline, then replay the same modifications through explicit setters.
        harness.close();
        harness = new Ticket39ModificationScriptHarness(Map.of());
        harness.writeServerScript("mod.js", """
                ItemEvents.modification(event => {
                  event.modify('minecraft:diamond', item => {
                    item.setMaxStackSize(24)
                    item.setRarity('epic')
                  })
                })
                """);
        loadServerScripts();
        harness.applyInitialPlan();
        int viaSetter = maxStackSizeOfDiamond();
        String viaSetterRarity = Items.DIAMOND.components()
                .getOrDefault(DataComponents.RARITY, net.minecraft.world.item.Rarity.COMMON).getSerializedName();

        assertEquals(24, viaProperty, "property write form applies through the same setter");
        assertEquals(24, viaSetter, "explicit setter form applies through the same setter");
        assertEquals(viaSetterRarity, viaPropertyRarity,
                "both script forms must produce the same normalized declaration (same Adapter result)");
        assertEquals("epic", viaPropertyRarity);
    }
}
