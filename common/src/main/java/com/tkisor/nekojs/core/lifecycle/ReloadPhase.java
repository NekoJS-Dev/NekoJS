package com.tkisor.nekojs.core.lifecycle;

/**
 * Stages of a reload.
 *
 * <p>A transactional reload runs through {@link #PREPARATION} → {@link #BINDING} →
 * {@link #EXECUTION} → {@link #EVENT_PLAN} → {@link #DOMAIN_PLAN} → {@link #STATE_PLAN} →
 * {@link #COMMIT}. A failure at any stage carries the stage it happened in. {@link #STARTUP} and
 * {@link #FILE} explicitly mark the non-candidate paths: the STARTUP restart/unsupported case and
 * the single-file reload.
 *
 * <p>{@code UNKNOWN}, for an unclassified failure, went away with
 * {@code ReloadResult.failure(type, error)}: nothing produced it afterwards, so keeping it was
 * dead code.
 */
public enum ReloadPhase {
    /** Creates the candidate Context and node runtime. */
    PREPARATION,
    /** Installs event groups, plugin bindings, managed globals and binding schemas into the candidate Context. */
    BINDING,
    /** Discovers candidate scripts and runs their entry points, which also collects listeners and timers. */
    EXECUTION,
    /**
     * Prepares the candidate's pending listeners for integrity and mountability; dispatch key
     * conversion and similar commit-time work moves here from commit.
     */
    EVENT_PLAN,
    /**
     * Collects domain plans for the candidate: the domain collectors the root owns
     * ({@code CandidateDomainCollector}) dispatch their collection events into the candidate's pending
     * listeners and produce inert domain plans (such as Item/Block modifications), which join the
     * candidate. Listener and collection errors surface here, so the candidate fails.
     */
    DOMAIN_PLAN,
    /**
     * Joint preflight of the candidate's managed state: checks the top-level global/shared write set
     * together with external candidate plans ({@code CandidateStatePlan}, including the domain plans
     * DOMAIN_PLAN produced) for conflicts. A conflict fails here (with
     * domain={@code global-write-conflict} and so on) and does not drop writes other writers already
     * committed.
     */
    STATE_PLAN,
    /** The single commit point: switches production routing and releases the old generation. */
    COMMIT,
    /** STARTUP non-transactional reload (reset+load): an explicit restart/unsupported case, not a candidate path. */
    STARTUP,
    /** Reload of a single file target; reuses the active environment and is a non-candidate path. */
    FILE
}
