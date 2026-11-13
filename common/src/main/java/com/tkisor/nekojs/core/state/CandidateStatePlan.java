package com.tkisor.nekojs.core.state;

/**
 * Joint preflight and joint success/failure for candidate domain plans.
 *
 * <p>This interface deliberately carries no domain semantics. The global/shared write set is the
 * first built-in plan; test plans and later domain plans (such as a candidate plan for Item/Block
 * modification) can join the same joint step: every plan of one candidate runs
 * {@link #preflight()} before commit, all of them run {@link #publish()} at the commit point once
 * every preflight passed, and if any preflight fails the candidate fails and no plan publishes.
 * This interface implements no domain plan and does not pull a domain Adapter into the global
 * owner.
 *
 * <p>Rules implementors must follow:
 * <ul>
 *   <li>{@link #preflight()} runs in the candidate phase (STATE_PLAN) and may throw freely. A
 *       throw fails the whole candidate as a structured reload failure with
 *       domain = {@link #domain()};</li>
 *   <li>{@link #publish()} runs at the commit point, before the global/shared write set
 *       publishes. After a passing preflight, publish must not throw. A throw stops the reload
 *       at the commit point (the global/shared write set does not publish and the active
 *       generation stays), but the plan's own partial side effects are not rolled back: there is
 *       no deep rollback inside a plan;</li>
 *   <li>publish must not write to the global/shared views again: the write set is frozen and the
 *       joint preflight already covered it;</li>
 *   <li>{@link #discard()} runs on the discard path when a candidate fails or is cancelled. A
 *       plan holding candidate resources releases them here, and the active generation is not
 *       affected.</li>
 * </ul>
 */
public interface CandidateStatePlan {

    /** Plan identifier. It becomes the domain of a failure result, for example {@code "test-plan"} or
     * {@code "item-modification"}. */
    String domain();

    /**
     * Joint preflight: checks that the plan is compatible with the already committed state. It
     * may throw; any exception fails the candidate as {@code state-plan-preflight:<domain>}. It
     * must not produce side effects visible outside the candidate.
     */
    void preflight();

    /**
     * Joint publish: runs at the commit point once every preflight passed. It must not throw after
     * a passing preflight (see the interface rules).
     */
    void publish();

    /**
     * Releases the plan when the candidate fails or is cancelled. Called on the owner thread from
     * the candidate discard path: the plan releases whatever candidate-scoped resources it
     * collected while the active generation keeps serving untouched. It is also called from the
     * generation {@code close()} path, where a plan may already have published, so an
     * implementation must treat a call after publish as an idempotent no-op. The default is a
     * no-op; only plans holding candidate resources need to override it.
     *
     * <p>It must be idempotent and must not throw. A throwing discard is not rolled back, same as
     * {@link #publish()}. The joint step logs the failure and keeps tearing the candidate down, so
     * one misbehaving plan cannot block candidate cleanup.
     */
    default void discard() {
    }
}
