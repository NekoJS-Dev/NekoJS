//? if neoforge {
package com.tkisor.nekojs.bindings.event;

import com.tkisor.nekojs.api.ScriptType;
import com.tkisor.nekojs.core.lifecycle.CandidateDomainCollector;
import com.tkisor.nekojs.core.lifecycle.NekoReloadException;
import com.tkisor.nekojs.core.state.CandidateStatePlan;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Ticket 24 reload cleanup fixture (NeoForge side, real Graal pipeline): scripts register
 * listeners on the real family buses, and after repeated SERVER reloads — no duplicate
 * dispatch, old generations no longer receive callbacks, cancellation results follow the
 * active generation, and a failed candidate keeps the old active with no half-cleaned
 * state. This is the primary evidence for ticket 24 AC4; bus-level concurrency stress
 * lives in common's {@code EventBusJSExternalBehaviorStressTest}.
 *
 * <p>The probe is a {@code List} (same as the ticket 26 harness): the script callback does
 * {@code event.add(tag)}, and the Java side posts a fresh List and reads back what was
 * "actually invoked" — assertions depend on no private registry or callback object
 * identity. The synthetic payload carries only the probe (a headless JVM has no real MC
 * event instances; the native event → payload conversion and the source trace of platform
 * callbacks are in {@code Ticket24GameplayEventPhaseTraceTest}).
 *
 * <p>Thread/timing semantics: this harness runs load/reload/post serially on the test
 * thread (matching the production owner-thread model — SERVER dispatch and reload both
 * happen on the server thread); cross-thread re-entrancy is backstopped by Graal's
 * single-thread constraint (already frozen by ticket 07's {@code SyncEvalWatchdogTest}),
 * not repeated here.
 */
class Ticket24GameplayEventReloadLifecycleTest {

    private Ticket24GameplayEventReloadHarness harness;

    @BeforeAll
    static void initPlatform() {
        Ticket24GameplayEventReloadHarness.ensurePlatformInitialized();
    }

    @BeforeEach
    void setUp() throws Exception {
        Ticket24GameplayEventReloadHarness.clearScripts(ScriptType.SERVER);
        Ticket24GameplayEventReloadHarness.clearScripts(ScriptType.STARTUP);
        harness = new Ticket24GameplayEventReloadHarness();
    }

    @AfterEach
    void tearDown() throws Exception {
        if (harness != null) {
            harness.close();
            harness = null;
        }
        Ticket24GameplayEventReloadHarness.clearScripts(ScriptType.SERVER);
        Ticket24GameplayEventReloadHarness.clearScripts(ScriptType.STARTUP);
    }

    /** Posts a probe List to the given bus (no key targeting: script listeners are all global listeners). */
    private static List<String> post(Object bus) {
        List<String> probe = new ArrayList<>();
        @SuppressWarnings("unchecked")
        var eventBus = (com.tkisor.nekojs.api.event.EventBusJS<List<String>, ?>) bus;
        eventBus.post(probe);
        return probe;
    }

    private static List<String> postCommand() {
        List<String> probe = new ArrayList<>();
        @SuppressWarnings("unchecked")
        var bus = (com.tkisor.nekojs.api.event.EventBusJS<List<String>, ?>) (Object) CommandEvents.COMMAND;
        bus.post(probe);
        return probe;
    }

    @Test
    void multipleSuccessfulReloadsDispatchEachGenerationExactlyOnce() throws Exception {
        harness.writeScript(ScriptType.SERVER, "lifecycle.js", """
                PlayerEvents.loggedIn(event => event.add('gen1'))
                """);
        harness.loadScripts(ScriptType.SERVER);
        assertEquals(List.of("gen1"), post(PlayerEvents.LOGGED_IN));

        harness.writeScript(ScriptType.SERVER, "lifecycle.js", """
                PlayerEvents.loggedIn(event => event.add('gen2'))
                """);
        harness.reloadScripts(ScriptType.SERVER);
        assertEquals(List.of("gen2"), post(PlayerEvents.LOGGED_IN),
                "after a successful reload the new generation runs exactly once"
                        + " and the old generation's listener is gone (no duplicate dispatch)");

        harness.writeScript(ScriptType.SERVER, "lifecycle.js", """
                PlayerEvents.loggedIn(event => event.add('gen3'))
                LevelEvents.loaded(event => event.add('level-gen3'))
                """);
        harness.reloadScripts(ScriptType.SERVER);
        assertEquals(List.of("gen3"), post(PlayerEvents.LOGGED_IN));
        assertEquals(List.of("level-gen3"), post(LevelEvents.LOADED),
                "a reload that adds listeners to another family swaps both families together");

        // Whole-declaration removal: the next round declares no listeners → post no longer dispatches (cleanup is not "keep the last round")
        harness.writeScript(ScriptType.SERVER, "lifecycle.js", "// no listeners\n");
        harness.reloadScripts(ScriptType.SERVER);
        assertEquals(List.of(), post(PlayerEvents.LOGGED_IN),
                "a generation that registers nothing retires the previous listeners");
        assertEquals(List.of(), post(LevelEvents.LOADED));
    }

    @Test
    void aFailedReloadKeepsTheOldGenerationServingWithoutDoubleRegistration() throws Exception {
        harness.writeScript(ScriptType.SERVER, "lifecycle.js", """
                PlayerEvents.loggedIn(event => event.add('old'))
                """);
        harness.loadScripts(ScriptType.SERVER);
        assertEquals(List.of("old"), post(PlayerEvents.LOGGED_IN));

        // A same-batch peer domain plan throws in the STATE_PLAN preflight → the whole candidate
        // batch fails (same technique as the ticket 26 harness, no production code changed just
        // to fabricate a failure). The peer rejects only once, keeping the recovery path testable.
        harness.root.registerDomainCollector(new CandidateDomainCollector() {
            private boolean rejected;

            @Override public String domain() { return "ticket24-failing-peer"; }
            @Override public ScriptType scriptType() { return ScriptType.SERVER; }
            @Override public void collect(Handle handle) {
                if (!rejected) {
                    rejected = true;
                    handle.registerPlan(new CandidateStatePlan() {
                        @Override public String domain() { return "ticket24-failing-peer-plan"; }
                        @Override public void preflight() { throw new IllegalStateException("peer domain rejected"); }
                        @Override public void publish() { }
                    });
                }
            }
        });

        harness.writeScript(ScriptType.SERVER, "lifecycle.js", """
                PlayerEvents.loggedIn(event => event.add('new'))
                """);
        assertThrows(NekoReloadException.class, () -> harness.reloadScripts(ScriptType.SERVER),
                "a rejected candidate batch must fail the reload loudly");

        assertEquals(List.of("old"), post(PlayerEvents.LOGGED_IN),
                "the previous active generation keeps serving after a failed reload");
        assertFalse(post(PlayerEvents.LOGGED_IN).contains("new"),
                "the rejected candidate's listener must never dispatch");

        harness.reloadScripts(ScriptType.SERVER);
        assertEquals(List.of("new"), post(PlayerEvents.LOGGED_IN),
                "the next reload recovers and swaps exactly once (no half-cleaned state)");
    }

    @Test
    void cancellationFollowsTheActiveGenerationAcrossReloads() throws Exception {
        harness.writeScript(ScriptType.SERVER, "cancel.js", """
                CommandEvents.command(event => { event.add('cancel-gen'); return true })
                """);
        harness.loadScripts(ScriptType.SERVER);
        List<String> cancelled = postCommand();
        assertEquals(List.of("cancel-gen"), cancelled);

        harness.writeScript(ScriptType.SERVER, "cancel.js", """
                CommandEvents.command(event => { event.add('observe-gen'); return false })
                """);
        harness.reloadScripts(ScriptType.SERVER);
        List<String> observed = postCommand();
        assertEquals(List.of("observe-gen"), observed,
                "after the swap the listener runs but does not cancel anymore");

        harness.writeScript(ScriptType.SERVER, "cancel.js", """
                CommandEvents.command(event => { event.add('cancel-gen2'); return true })
                """);
        harness.reloadScripts(ScriptType.SERVER);
        assertEquals(List.of("cancel-gen2"), postCommand());
    }

    @Test
    void cancellationResultIsObservableFromThePostSide() throws Exception {
        // The post-side return value is the observable "script cancellation" result (the platform bridge relays it back as setCanceled)
        harness.writeScript(ScriptType.SERVER, "cancel.js", """
                CommandEvents.command(event => true)
                """);
        harness.loadScripts(ScriptType.SERVER);

        List<String> probe = new ArrayList<>();
        @SuppressWarnings("unchecked")
        var bus = (com.tkisor.nekojs.api.event.EventBusJS<List<String>, ?>) (Object) CommandEvents.COMMAND;
        assertTrue(bus.post(probe), "a cancelling script listener makes the family post report true");

        harness.writeScript(ScriptType.SERVER, "cancel.js", """
                CommandEvents.command(event => false)
                """);
        harness.reloadScripts(ScriptType.SERVER);
        assertFalse(bus.post(probe), "a non-cancelling generation keeps the post result false");
    }

    @Test
    void entityFamilyLifecycleListenersFollowTheSameGenerationSwap() throws Exception {
        harness.writeScript(ScriptType.SERVER, "entity.js", """
                EntityEvents.joinLevel(event => event.add('join'))
                EntityEvents.death(event => event.add('death'))
                EntityEvents.drops(event => event.add('drops'))
                EntityEvents.damagePre(event => event.add('damage'))
                EntityEvents.finalizeSpawn(event => event.add('spawn'))
                EntityEvents.leaveLevel(event => event.add('leave'))
                """);
        harness.loadScripts(ScriptType.SERVER);
        assertEquals(List.of("join"), post(EntityEvents.JOIN_LEVEL));
        assertEquals(List.of("death"), post(EntityEvents.DEATH));
        assertEquals(List.of("drops"), post(EntityEvents.DROPS));
        assertEquals(List.of("damage"), post(EntityEvents.DAMAGE_PRE));
        assertEquals(List.of("spawn"), post(EntityEvents.FINALIZE_SPAWN));
        assertEquals(List.of("leave"), post(EntityEvents.LEAVE_LEVEL));

        harness.writeScript(ScriptType.SERVER, "entity.js", """
                EntityEvents.joinLevel(event => event.add('join-2'))
                """);
        harness.reloadScripts(ScriptType.SERVER);
        assertEquals(List.of("join-2"), post(EntityEvents.JOIN_LEVEL));
        assertEquals(List.of(), post(EntityEvents.DEATH),
                "listeners the new generation no longer declares stop dispatching");
        assertEquals(List.of(), post(EntityEvents.DROPS));
        assertEquals(List.of(), post(EntityEvents.DAMAGE_PRE));
        assertEquals(List.of(), post(EntityEvents.FINALIZE_SPAWN));
        assertEquals(List.of(), post(EntityEvents.LEAVE_LEVEL));
    }

    @Test
    void goalStartupFamilyRoundTripsThroughTheProductionPostingSite() throws Exception {
        harness.writeScript(ScriptType.STARTUP, "goals.js", """
                GoalEvents.register(event => event.add('goal'))
                """);
        harness.loadScripts(ScriptType.STARTUP);

        List<String> first = new ArrayList<>();
        @SuppressWarnings("unchecked")
        var bus = (com.tkisor.nekojs.api.event.EventBusJS<List<String>, ?>) (Object) GoalEvents.REGISTER;
        bus.post(first);
        assertEquals(List.of("goal"), first,
                "the startup family delivers the script listener through the script registration path");

        // Production posting site (the same spot as NekoJSMod.initializeScripts / NekoJSFabricMod.initializeScripts):
        // each post dispatches exactly one generation of listeners, once each
        bus.post(first);
        assertEquals(List.of("goal", "goal"), first);
    }
}
//?}
