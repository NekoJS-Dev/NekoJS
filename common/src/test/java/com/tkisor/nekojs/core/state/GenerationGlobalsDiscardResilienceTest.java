package com.tkisor.nekojs.core.state;

import com.tkisor.nekojs.api.ScriptType;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Ticket 27 AC4 discard resilience: a candidate generation's joint discard must tear down
 * EVERY plan even when one plan's {@code discard()} throws — one misbehaving plan cannot
 * block the candidate cleanup of the others (the failure is logged as NEKO-1001 and skipped).
 */
class GenerationGlobalsDiscardResilienceTest {

    /**
     * In-memory plan recording release calls; honors the {@link CandidateStatePlan#discard()}
     * idempotency contract (a second call is a no-op) and optionally throws on the FIRST call.
     */
    private static final class RecordingPlan implements CandidateStatePlan {
        final String domain;
        final RuntimeException discardFailure;
        int releaseCalls;
        boolean discarded;

        RecordingPlan(String domain, RuntimeException discardFailure) {
            this.domain = domain;
            this.discardFailure = discardFailure;
        }

        @Override public String domain() {
            return domain;
        }

        @Override public void preflight() {
        }

        @Override public void publish() {
        }

        @Override public void discard() {
            if (discarded) {
                return;
            }
            discarded = true;
            releaseCalls++;
            if (discardFailure != null) {
                throw discardFailure;
            }
        }
    }

    @Test
    void oneThrowingPlanDoesNotBlockTheOtherPlansDiscard() {
        GlobalStateStores stores = new GlobalStateStores();
        GenerationGlobals candidate = stores.newGeneration(ScriptType.SERVER, true);
        RecordingPlan healthy = new RecordingPlan("healthy-plan", null);
        RecordingPlan broken = new RecordingPlan("broken-plan",
                new IllegalStateException("candidate resource already gone"));
        RecordingPlan healthyAfter = new RecordingPlan("healthy-after-plan", null);
        candidate.addPlan(healthy);
        candidate.addPlan(broken);
        candidate.addPlan(healthyAfter);

        candidate.discard();

        assertTrue(healthy.discarded, "the plan before the failing one must be discarded");
        assertTrue(broken.discarded, "the failing plan itself runs its release body");
        assertTrue(healthyAfter.discarded,
                "the plan AFTER the failing one must still be discarded — one misbehaving plan "
                        + "cannot block candidate cleanup (ticket 27 AC4)");
        assertEquals(1, healthy.releaseCalls);
        assertEquals(1, healthyAfter.releaseCalls);
    }

    @Test
    void discardAfterPublishReachesPlansButStaysIdempotent() {
        GlobalStateStores stores = new GlobalStateStores();
        GenerationGlobals candidate = stores.newGeneration(ScriptType.SERVER, true);
        RecordingPlan published = new RecordingPlan("published-plan", null);
        candidate.addPlan(published);
        candidate.preflightJoint();
        candidate.publishJoint();
        assertEquals(0, published.releaseCalls,
                "publish must not invoke the discard release itself");

        // Generation close() also runs the discard path after a successful publish; the plan
        // sees a post-publish discard call and its idempotency guard keeps it a no-op release.
        candidate.discard();
        assertEquals(1, published.releaseCalls,
                "exactly one release across publish + close-time discard (idempotent contract)");
        assertTrue(published.discarded);
    }
}
