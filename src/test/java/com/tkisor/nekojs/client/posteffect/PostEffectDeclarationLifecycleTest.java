//? if neoforge {
package com.tkisor.nekojs.client.posteffect;

import com.tkisor.nekojs.api.ScriptType;
import com.tkisor.nekojs.core.lifecycle.NekoReloadException;
import com.tkisor.nekojs.core.lifecycle.ReloadPhase;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * End-to-end fixture for post-effect declarations, shared by all five nodes. Real CLIENT scripts
 * declare register/unregister through {@code ClientEvents.postEffects}; declarations stay inert while
 * the candidate is built, and at the commit point the platform {@link PostEffectDomainOwner} adapter
 * installs the new generation and retires the old one.
 *
 * <p>Covers: live definitions stay unchanged while the candidate is built, a committed generation can
 * be read back, declarations of the old generation are retired and no longer visible, an invalid
 * declaration JSON fails the whole batch and keeps the old active state, an explicit unregister and a
 * removed declaration both take effect, generation and stale queries stay read-only, and the runtime
 * binding is not replaced by the declaration event.
 *
 * <p>Assertions only use public entry points: scripts, reload success or failure,
 * {@code owner.lastDiagnostics()} and the read-only queries of {@code PostEffectManager}. They never
 * assert on a private static map.
 */
class PostEffectDeclarationLifecycleTest {

    private PostEffectDeclarationHarness harness;

    @BeforeAll
    static void initPlatform() {
        PostEffectDeclarationHarness.ensurePlatformInitialized();
    }

    @BeforeEach
    void setUp() throws Exception {
        PostEffectDeclarationHarness.clearClientScripts();
        PostEffectDeclarationHarness.resetDeclarations();
        harness = new PostEffectDeclarationHarness();
    }

    @AfterEach
    void tearDown() throws Exception {
        if (harness != null) {
            harness.close();
            harness = null;
        }
        PostEffectDeclarationHarness.resetDeclarations();
        PostEffectDeclarationHarness.clearClientScripts();
    }

    /**
     * A chain JSON payload valid for the running node: 26.x uses the modern
     * {@code PostChainConfig} shape (bare shader ids), 1.21.1 uses the legacy
     * {@code shaders/post} shape (program names). Both come from the production generator
     * ({@code PostEffectChainJson}), so the fixture exercises the real payload contract.
     */
    private static String chainJson(String shaderOrProgram) {
//? if >=26 {
        return com.tkisor.nekojs.core.posteffect.PostEffectChainJson
                .simpleBlitChainModern(shaderOrProgram, null);
//?} else {
        /*return com.tkisor.nekojs.core.posteffect.PostEffectChainJson
                .simpleBlitChainLegacy(shaderOrProgram);
*///?}
    }

    @Test
    void declaredEffectsInstallOnlyAtTheCommitPoint() throws Exception {
        harness.writeClientScript("fx.js", """
                ClientEvents.postEffects(event => event.register('nekojs:gray', { chainJson: '%s' }))
                """.formatted(chainJson("minecraft:post/invert")));

        harness.loadAndApplyInitialPlan();

        assertEquals(List.of("nekojs:gray"), PostEffectDeclarationHarness.installedIds(),
                "the initial generation collection point installs the declared definition");
        assertEquals(PostEffectDomainOwner.Outcome.APPLIED, harness.owner.lastDiagnostics().outcome());
        long installed = harness.owner.activeGeneration();
        assertEquals(installed, PostEffectManager.activeGeneration(),
                "the runtime binding's generation query reports the installed generation");
        assertTrue(harness.owner.hasActiveDefinition(Identifier.parse("nekojs:gray")));
    }

