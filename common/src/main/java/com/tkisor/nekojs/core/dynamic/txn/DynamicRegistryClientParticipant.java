package com.tkisor.nekojs.core.dynamic.txn;

import com.tkisor.nekojs.core.dynamic.plan.DynamicAdapterRequest;
import com.tkisor.nekojs.core.dynamic.plan.DynamicCandidateRegistryPlan;
import com.tkisor.nekojs.core.dynamic.plan.DynamicDefinition;
import com.tkisor.nekojs.core.dynamic.plan.DynamicRegistryPlanStore;

import java.util.ArrayList;
import java.util.List;

/**
 * Client-side endpoint of the Dynamic Registry batch transaction (ticket 21, AC4):
 * holds this node's view of the activated state (its own
 * {@link DynamicRegistryPlanStore} ledger plus an activation watermark) and turns
 * server messages into local, all-or-nothing activation through a local
 * {@link DynamicRegistryAdapter}.
 *
 * <p><b>Sync-incomplete nodes never activate a new generation</b>: a COMMIT whose
 * generation has no prior accepted PREPARE of the same generation is rejected with a
 * defined outcome; a duplicate COMMIT and a COMMIT for an already-activated
 * generation are recorded no-ops (the new generation executes exactly once); an
 * ABORT discards the staged prepare. Disconnect discards any staged prepare while
 * the ledger and watermark survive (registry entries are never unregistered
 * mid-session — ticket 16's no-physical-delete rule), so a rejoin is caught up by a
 * server STATE_SYNC of the already-activated state.
 *
 * <p>Local validation applies the same first-version conflict rule as the server:
 * a target definition whose key is already present locally with a different
 * fingerprint rejects the whole message (no silent overwrite by an unimplemented
 * replace/update).
 *
 * <p><b>Thread contract</b>: every public method must be called on this node's
 * transaction owner thread (the client main thread in production). A production
 * receive path that starts on a network thread must hop to the owner thread before
 * calling in, per ticket 17's receive-side discipline — this class is not
 * thread-safe by design.
 */
public final class DynamicRegistryClientParticipant {

    /** Decision for a PREPARE / STATE_SYNC message (becomes the ack payload). */
    public record PrepareDecision(boolean accepted, String reason) {}

    /** Result of a COMMIT / STATE_SYNC activation attempt. */
    public record ActivationResult(long generation, boolean activated, String outcome, String detail) {}

    private final DynamicRegistryAdapter adapter;
    private final DynamicRegistryPlanStore ledger = new DynamicRegistryPlanStore();
    private long activatedGeneration = -1L;
    private DynamicSyncMessage staged;
    private final List<String> eventLog = new ArrayList<>();

    public DynamicRegistryClientParticipant(DynamicRegistryAdapter adapter) {
        this.adapter = adapter;
    }

    // ---- server → client messages ----

    /** PREPARE: validate the target state and stage it; the answer becomes the ack. */
    public PrepareDecision onPrepare(DynamicSyncMessage message) {
        if (message.kind() != DynamicSyncMessage.Kind.PREPARE) {
            return reject("unexpected-kind:" + message.kind());
        }
        if (message.generation() <= activatedGeneration) {
            // Duplicate/late prepare for a generation this node already activated:
            // defined idempotent outcome — re-accept so the server can finish.
            event("prepare-duplicate-generation:" + message.generation());
            return new PrepareDecision(true, "duplicate-generation");
        }
        String conflict = findConflict(message);
        if (conflict != null) {
            return reject("conflict:" + conflict);
        }
        staged = message;
        event("prepare-staged:" + message.generation() + " entries=" + message.entries().size());
        return new PrepareDecision(true, null);
    }

    /** COMMIT: activate the staged prepare of the same generation, exactly once. */
    public ActivationResult onCommit(DynamicSyncMessage message) {
        if (message.kind() != DynamicSyncMessage.Kind.COMMIT) {
            return notActivated(message.generation(), "unexpected-kind:" + message.kind());
        }
        if (message.generation() <= activatedGeneration) {
            // Duplicate commit for a generation this node already activated: defined
            // idempotent no-op — the new generation executed exactly once. A stray
            // staged prepare of that same generation is discarded with it.
            if (staged != null && staged.generation() == message.generation()) {
                event("discarded-duplicate-staged-prepare:" + message.generation());
                staged = null;
            }
            event("already-activated:" + message.generation());
            return new ActivationResult(activatedGeneration, false, "already-activated",
                    "generation " + message.generation() + " executes once; repeat commit recorded");
        }
        if (staged == null || staged.generation() != message.generation()) {
            // Sync incomplete for this generation: never activate (AC4).
            return notActivated(message.generation(), "commit-without-matching-prepare");
        }
        try {
            activateStaged(staged);
        } catch (Throwable e) {
            staged = null;
            event("activation-failed:" + message.generation() + " " + e.getMessage());
            return notActivated(message.generation(), "activation-failed:"
                    + (e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage()));
        }
        activatedGeneration = staged.generation();
        staged = null;
        event("activated:" + activatedGeneration);
        return new ActivationResult(activatedGeneration, true, "activated", null);
    }

