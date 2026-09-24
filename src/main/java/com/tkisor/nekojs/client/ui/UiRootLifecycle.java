//? if neoforge && >=26 {
package com.tkisor.nekojs.client.ui;

import com.tkisor.nekojs.core.state.GenerationGlobals;

/**
 * Lifecycle state machine and generation epoch checks for one JSX UI root (ticket 42).
 * Minecraft-free so reload, discard and stale-handle transitions stay testable without a
 * live client; {@link JsxHostAdapter} delegates every generation decision here.
 *
 * <p>States: {@code CANDIDATE} (created by an in-flight reload candidate — data preparation
 * allowed, nothing production-visible) → {@code ACTIVE} (its generation committed —
 * production Screen and input routing allowed) → {@code CLOSING} → {@code CLOSED}
 * (terminal). Transitions are forward-only; re-opening a closed root or observing an
 * unexpected state throws with a stable {@code NEKO-} code instead of silently succeeding.
 *
 * <p>Epoch validity has two layers: the generation number must relate to the manager's
 * committed generation (equal, or exactly one ahead while the owning candidate builds), and
 * the owning {@link GenerationGlobals} must not be closed. The instance check is what makes
 * a root from a <i>failed</i> candidate distinguishable from a later candidate that happens
 * to reuse the same number — failed candidates do not increment the counter.
 */
final class UiRootLifecycle {
    enum State { CANDIDATE, ACTIVE, CLOSING, CLOSED }

    private final long generation;
    private final GenerationGlobals globals;
    private State state;

    UiRootLifecycle(boolean createdInCandidate, long generation, GenerationGlobals globals) {
        this.state = createdInCandidate ? State.CANDIDATE : State.ACTIVE;
        this.generation = generation;
        this.globals = globals;
    }

    long generation() {
        return generation;
    }

    State state() {
        return state;
    }

    boolean isClosed() {
        return state == State.CLOSED;
    }

    /**
     * Whether operations of the owning generation may still run: the root is not closing or
     * closed, its globals are alive, and its generation is either the committed one or the
     * single in-flight candidate ({@code activeGeneration + 1}).
     */
    boolean isUsable(long activeGeneration) {
        if (state != State.CANDIDATE && state != State.ACTIVE) return false;
        if (globals.isClosed()) return false;
        return generation == activeGeneration || generation == activeGeneration + 1;
    }

    /**
     * Whether production surfaces (Screen display, input routing) may be used: only the
     * committed generation itself. A usable-but-not-production root belongs to a candidate
     * whose commit has not happened yet.
     */
    boolean isProduction(long activeGeneration) {
        return state == State.ACTIVE && !globals.isClosed() && generation == activeGeneration;
    }

    /**
     * Observes the manager's committed generation and promotes {@code CANDIDATE} to
     * {@code ACTIVE} when this root's candidate committed (same generation number, globals
     * still alive). A mismatch is not an error — the candidate may still be building, or may
     * have failed — so observation is simply a no-op and validity is decided by
     * {@link #isUsable(long)} / {@link #isProduction(long)}. Observing from {@code CLOSING} or
     * {@code CLOSED} is an illegal transition.
     */
    void observeCommit(long activeGeneration) {
        if (state == State.CLOSED || state == State.CLOSING) {
            throw new IllegalStateException("[NEKO-7002] UI root cannot observe a commit while "
                    + state + "; generation " + generation);
        }
        if (state == State.CANDIDATE && generation == activeGeneration && !globals.isClosed()) {
            state = State.ACTIVE;
        }
    }

    /**
     * Enters {@code CLOSING} (idempotent: a second close request on {@code CLOSING} or
     * {@code CLOSED} returns without effect so every cleanup path can run unconditionally).
     *
     * @return the state before this call, so callers skip work they already did
     */
    State beginClose() {
        State previous = state;
        if (state == State.CANDIDATE || state == State.ACTIVE) state = State.CLOSING;
        return previous;
    }

    /** Finishes the close started by {@link #beginClose()}; idempotent on {@code CLOSED}. */
    void finishClose() {
        if (state == State.CLOSING) state = State.CLOSED;
        else if (state != State.CLOSED) {
            throw new IllegalStateException("[NEKO-7002] UI root cannot finish a close from "
                    + state + "; generation " + generation);
        }
    }
}
//?}
