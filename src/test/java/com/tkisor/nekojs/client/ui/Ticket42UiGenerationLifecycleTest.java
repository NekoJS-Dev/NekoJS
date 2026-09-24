//? if neoforge && >=26 {
package com.tkisor.nekojs.client.ui;

import com.tkisor.nekojs.api.ScriptType;
import com.tkisor.nekojs.core.state.GenerationGlobals;
import com.tkisor.nekojs.core.state.GlobalStateStores;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Ticket 42 generation semantics for JSX UI roots, tested at the Minecraft-free seam:
 * {@link UiRootLifecycle} for the epoch state machine and {@link GenerationGlobals} for the
 * per-generation root registry. The real-client smoke flows (reload success/failure, old
 * Screen close, stale event invalidation, active UI retention) map one-to-one onto the
 * scenarios below; running them against a live client requires the registered Minecraft
 * smoke environment and is not exercised here.
 */
class Ticket42UiGenerationLifecycleTest {

    // ---- state machine ----

    @Test
    void candidateBecomesProductionOnlyAfterItsGenerationCommits() {
        GlobalStateStores stores = new GlobalStateStores();
        GenerationGlobals candidateGlobals = stores.newGeneration(ScriptType.CLIENT, true);
        UiRootLifecycle root = new UiRootLifecycle(true, 6, candidateGlobals);

        // While the candidate builds (active is still 5) the root is usable for data
        // preparation but invisible to production surfaces.
        assertTrue(root.isUsable(5), "candidate root must remain usable for preparation");
        assertFalse(root.isProduction(5), "candidate root must not be production-visible");

        root.observeCommit(6);
        assertEquals(UiRootLifecycle.State.ACTIVE, root.state());
        assertTrue(root.isProduction(6), "committed root must be production-visible");
        assertTrue(root.isUsable(6));
    }

    @Test
    void failedCandidateRootStaysUnusableEvenWhenALaterCandidateReusesTheNumber() {
        GlobalStateStores stores = new GlobalStateStores();
        // Candidate generation 6 fails: its globals are closed during candidate teardown.
        GenerationGlobals failed = stores.newGeneration(ScriptType.CLIENT, true);
        UiRootLifecycle failedRoot = new UiRootLifecycle(true, 6, failed);
        failed.close();

        // A later candidate also numbered 6 is building while active is still 5.
        GenerationGlobals retry = stores.newGeneration(ScriptType.CLIENT, true);
        UiRootLifecycle retryRoot = new UiRootLifecycle(true, 6, retry);

        assertFalse(failedRoot.isUsable(5), "a root whose generation closed must never become usable again");
        assertFalse(failedRoot.isUsable(6), "committing a same-numbered later candidate must not revive the failed root");
        assertTrue(retryRoot.isUsable(5), "the live candidate with the same number must stay usable");
        retryRoot.observeCommit(6);
        assertTrue(retryRoot.isProduction(6));
        assertFalse(failedRoot.isProduction(6));
    }

    @Test
    void supersededActiveRootFailsExplicitlyWithStableCode() {
        GlobalStateStores stores = new GlobalStateStores();
        GenerationGlobals globals = stores.newGeneration(ScriptType.CLIENT, false);
        UiRootLifecycle root = new UiRootLifecycle(false, 5, globals);
        assertTrue(root.isProduction(5));

        // The next generation commits: the old active root must be unusable — an ACTIVE
        // root is never reinterpreted as the in-flight candidate of activeGeneration + 1.
        assertFalse(root.isUsable(6), "superseded active root must be unusable");
        assertEquals(UiRootLifecycle.State.ACTIVE, root.state(), "superseded roots are closed, not demoted to candidate");
    }

    @Test
    void closingIsIdempotentAndTerminal() {
        GenerationGlobals globals = new GlobalStateStores().newGeneration(ScriptType.CLIENT, false);
        UiRootLifecycle root = new UiRootLifecycle(false, 5, globals);

        root.beginClose();
        assertEquals(UiRootLifecycle.State.CLOSING, root.state());
        assertFalse(root.isUsable(5), "input must be rejected while closing");

        root.beginClose(); // reload/setScreen/exit close preemption re-runs teardown
        root.finishClose();
        root.finishClose();
        assertTrue(root.isClosed());
        assertFalse(root.isUsable(5));

        IllegalStateException closedObservation = assertThrows(IllegalStateException.class,
                () -> root.observeCommit(5));
        assertTrue(closedObservation.getMessage().contains("NEKO-7002"),
                "illegal transition must carry the stable code: " + closedObservation.getMessage());
    }