    /** ABORT: discard the staged prepare (cleanup); ledger and watermark unchanged. */
    public void onAbort(DynamicSyncMessage message) {
        if (staged != null && staged.generation() == message.generation()) {
            event("aborted:" + message.generation() + " reason=" + message.reason());
            staged = null;
        } else {
            event("abort-ignored:" + message.generation() + " (no matching staged prepare)");
        }
    }

    /**
     * STATE_SYNC: catch-up to the already-activated server state — validate like a
     * prepare, then apply immediately. Never activates a generation newer than the
     * server's activated generation (the server only ever sends its own watermark).
     */
    public PrepareDecision onStateSync(DynamicSyncMessage message) {
        if (message.kind() != DynamicSyncMessage.Kind.STATE_SYNC) {
            return reject("unexpected-kind:" + message.kind());
        }
        String conflict = findConflict(message);
        if (conflict != null) {
            return reject("conflict:" + conflict);
        }
        if (message.generation() > activatedGeneration) {
            try {
                activateStaged(message);
            } catch (Throwable e) {
                event("state-sync-failed:" + message.generation() + " " + e.getMessage());
                return reject("activation-failed:"
                        + (e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage()));
            }
            activatedGeneration = message.generation();
            event("state-sync-activated:" + activatedGeneration);
        } else {
            event("state-sync-idempotent:" + message.generation());
        }
        return new PrepareDecision(true, null);
    }

    /** Disconnect: drop any staged prepare; the ledger and watermark survive for rejoin. */
    public void onDisconnect() {
        if (staged != null) {
            event("disconnect-discarded-staged:" + staged.generation());
            staged = null;
        } else {
            event("disconnect");
        }
    }

    // ---- observation ----

    /** Server-generation watermark of this node (highest activated generation). */
    public long activatedGeneration() {
        return activatedGeneration;
    }

    /** Staged (accepted but not committed) generation; -1 when none. */
    public long stagedGeneration() {
        return staged == null ? -1L : staged.generation();
    }

    /** This node's activated definition ledger (its own conflict-detection view). */
    public DynamicRegistryPlanStore ledger() {
        return ledger;
    }

    /** Client-side decision log (bounded diagnostics). */
    public List<String> eventLog() {
        return List.copyOf(eventLog);
    }

    // ---- internals ----

    private void activateStaged(DynamicSyncMessage message) {
        List<DynamicAdapterRequest> requests = new ArrayList<>();
        DynamicCandidateRegistryPlan plan = ledger.beginBatch();
        for (DynamicSyncMessage.Entry entry : message.entries()) {
            plan.stageExistingDefinition(entry.definition(), entry.ownerScriptId());
            requests.add(new DynamicAdapterRequest(
                    entry.definition(), message.generation(), entry.ownerScriptId()));
        }
        plan.preflight();
        adapter.activate(requests);
        plan.publish();
    }

    /** First key whose fingerprint differs from this node's ledger, or null when consistent. */
    private String findConflict(DynamicSyncMessage message) {
        for (DynamicSyncMessage.Entry entry : message.entries()) {
            DynamicRegistryPlanStore.ExposedEntry exposed = ledger.exposedEntry(entry.key());
            if (exposed != null
                    && !exposed.definition().fingerprint().equals(entry.definition().fingerprint())) {
                return entry.key();
            }
        }
        return null;
    }

    private PrepareDecision reject(String reason) {
        event("rejected:" + reason);
        return new PrepareDecision(false, reason);
    }

    private ActivationResult notActivated(long generation, String outcome) {
        event(outcome + ":" + generation);
        return new ActivationResult(activatedGeneration, false, outcome, null);
    }

    private void event(String entry) {
        eventLog.add(entry);
        if (eventLog.size() > 128) {
            eventLog.remove(0);
        }
    }
}