    @Test
    void candidateCollectionStaysInertUntilCommitThenSwapsTheGeneration() throws Exception {
        harness.writeClientScript("fx.js", """
                ClientEvents.postEffects(event => event.register('nekojs:gray', { chainJson: '%s' }))
                """.formatted(chainJson("minecraft:post/invert")));
        harness.loadAndApplyInitialPlan();
        assertEquals(List.of("nekojs:gray"), PostEffectDeclarationHarness.installedIds());
        long beforeReload = harness.owner.activeGeneration();

        // While the candidate is built (DOMAIN_PLAN collected, not yet committed) the live definitions
        // must still be the old generation.
        // The probe collector is registered after the real owner, so it sees the live state where this
        // round's declarations are collected but not yet installed.
        List<List<String>> seenDuringCandidate = new ArrayList<>();
        harness.root.registerDomainCollector(new com.tkisor.nekojs.core.lifecycle.CandidateDomainCollector() {
            @Override public String domain() { return "post-effect-e2e-probe"; }
            @Override public ScriptType scriptType() { return ScriptType.CLIENT; }
            @Override public void collect(Handle handle) {
                seenDuringCandidate.add(PostEffectDeclarationHarness.installedIds());
            }
        });

        harness.writeClientScript("fx.js", """
                ClientEvents.postEffects(event => event.register('nekojs:sepia', { chainJson: '%s' }))
                """.formatted(chainJson("minecraft:post/blur")));
        harness.reloadClientScripts();

        assertEquals(List.of(List.of("nekojs:gray")), seenDuringCandidate,
                "candidate collection must not touch the installed definitions (inert until commit)");
        assertEquals(List.of("nekojs:sepia"), PostEffectDeclarationHarness.installedIds(),
                "the commit point installs the new generation and retires the previous one");
        assertEquals(beforeReload + 1L, harness.owner.activeGeneration(),
                "the committed generation advances exactly once per successful batch");
        assertEquals(beforeReload + 1L, PostEffectManager.activeGeneration());
        assertFalse(harness.owner.hasActiveDefinition(Identifier.parse("nekojs:gray")),
                "the previous generation's declaration is no longer active");
        assertTrue(harness.owner.hasActiveDefinition(Identifier.parse("nekojs:sepia")));
        assertEquals(1, harness.owner.lastDiagnostics().retired(),
                "the release order is observable: exactly one previous definition retired");
    }

    @Test
    void invalidChainJsonFailsTheWholeBatchAndKeepsTheOldActiveGeneration() throws Exception {
        harness.writeClientScript("fx.js", """
                ClientEvents.postEffects(event => event.register('nekojs:gray', { chainJson: '%s' }))
                """.formatted(chainJson("minecraft:post/invert")));
        harness.loadAndApplyInitialPlan();
        assertEquals(List.of("nekojs:gray"), PostEffectDeclarationHarness.installedIds());
        long generationBefore = harness.owner.activeGeneration();

        // The candidate JSON is invalid, so the STATE_PLAN preflight rejects the whole batch and the old
        // generation stays usable.
        harness.writeClientScript("fx.js", """
                ClientEvents.postEffects(event => {
                  event.register('nekojs:broken', { chainJson: '{not json' })
                })
                """);
        NekoReloadException failure = assertThrows(NekoReloadException.class, harness::reloadClientScripts);

        assertEquals(ReloadPhase.STATE_PLAN, failure.report().phase(),
                "invalid chain JSON joins the joint preflight: " + failure.report());
        assertEquals(List.of("nekojs:gray"), PostEffectDeclarationHarness.installedIds(),
                "the previous active generation keeps serving after a blocked batch");
        assertEquals(generationBefore, harness.owner.activeGeneration(),
                "a blocked batch does not advance the active declaration generation");
        assertEquals(PostEffectDomainOwner.Outcome.APPLIED, harness.owner.lastDiagnostics().outcome(),
                "a blocked batch leaves the last applied outcome untouched");
    }

    @Test
    void collectionErrorFailsTheWholeBatchInDomainPlanAndALaterCandidateStillCommits() throws Exception {
        harness.writeClientScript("fx.js", """
                ClientEvents.postEffects(event => event.register('nekojs:gray', { chainJson: '%s' }))
                """.formatted(chainJson("minecraft:post/invert")));
        harness.loadAndApplyInitialPlan();

        harness.writeClientScript("fx.js", """
                ClientEvents.postEffects(event => {
                  event.register('nekojs:sepia', { chainJson: '%s' })
                  event.register('nekojs:bad id', { chainJson: '{}' })
                })
                """.formatted(chainJson("minecraft:post/blur")));
        NekoReloadException failure = assertThrows(NekoReloadException.class, harness::reloadClientScripts);

        assertEquals(ReloadPhase.STATE_PLAN, failure.report().phase(),
                "a collection error poisons the batch and is rejected at joint preflight: " + failure.report());
        assertTrue(failure.report().domain() != null && failure.report().domain().contains("post-effects"),
                "domain=" + failure.report().domain());
        assertEquals(List.of("nekojs:gray"), PostEffectDeclarationHarness.installedIds(),
                "neither declaration of the failed batch is installed (whole batch fails)");

        // The candidate resources are dropped with the failure, so the next normal reload can still
        // commit.
        harness.writeClientScript("fx.js", """
                ClientEvents.postEffects(event => event.register('nekojs:sepia', { chainJson: '%s' }))
                """.formatted(chainJson("minecraft:post/blur")));
        harness.reloadClientScripts();
        assertEquals(List.of("nekojs:sepia"), PostEffectDeclarationHarness.installedIds(),
                "a later candidate commits normally (no pending listener leak)");
    }

