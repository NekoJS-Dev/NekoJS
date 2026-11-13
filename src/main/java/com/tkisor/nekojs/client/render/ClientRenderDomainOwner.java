package com.tkisor.nekojs.client.render;

import com.tkisor.nekojs.api.ScriptType;
import com.tkisor.nekojs.core.lifecycle.CandidateDomainCollector;

/**
 * Candidate-domain collector for the script HUD and world renderer registration face: it
 * attaches "which persistent renderers this candidate generation registered" to the
 * candidate's preflight and publish steps, so the commit point can swap the production tables
 * in one go.
 *
 * <p><b>Collection always happens</b>: register a batch even when this generation registers no
 * renderer at all. "The new generation registers nothing" is the only path that retires the
 * old renderers, and an empty batch that never reaches commit can never replace the old table
 * (the empty-plan rule of {@link CandidateDomainCollector}). The batch instance comes from one
 * of two places: the first {@code hudRender}/{@code worldRender} call during script execution
 * created it (that same instance is attached here), or this generation registered nothing and
 * it is created here as an empty batch.
 *
 * <p><b>Not visible during the candidate phase</b>: the batch is inert (it only records ids)
 * and render hooks see the new generation only after {@code publish()} at the commit point.
 * When the candidate fails or is cancelled the batch is dropped with it and the old active
 * generation keeps serving. That is also why
 * {@code ClientRenderPlugin#beforeScriptsLoaded} no longer clears the whole table before
 * loading: that cleared the old active renderers too.
 */
public final class ClientRenderDomainOwner implements CandidateDomainCollector {

    /** Collector identity, shared with the candidate plan and reported in structured reload failures
     * as {@code domain-collect:<id>}. */
    public static final String DOMAIN = ClientRenderRegistry.CANDIDATE_DOMAIN;

    @Override
    public String domain() {
        return DOMAIN;
    }

    /** CLIENT-only: renderer registration is visible only to client scripts. */
    @Override
    public ScriptType scriptType() {
        return ScriptType.CLIENT;
    }

    @Override
    public void collect(Handle handle) {
        ClientRenderRegistry.registerCandidateBatches(handle);
    }

    @Override
    public void reconcileAfterRemoval(Handle handle) {
        // Each published renderer batch withdraws entries belonging to its closing Context.
    }
}
