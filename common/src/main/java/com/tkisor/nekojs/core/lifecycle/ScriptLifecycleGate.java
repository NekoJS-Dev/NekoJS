package com.tkisor.nekojs.core.lifecycle;

/**
 * Owner-thread lifecycle scheduling gate for one ScriptType.
 *
 * <p>The caller's instance lock ({@code ScriptManager}) already serializes work across threads:
 * managed lifecycle requests from a non-owner thread queue on the monitor and never touch the
 * Context, bindings, listeners or timers concurrently. This gate covers the three cases the
 * instance lock cannot express:
 * <ul>
 *   <li>No nested reload: when the same thread is already in a lifecycle and asks for RELOAD or
 *       LOAD again (a reload from a callback, or a concurrent reload re-entering on the same
 *       thread), the gate rejects it instead of recursively starting a second candidate.</li>
 *   <li>Requests from callbacks: a lifecycle request on the same thread while a managed callback
 *       (event listener or timer) runs is rejected instead of waiting on its own queue.</li>
 *   <li>Close wins: {@link #requestClose()} sets a volatile flag <i>before</i> taking the instance
 *       lock, so a reload/load that has not started yet is rejected once it gets the lock. The
 *       close side interrupts and discards an in-flight candidate (cancellation points: candidate
 *       evaluation interrupt and the check before commit). After {@link #markClosed()} every new
 *       lifecycle is rejected.</li>
 * </ul>
 *
 * <p>Threading: apart from the volatile flags (closeRequested/closed/activeFailed) and the static
 * callback depth, every instance method must be called while holding the caller's instance lock,
 * which matches {@code ScriptManager}'s synchronized methods. The instance lock handles queuing
 * across threads, so {@code depth > 0} can only mean re-entry on the same thread; another thread
 * would be blocked outside.
 *
 * <p>Nesting exception: the STARTUP reset+load path calls LOAD synchronously inside a RELOAD
 * critical section. That one nesting (LOAD in RELOAD on the same thread) is allowed; all others
 * are rejected. CLOSE never nests: a close requested from the same thread inside a lifecycle only
 * sets the {@link #requestClose()} flag and returns a rejection. The in-flight operation then
 * fails at a cancellation point, and the caller can close again once it finishes.
 */
public final class ScriptLifecycleGate {

    /** Kinds of lifecycle operation. */
    public enum Operation {
        RELOAD,
        LOAD,
        CLOSE
    }

    /** Result of entering, as the caller observes it, not internal lock state. */
    public enum Decision {
        /** Entered; the caller continues and calls {@link #exit(Operation)} in a finally block. */
        EXECUTED,
        /** Re-entry on the same thread, or a request from inside a managed callback. Never recurse; report the rejection. */
        REJECTED_REENTRANT,
        /** Close was requested but is not finished: unstarted work is rejected, work in flight is preempted by close. */
        REJECTED_CLOSING,
        /** Closed: every new lifecycle is rejected. */
        REJECTED_CLOSED
    }

    private Thread owner;
    private int depth;
    private Operation outer;
    private volatile boolean closeRequested;
    private volatile boolean closed;
    private volatile boolean activeFailed;

    private static final ThreadLocal<Integer> CALLBACK_DEPTH = ThreadLocal.withInitial(() -> 0);
    private static final ThreadLocal<java.util.List<Runnable>> CALLBACK_RELEASES = new ThreadLocal<>();

    public static boolean isInCallback() {
        return CALLBACK_DEPTH.get() > 0;
    }

    /** Defer owner teardown until every nested guest callback has returned. */
    public static void afterCallback(Runnable release) {
        if (!isInCallback()) throw new IllegalStateException("[NEKO-1017] No callback owns deferred teardown");
        var releases = CALLBACK_RELEASES.get();
        if (releases == null) {
            releases = new java.util.ArrayList<>();
            CALLBACK_RELEASES.set(releases);
        }
        releases.add(release);
    }

