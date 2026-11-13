package com.tkisor.nekojs.core.lifecycle;

import com.tkisor.nekojs.api.ScriptType;
import com.tkisor.nekojs.api.event.EventBusJS;
import com.tkisor.nekojs.core.state.CandidateStatePlan;
import graal.graalvm.polyglot.Context;

/**
 * Collects domain contributions for a candidate generation. It is a domain collection mount point
 * owned by {@link NekoRuntimeRoot}. A reload transaction calls each collector once for the
 * candidate being built, after EVENT_PLAN and before STATE_PLAN
 * ({@code ReloadPhase.DOMAIN_PLAN}). The collector dispatches its domain collect events to the
 * <b>candidate's pending listeners</b>, produces an inert domain plan, and attaches it to the
 * same candidate through {@link Handle#registerPlan(CandidateStatePlan)}, so the plan takes part
 * in that candidate's joint preflight and joint publication ({@code CandidateStatePlan}).
 *
 * <p>This interface carries no domain semantics in common: the implementations for Item/Block
 * modification and for the dynamic registration facades live in the shared MC-facing layer of the
 * version tree (plus the paired 1.21.1 files) and are registered into the root by platform
 * assembly (the loader entry). It adds no second runtime owner, no second event bus and no public
 * domain runtime; a collector is only a reload-phase mount point owned by the root.
 *
 * <p>Rules:
 * <ul>
 *   <li>{@link #collect(Handle)} runs synchronously on the owner thread (the reload holds the
 *       instance lock). A throw fails the candidate (domain = {@code "domain-collect:" + domain()})
 *       and the old active generation stays;</li>
 *   <li>The collector decides how listener errors are handled. A domain that must fail as a whole
 *       (modification) lets the exception bubble through {@link Handle#dispatch}, which fails the
 *       candidate. A domain that wants data-level failures recorded in the plan and rejected
 *       exactly at preflight (dynamic registration) handles listeners one by one with
 *       {@link Handle#listenersOf}/{@link Handle#execute};</li>
 *   <li>Register a plan even when there are no listeners (an empty plan): the "restore baseline"
 *       semantics of declaration removal depends on an empty plan taking part in commit.</li>
 * </ul>
 */
public interface CandidateDomainCollector {

    /**
     * Collector id, used to tell domains apart in failure results (for example
     * {@code "item-block-modification"}).
     */
    String domain();

    /**
     * The ScriptType this collector takes part in; the collector is only called during a
     * reload of that type (for example SERVER).
     */
    ScriptType scriptType();

    /**
     * Collects a candidate: dispatches the collect events to the candidate's pending listeners and
     * registers the domain plan. A throw fails the candidate.
     */
    void collect(Handle handle);

    /**
     * Rebuilds whole-type contributions after an activation is removed. The handle exposes
     * surviving active listeners without re-running entry scripts. Contexts may be empty;
     * plans join a detached preflight/publication batch. Owners whose generation resources
     * already remove their own contributions can override this with a no-op.
     */
    default void reconcileAfterRemoval(Handle handle) { collect(handle); }

    /**
     * Handle for one collect call: the candidate Context, the dispatch primitives that
     * target the candidate's pending listeners, and plan registration. It is valid only for the
     * duration of the enclosing {@link #collect(Handle)} call.
     */
    interface Handle {

        /**
         * The Context of the candidate being built, used to decide where a joint plan is
         * registered from.
         */
        Context candidateContext();

        /** All participants in a joint replacement, in deterministic initialization order. */
        default java.util.List<Context> candidateContexts() {
            return java.util.List.of(candidateContext());
        }

        ScriptType scriptType();

        /**
         * Pending listeners this candidate collected on the target bus; they are not on the
         * production bus before commit. The list is stably sorted in the same order as the
         * {@code EventBusBase} compile snapshot (priority descending, registration order within a
         * priority). The collector can pass each listener to {@link #execute} and handle errors
         * itself.
         *
         * <p><b>Non-dispatch buses only</b>: a bus that dispatches by key
         * ({@code EventBusJS#canDispatch()}) needs the key to decide the delivery subset, and a
         * collect dispatch has no key context. Calls on such a bus are rejected with
         * {@code UnsupportedOperationException}; there is no silent fallback that dispatches
         * everything and ignores the key.
         */
        java.util.List<EventBusJS.PendingListener> listenersOf(EventBusJS<?, ?> bus);

        /**
         * Runs one candidate listener in the candidate Context on the same owner thread.
         * It applies the same scriptId switch and callback depth marker as production dispatch.
         * Exceptions <b>propagate up</b>; the collector decides whether to catch them.
         */
        void execute(EventBusJS.PendingListener listener, Object event);

        /**
         * Convenience collect dispatch: sends the event in dispatch order to every pending
         * listener this candidate collected on the bus. An exception thrown by a listener callback
         * propagates up (it fails the candidate, or the caller handles it).
         */
        default void dispatch(EventBusJS<?, ?> bus, Object event) {
            for (EventBusJS.PendingListener listener : listenersOf(bus)) {
                execute(listener, event);
            }
        }

        /**
         * Attaches a domain plan to the candidate's joint batch. Same as
         * {@code ScriptManager.registerCandidatePlan}.
         */
        void registerPlan(CandidateStatePlan plan);
    }
}
