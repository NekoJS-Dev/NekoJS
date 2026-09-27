//? if neoforge {
//? if >=26 {
package com.tkisor.nekojs.client.render;

import com.tkisor.nekojs.api.ScriptType;
import com.tkisor.nekojs.bindings.event.client.ClientEvents;
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
 * Ticket 27 GUI/render resource lifecycle fixture (26.x version-tree test tree): world render
 * registration and presentation, plus the {@code ClientEvents.screenRender}/
 * {@code ClientEvents.hud} listener surface, follow the same candidate generation lifecycle as
 * renderer registration — invisible to the production route while the candidate builds,
 * swapped exactly once at the commit point, and on candidate failure/cancellation the old
 * active keeps serving while the candidate resources are released <b>at failure time</b>
 * (ticket 26 covered the HUD registration boundary and only indirect failure-cleanup
 * evidence; this ticket adds the direct failure-moment assertion).
 *
 * <p>Assertions only use public behavior: the {@code List} probes of
 * {@link Ticket26ClientInputHudHarness#dispatchHud(String)} and this class'
 * {@link #dispatchWorldProbe()} (render-context probes returning the renderer ids actually
 * invoked this frame), the public {@code hasListeners()} surface of
 * {@link ClientEvents#SCREEN_RENDER}/{@link ClientEvents#HUD}, and the read-only
 * {@link ClientRenderRegistry#pendingCandidateRegistrations()} count; never private registry
 * snapshots, callback object identity, or unpublished platform collections.
 *
 * <p>Reuses the ticket 26 harness (real root + real Graal pipeline + production-parity
 * {@code ClientRenderDomainOwner} assembly); this ticket does not build a second assembly.
 */class Ticket27ClientGuiRenderLifecycleTest {

    private Ticket26ClientInputHudHarness harness;

    @BeforeAll
    static void initPlatform() {
        // Gradle may reuse a test worker JVM whose Platform singleton was initialized by a
        // previous execution (e.g. the real NeoForgePlatform, which needs an FML bootstrap this
        // JVM never ran). Reclaim the stub in that case; a healthy worker is left untouched.
        try {
            Object current = platformInstance();
            if (current != null && current.getClass().getName().endsWith("NeoForgePlatform")) {
                platformInstanceField().set(null, null);
            }
        } catch (Throwable ignored) {
            // Reflective hygiene is best effort; ensurePlatformInitialized reports real failures.
        }
        Ticket26ClientInputHudHarness.ensurePlatformInitialized();
    }

    private static Object platformInstance() throws Exception {
        java.lang.reflect.Field instance = platformInstanceField();
        instance.setAccessible(true);
        return instance.get(null);
    }

    private static java.lang.reflect.Field platformInstanceField() throws Exception {
        return com.tkisor.nekojs.platform.Platform.class.getDeclaredField("INSTANCE");
    }

    @BeforeEach
    void setUp() throws Exception {
        Ticket26ClientInputHudHarness.clearClientScripts();
        Ticket26ClientInputHudHarness.resetRegistry();
        harness = new Ticket26ClientInputHudHarness();
    }

    @AfterEach
    void tearDown() throws Exception {
        if (harness != null) {
            harness.close();
            harness = null;
        }
        Ticket26ClientInputHudHarness.resetRegistry();
        Ticket26ClientInputHudHarness.clearClientScripts();
    }

    private static String hudScript(String id) {
        return "ClientEvents.hudRender('" + id + "', { layer: 'normal' }, ctx => ctx.add('" + id + "'))\n";
    }

    private static String worldScript(String id) {
        return "ClientEvents.worldRender('" + id + "', { layer: 'normal' }, ctx => ctx.add('" + id + "'))\n";
    }

    private static String listenerScript() {
        return """
                ClientEvents.screenRender(event => { global.screenSeen = true })
                ClientEvents.hud(painter => { global.hudSeen = true })
                """;
    }

    /** World presentation probe: renderer ids actually invoked this frame (the script callback writes {@code ctx.add(id)}). */
    private static List<String> dispatchWorldProbe() {
        List<String> calls = new ArrayList<>();
        ClientRenderRegistry.dispatchWorld(ClientRenderRegistry.WorldLayer.NORMAL, calls);
        return calls;
    }

    /**
     * Peer collector: rejects the whole candidate batch once at the STATE_PLAN joint preflight
     * (the rejection happens exactly once so the recovery path stays testable), same shape as
     * {@code Ticket26ClientInputHudLifecycleTest}.
     */
    private void rejectNextCandidateOnce() {
        boolean[] rejected = {false};
        harness.root.registerDomainCollector(new CandidateDomainCollector() {
            @Override public String domain() { return "ticket27-failing-peer"; }

            @Override public ScriptType scriptType() { return ScriptType.CLIENT; }

            @Override public void collect(Handle handle) {
                if (!rejected[0]) {
                    rejected[0] = true;
                    handle.registerPlan(new CandidateStatePlan() {
                        @Override public String domain() { return "ticket27-failing-peer-plan"; }
                        @Override public void preflight() { throw new IllegalStateException("peer domain rejected"); }
                        @Override public void publish() { }
                    });
                }
            }
        });
    }

    /**
     * DOMAIN_PLAN observation point: records what the production route looks like while the
     * candidate builds (both the HUD and the world presentation probes).
     */
    private List<List<String>> observeProductionRouteDuringCandidate() {
        List<List<String>> seen = new ArrayList<>();
        harness.root.registerDomainCollector(new CandidateDomainCollector() {
            @Override public String domain() { return "ticket27-observation-peer"; }

            @Override public ScriptType scriptType() { return ScriptType.CLIENT; }

            @Override public void collect(Handle handle) {
                seen.add(List.copyOf(Ticket26ClientInputHudHarness.dispatchHud("normal")));
                seen.add(List.copyOf(dispatchWorldProbe()));
            }
        });
        return seen;
    }

    @Test
    void aRejectedCandidateReleasesItsPendingRendererBatchAtFailureTime() throws Exception {
        harness.writeClientScript("hud.js", hudScript("first"));
        harness.loadClientScripts();
        assertEquals(List.of("first"), Ticket26ClientInputHudHarness.dispatchHud("normal"));

        rejectNextCandidateOnce();
        harness.writeClientScript("hud.js", hudScript("second"));
        assertThrows(NekoReloadException.class, harness::reloadClientScripts);

        assertEquals(List.of("first"), Ticket26ClientInputHudHarness.dispatchHud("normal"),
                "a rejected candidate must not disturb the previous active generation");
        assertEquals(0, ClientRenderRegistry.pendingCandidateRegistrations(),
                "a failed candidate must release its pending registration batch at failure time"
                        + " (AC4: candidate resources fully released; today the inert batch lingers"
                        + " keyed by the dead candidate Context until the next round prunes it)");

        harness.writeClientScript("hud.js", hudScript("third"));
        harness.reloadClientScripts();
        assertEquals(List.of("third"), Ticket26ClientInputHudHarness.dispatchHud("normal"),
                "a later candidate commits normally (no pending batch leak)");
    }

    @Test
    void aCandidateKilledDuringExecutionReleasesItsPendingBatchAndKeepsTheOldActive() throws Exception {
        harness.writeClientScript("hud.js", hudScript("first"));
        harness.loadClientScripts();
        assertEquals(List.of("first"), Ticket26ClientInputHudHarness.dispatchHud("normal"));

        // The candidate script registers the renderer first (the batch/plan has already joined
        // the joint candidate boundary), then trips the statement limit kill: the batch must be
        // released at failure time, not linger keyed by the dead candidate Context.
        harness.writeClientScript("hud.js", hudScript("second") + "while(true) {}\n");
        assertThrows(NekoReloadException.class, harness::reloadClientScripts);

        assertEquals(List.of("first"), Ticket26ClientInputHudHarness.dispatchHud("normal"),
                "a killed candidate leaves the previous active generation serving");
        assertEquals(0, ClientRenderRegistry.pendingCandidateRegistrations(),
                "a killed candidate must release its pending registration batch at failure time");

        harness.writeClientScript("hud.js", hudScript("third"));
        harness.reloadClientScripts();
        assertEquals(List.of("third"), Ticket26ClientInputHudHarness.dispatchHud("normal"),
                "a later candidate commits normally (no pending batch leak)");
    }

    @Test
    void worldRenderPresentationsSwapExactlyOnceAtTheCommitPoint() throws Exception {
        harness.writeClientScript("world.js", worldScript("first"));
        harness.loadClientScripts();
        assertEquals(List.of("first"), dispatchWorldProbe(),
                "the initial (non-transactional) load installs the world renderer as the active generation");

        List<List<String>> seenDuringCandidate = observeProductionRouteDuringCandidate();

        harness.writeClientScript("world.js", worldScript("second"));
        harness.reloadClientScripts();

        assertEquals(List.of(List.of(), List.of("first")), seenDuringCandidate,
                "candidate world renderer registrations must not reach the production route"
                        + " (observation pairs are [hud probe, world probe]; only the world layer is declared here)");
        assertEquals(List.of("second"), dispatchWorldProbe(),
                "the commit point swaps the world presentation in one step: the new renderer runs"
                        + " exactly once and the previous generation's renderer is gone");

        // This generation declares nothing -> the empty batch joins the commit -> the previous
         // world renderer retires (the same boundary as the HUD side).
        harness.writeClientScript("world.js", "global.noWorldRender = true\n");
        harness.reloadClientScripts();
        assertTrue(dispatchWorldProbe().isEmpty(),
                "an empty committed batch retires the previous generation's world renderer");
        assertFalse(ClientRenderRegistry.hasWorld(ClientRenderRegistry.WorldLayer.NORMAL),
                "the per-frame fast path agrees: the world layer has no renderer left");
    }

    @Test
    void screenRenderAndHudListenersFollowTheSameCandidateBoundary() throws Exception {
        harness.writeClientScript("listeners.js", listenerScript());
        harness.loadClientScripts();
        assertTrue(ClientEvents.SCREEN_RENDER.hasListeners(),
                "the initial load installs the screen render listener on the production bus");
        assertTrue(ClientEvents.HUD.hasListeners(),
                "the initial load installs the hud listener on the production bus");

        List<Boolean> screenDuringCandidate = new ArrayList<>();
        harness.root.registerDomainCollector(new CandidateDomainCollector() {
            @Override public String domain() { return "ticket27-listener-observation-peer"; }

            @Override public ScriptType scriptType() { return ScriptType.CLIENT; }

            @Override public void collect(Handle handle) {
                screenDuringCandidate.add(ClientEvents.SCREEN_RENDER.hasListeners());
            }
        });

        // Reloading with the same listeners: while the candidate builds the production route
         // still serves the old generation (the new listeners stay pending), after the commit
         // the new generation takes over; the production listener surface neither duplicates nor
         // loses a listener across the boundary.
        harness.writeClientScript("listeners.js", listenerScript());
        harness.reloadClientScripts();
        assertEquals(List.of(true), screenDuringCandidate,
                "during DOMAIN_PLAN the production route still serves the old generation's listener");
        assertTrue(ClientEvents.SCREEN_RENDER.hasListeners(),
                "the committed generation serves the screen render listener");
        assertTrue(ClientEvents.HUD.hasListeners(), "the committed generation serves the hud listener");

        // A generation that declares no listener retires the old one at the commit, instead of
         // keeping the per-frame callback alive.
        harness.writeClientScript("listeners.js", "global.noListeners = true\n");
        harness.reloadClientScripts();
        assertFalse(ClientEvents.SCREEN_RENDER.hasListeners(),
                "a generation that declares no screen render listener retires the old one");
        assertFalse(ClientEvents.HUD.hasListeners(),
                "a generation that declares no hud listener retires the old one");
    }
}
//?}
//?}