    /**
     * Enters a managed callback (the body of an event listener or timer callback). The caller
     * (EventBusJS / NekoNodeTimers) pairs it with exitCallback() around the guest callback. It
     * only updates a thread-local counter: no lock, and no access to the Context.
     */
    public static void enterCallback() {
        CALLBACK_DEPTH.set(CALLBACK_DEPTH.get() + 1);
    }

    /** Leaves a managed callback; pairs with {@link #enterCallback()}. */
    public static void exitCallback() {
        int remaining = CALLBACK_DEPTH.get() - 1;
        if (remaining <= 0) {
            CALLBACK_DEPTH.remove();
            var releases = CALLBACK_RELEASES.get();
            CALLBACK_RELEASES.remove();
            if (releases != null) {
                for (Runnable release : releases) {
                    try { release.run(); }
                    catch (Throwable failure) {
                        com.tkisor.nekojs.NekoJS.LOGGER.error("[NEKO-1017] Deferred callback teardown failed", failure);
                    }
                }
            }
        } else {
            CALLBACK_DEPTH.set(remaining);
        }
    }

    /**
     * Requests entry into a lifecycle. The caller must already hold the instance lock.
     *
     * @return the decision; when it is not {@link Decision#EXECUTED} no gate state changed, the caller
     *         must not run the lifecycle body and must report the rejection to the outside
     */
    public Decision tryEnter(Operation op) {
        if (op == Operation.CLOSE) {
            // CLOSE never nests: a close requested on the same thread inside RELOAD/LOAD only leaves the
            // preemption flag, and the in-flight operation is closed separately after it fails at a
            // cancellation point. This avoids tearing down the state machine while it runs.
            if (depth > 0) {
                return Decision.REJECTED_REENTRANT;
            }
            owner = Thread.currentThread();
            outer = op;
            depth = 1;
            return Decision.EXECUTED;
        }
        if (closed) {
            return Decision.REJECTED_CLOSED;
        }
        if (closeRequested) {
            return Decision.REJECTED_CLOSING;
        }
        if (depth > 0) {
            if (op == Operation.LOAD && outer == Operation.RELOAD && depth == 1) {
                // The one supported nesting: a single LOAD inside the RELOAD of the STARTUP reset+load path.
                // Anything deeper is still rejected, because real calls never go deeper than one level.
                depth++;
                return Decision.EXECUTED;
            }
            return Decision.REJECTED_REENTRANT;
        }
        if (CALLBACK_DEPTH.get() > 0) {
            return Decision.REJECTED_REENTRANT;
        }
        owner = Thread.currentThread();
        outer = op;
        depth = 1;
        return Decision.EXECUTED;
    }

    /**
     * Exits a lifecycle. The caller must hold the instance lock and must pair this with one
     * {@link Decision#EXECUTED} from {@link #tryEnter(Operation)}. Call it in a finally block; the
     * cleanup is idempotent and swallows nothing.
     */
    public void exit(Operation op) {
        if (depth <= 0) {
            throw new IllegalStateException("ScriptLifecycleGate.exit without matching tryEnter(" + op + ")");
        }
        depth--;
        if (depth == 0) {
            owner = null;
            outer = null;
        }
    }

    /** Whether a lifecycle is in progress. Reads gate state, so call it while holding the lock. */
    public boolean isInLifecycle() {
        return depth > 0;
    }

    /**
     * Requests close. The close side sets this <i>before</i> it takes the instance lock, so any other
     * thread that gets the lock afterwards sees the rejection: close wins over a reload that has not
     * started.
     */
    public void requestClose() {
        closeRequested = true;
    }

    public boolean isCloseRequested() {
        return closeRequested;
    }

    /** Marks the terminal close as done: every new lifecycle is rejected afterwards; idempotent. */
    public void markClosed() {
        closed = true;
    }

    public boolean isClosed() {
        return closed;
    }

    /** Stops dispatch to a terminated active generation until explicit recovery or close. */
    public void markActiveFailed() {
        activeFailed = true;
    }

    public void clearActiveFailed() {
        activeFailed = false;
    }

    public boolean isActiveFailed() {
        return activeFailed;
    }
}
