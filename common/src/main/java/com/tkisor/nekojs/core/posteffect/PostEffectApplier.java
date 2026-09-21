package com.tkisor.nekojs.core.posteffect;

import java.util.List;

/**
 * Platform side of the post-effect declaration lifecycle (ticket 28): the seam between the
 * Minecraft-free candidate plan and the Adapter that owns the live client renderer and its
 * resources.
 *
 * <p>The plan calls {@link #preflight(List)} during the candidate's joint preflight
 * (STATE_PLAN) and {@link #apply(List)} exactly once from the single commit point. Neither
 * method may mount a production callback for the candidate generation before the commit
 * point: {@code preflight} validates only, and {@code apply} replaces the whole active
 * generation atomically (installs + retires), cleaning up the previous generation's
 * listeners and resources.
 */
public interface PostEffectApplier {

    /** Adapter identity (enters the plan fingerprint and diagnostics, e.g. {@code "26.x-client"}). */
    String adapterId();

    /**
     * Validates declarations against the platform's supported shape (chain JSON parses,
     * resource ids are usable). Must not change any live state; throwing rejects the whole
     * batch and keeps the previous active generation.
     */
    void preflight(List<PostEffectDeclaration> declarations);

    /**
     * Commit point: installs every {@link PostEffectDeclaration.Kind#INSTALL} declaration and
     * releases every {@link PostEffectDeclaration.Kind#RETIRE} id owned by the previous
     * generation. Runs on the client/owner thread with the previous generation still active.
     */
    void apply(List<PostEffectDeclaration> declarations);
}