    @Test
    void removedDeclarationRetiresInsteadOfStayingStale() throws Exception {
        harness.writeClientScript("fx.js", """
                ClientEvents.postEffects(event => event.register('nekojs:gray', { chainJson: '%s' }))
                """.formatted(chainJson("minecraft:post/invert")));
        harness.loadAndApplyInitialPlan();
        assertTrue(harness.owner.hasActiveDefinition(Identifier.parse("nekojs:gray")));

        // The script no longer declares anything, so a successful reload retires the old id and it is no
        // longer visible to this generation.
        harness.writeClientScript("fx.js", "global.noEffects = true\n");
        harness.reloadClientScripts();

        assertTrue(PostEffectDeclarationHarness.installedIds().isEmpty(),
                "an undeclared id is retired instead of staying silently stale");
        assertFalse(harness.owner.hasActiveDefinition(Identifier.parse("nekojs:gray")));
        assertEquals(1, harness.owner.lastDiagnostics().retired());
    }

    @Test
    void explicitUnregisterRetiresAnIdDeclaredEarlierInTheSameBatch() throws Exception {
        harness.writeClientScript("fx.js", """
                ClientEvents.postEffects(event => {
                  event.register('nekojs:gray', { chainJson: '%s' })
                  event.register('nekojs:sepia', { chainJson: '%s' })
                })
                """.formatted(chainJson("minecraft:post/invert"), chainJson("minecraft:post/blur")));
        harness.loadAndApplyInitialPlan();
        assertEquals(List.of("nekojs:gray", "nekojs:sepia"), PostEffectDeclarationHarness.installedIds());

        harness.writeClientScript("fx.js", """
                ClientEvents.postEffects(event => {
                  event.register('nekojs:gray', { chainJson: '%s' })
                  event.unregister('nekojs:sepia')
                })
                """.formatted(chainJson("minecraft:post/invert")));
        harness.reloadClientScripts();

        assertEquals(List.of("nekojs:gray"), PostEffectDeclarationHarness.installedIds(),
                "unregister retires exactly the id it names");
        assertFalse(harness.owner.hasActiveDefinition(Identifier.parse("nekojs:sepia")));
    }

    @Test
    void generationAndStaleQueriesReportTheActiveDeclarationGeneration() throws Exception {
        // With no declarations the query reports an empty generation (no id) instead of expressing
        // "nothing declared" as a silent no-op.
        harness.writeClientScript("fx.js", "global.idle = true\n");
        harness.loadAndApplyInitialPlan();

        assertTrue(PostEffectDeclarationHarness.installedIds().isEmpty(),
                "an empty declaration batch installs no definition");
        assertFalse(harness.owner.hasActiveDefinition(Identifier.parse("nekojs:gray")),
                "an undeclared id is never reported as active");
        assertEquals(harness.owner.activeGeneration(), PostEffectManager.activeGeneration());
        long emptyBatchGeneration = harness.owner.activeGeneration();

        harness.writeClientScript("fx.js", """
                ClientEvents.postEffects(event => event.register('nekojs:gray', { chainJson: '%s' }))
                """.formatted(chainJson("minecraft:post/invert")));
        harness.reloadClientScripts();

        assertEquals(emptyBatchGeneration + 1L, harness.owner.activeGeneration(),
                "the query face advances exactly once with the committed generation");
        assertEquals(harness.owner.activeGeneration(), PostEffectManager.activeGeneration());
        assertTrue(harness.owner.hasActiveDefinition(Identifier.parse("nekojs:gray")));
        assertEquals(List.of("nekojs:gray"), PostEffectDeclarationHarness.installedIds(),
                "the read-only declaration query exposes the committed id");
        assertTrue(new PostEffectsJS().hasDefinition("nekojs:gray"),
                "the runtime binding's declaration query agrees with the owner (no stale read)");
    }