    @Test
    void finishingAnUnopenedCloseIsIllegal() {
        GenerationGlobals globals = new GlobalStateStores().newGeneration(ScriptType.CLIENT, false);
        UiRootLifecycle root = new UiRootLifecycle(false, 5, globals);
        IllegalStateException e = assertThrows(IllegalStateException.class, root::finishClose);
        assertTrue(e.getMessage().contains("NEKO-7002"));
    }

    // ---- generation registry ----

    private static final class RecordingRoot implements GenerationGlobals.UiRoot {
        final List<String> reasons = new ArrayList<>();
        final boolean fail;

        RecordingRoot(boolean fail) {
            this.fail = fail;
        }

        @Override
        public void closeForGeneration(String reason) {
            reasons.add(reason);
            if (fail) throw new IllegalStateException("boom");
        }
    }

    @Test
    void generationCloseReleasesEveryRootOnceAndSurvivesAFailingRoot() {
        GenerationGlobals globals = new GlobalStateStores().newGeneration(ScriptType.CLIENT, false);
        RecordingRoot healthy = new RecordingRoot(false);
        RecordingRoot failing = new RecordingRoot(true);
        RecordingRoot tail = new RecordingRoot(false);
        globals.registerUiRoot(healthy);
        globals.registerUiRoot(failing);
        globals.registerUiRoot(tail);

        globals.close();
        assertEquals(List.of("generation-close"), healthy.reasons);
        assertEquals(List.of("generation-close"), failing.reasons, "failing root was attempted, not skipped");
        assertEquals(List.of("generation-close"), tail.reasons, "failing root must not block the rest");

        globals.close(); // idempotent teardown path
        assertEquals(1, healthy.reasons.size(), "roots are released exactly once");
    }

    @Test
    void candidateTeardownPathCleansCandidateRootsAndKeepsActiveRoots() {
        GlobalStateStores stores = new GlobalStateStores();
        GenerationGlobals activeGlobals = stores.newGeneration(ScriptType.CLIENT, false);
        GenerationGlobals candidateGlobals = stores.newGeneration(ScriptType.CLIENT, true);
        RecordingRoot activeRoot = new RecordingRoot(false);
        RecordingRoot candidateRoot = new RecordingRoot(false);
        activeGlobals.registerUiRoot(activeRoot);
        candidateGlobals.registerUiRoot(candidateRoot);

        // Candidate failure: discard drops write sets, then candidate teardown closes the
        // candidate globals. The active generation is untouched.
        candidateGlobals.discard();
        assertTrue(candidateRoot.reasons.isEmpty(), "discard alone drops write sets; release happens at close");
        candidateGlobals.close();
        assertEquals(List.of("generation-close"), candidateRoot.reasons);
        assertTrue(activeRoot.reasons.isEmpty(), "active UI must survive a failed candidate");

        // Commit-supersede path: the old active generation closes and releases its root.
        activeGlobals.close();
        assertEquals(List.of("generation-close"), activeRoot.reasons);
    }

    @Test
    void registeringOnAClosedGenerationFailsExplicitly() {
        GenerationGlobals globals = new GlobalStateStores().newGeneration(ScriptType.CLIENT, false);
        globals.close();
        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> globals.registerUiRoot(new RecordingRoot(false)));
        assertTrue(e.getMessage().contains("NEKO-7001"), "stale registration must carry the stable code");
    }

    @Test
    void unregisteredRootIsNotReleasedByItsGeneration() {
        GenerationGlobals globals = new GlobalStateStores().newGeneration(ScriptType.CLIENT, false);
        RecordingRoot root = new RecordingRoot(false);
        globals.registerUiRoot(root);
        globals.unregisterUiRoot(root);
        globals.close();
        assertTrue(root.reasons.isEmpty(), "a root closed by its script must not be closed again");
    }
}
//?}