    @Test
    void aCandidatePreflightThatPassesDoesNotPublishAnObservation() throws Exception {
        // A passing preflight in this domain does not mean this domain committed: the whole batch can
        // still be dropped by another domain, by STATE_PLAN, or by a close. lastDiagnostics is the
        // public read of what the active generation is now, so nothing may write it while the candidate
        // is built.
        harness.writeClientScript("fx.js", """
                ClientEvents.postEffects(event => event.register('nekojs:gray', { chainJson: '%s' }))
                """.formatted(chainJson("minecraft:post/invert")));
        harness.loadAndApplyInitialPlan();
        PostEffectDomainOwner.Diagnostics before = harness.owner.lastDiagnostics();
        assertEquals(PostEffectDomainOwner.Outcome.APPLIED, before.outcome());

        // Another domain fails the STATE_PLAN preflight: this domain passes its own preflight first, but
        // the batch is not committed.
        harness.root.registerDomainCollector(new com.tkisor.nekojs.core.lifecycle.CandidateDomainCollector() {
            @Override public String domain() { return "post-effect-failing-peer"; }
            @Override public ScriptType scriptType() { return ScriptType.CLIENT; }
            @Override public void collect(Handle handle) {
                handle.registerPlan(new com.tkisor.nekojs.core.state.CandidateStatePlan() {
                    @Override public String domain() { return "post-effect-failing-peer"; }
                    @Override public void preflight() { throw new IllegalStateException("peer domain rejected"); }
                    @Override public void publish() { }
                });
            }
        });
        harness.writeClientScript("fx.js", """
                ClientEvents.postEffects(event => event.register('nekojs:sepia', { chainJson: '%s' }))
                """.formatted(chainJson("minecraft:post/blur")));
        assertThrows(NekoReloadException.class, harness::reloadClientScripts);

        assertEquals(before, harness.owner.lastDiagnostics(),
                "a candidate that never committed must not publish an observation (no PREFLIGHT_OK leak)");
        assertEquals(List.of("nekojs:gray"), PostEffectDeclarationHarness.installedIds(),
                "the old active generation keeps serving after the discarded candidate");
    }

    @Test
    void reloadTeardownDoesNotTouchTheRuntimePicture() throws Exception {
        // close(ScriptType) does not clear the screen. The declaration registry is owned by the
        // generation lifecycle, and runtime screen state is not something a binding cleans up.
        //
        // The assertion here is that the binding does NOT override close: the only cleanup path is the
        // default no-op of Binding, so reload teardown cannot clear the screen as a visible side effect.
        // The render state itself needs a real client, so a headless JVM cannot assert it.
        java.lang.reflect.Method close;
        try {
            close = PostEffectsJS.class.getDeclaredMethod("close", ScriptType.class);
        } catch (NoSuchMethodException absent) {
            close = null;
        }
        assertEquals(null, close,
                "the runtime binding must not override close: reload teardown must not clear the"
                        + " picture (declarations are owned by the generation lifecycle)");
    }

    @Test
    void runtimeBindingMembersStayAvailableAndAreNotReplacedByTheDeclarationEvent() {
        // set/clear/toggle/current are still runtime binding members; the declaration event does not
        // provide them. The members are read by reflection rather than asserted against a literal set,
        // which would always pass and prove nothing.
        Set<String> bindingMembers = new java.util.HashSet<>();
        for (var method : PostEffectsJS.class.getMethods()) {
            bindingMembers.add(method.getName());
        }
        assertTrue(bindingMembers.containsAll(Set.of("set", "clear", "toggle", "current", "isActive")),
                "the runtime binding lost caller-visible members: " + bindingMembers);
        for (var method : PostEffectsJS.class.getMethods()) {
            if (method.getDeclaringClass() == Object.class) continue;
            assertFalse(Set.of("register", "unregister").contains(method.getName()),
                    "the declaration entry points must not stay on the runtime binding: " + method.getName());
        }
        for (var method : PostEffectEventJS.class.getMethods()) {
            assertFalse(Set.of("set", "clear", "toggle", "current").contains(method.getName()),
                    "runtime actions must not move onto the declaration event: " + method.getName());
        }
    }
}
//?}
