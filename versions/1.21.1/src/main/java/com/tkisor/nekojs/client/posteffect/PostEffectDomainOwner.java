// 1.21.1 implementation. It pairs with the same-named 26.x file under src/ in the version
// tree: this file is that file evaluated for this node. Keep them in sync when 26.x changes.
// The @Doc/@Param text on the script side differs between the two trees and is checked by the
// probe-type golden, so do not copy the mainline wording.
package com.tkisor.nekojs.client.posteffect;

import com.tkisor.nekojs.NekoJS;
import com.tkisor.nekojs.core.lifecycle.CandidateDomainCollector;
import com.tkisor.nekojs.core.posteffect.PostEffectApplier;
import com.tkisor.nekojs.core.posteffect.PostEffectCandidatePlan;
import com.tkisor.nekojs.core.posteffect.PostEffectDeclaration;
import com.tkisor.nekojs.bindings.event.client.ClientEvents;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Owns the post-effect declaration lifecycle and adapts it for the client. It is a
 * root-authorized {@link CandidateDomainCollector} that keeps the <b>declaration</b> lifecycle
 * separate from the runtime binding.
 *
 * <p><b>Collection (candidate, {@code ReloadPhase.DOMAIN_PLAN}).</b> {@link #collect}
 * dispatches {@code ClientEvents.postEffects} into the <b>candidate's</b> pending listeners,
 * collects {@code register}/{@code unregister} declarations into an inert
 * {@link PostEffectCandidatePlan} and hands that plan to the candidate's preflight/publish
 * step. Nothing here mounts a production listener or touches the current client frame.
 *
 * <p><b>Commit (single point).</b> {@link PostEffectCandidatePlan#publish()} calls
 * {@link #apply} exactly once on the owner thread. There the whole new generation is installed
 * and the previous generation's definitions are released, so no old-generation definition
 * survives and no double registration or half update can happen. A failed or interrupted
 * candidate never reaches {@code apply}, and the previous active generation keeps serving.
 *
 * <p><b>Runtime binding.</b> {@code PostEffects.set/clear/toggle/current} behave the same as
 * before for callers and are not replaced by this event.
 */
public final class PostEffectDomainOwner implements CandidateDomainCollector, PostEffectApplier {

    /** Collector identity (enters the structured reload failure as {@code domain-collect:<id>}). */
    public static final String DOMAIN = PostEffectCandidatePlan.DOMAIN;

    private volatile long activeGeneration = -1L;
    private volatile Set<String> activeIds = Set.of();
    private volatile Diagnostics lastDiagnostics =
            new Diagnostics(Outcome.INITIAL, "none", -1L, 0, 0, List.of(), null);

    public PostEffectDomainOwner() {
    }

    // ---- CandidateDomainCollector (DOMAIN_PLAN phase) ----

    @Override
    public String domain() {
        return DOMAIN;
    }

    /** CLIENT-only domain: the collector never runs in SERVER/STARTUP/TEST candidates. */
    @Override
    public com.tkisor.nekojs.api.ScriptType scriptType() {
        return com.tkisor.nekojs.api.ScriptType.CLIENT;
    }

    @Override
    public void collect(CandidateDomainCollector.Handle handle) {
        // The batch generation is the domain ledger's own sequence number: the initial collection point
        // and the transactional commit share one increasing line. A domain plan is not visible before
        // commit, so use this number as the diagnostic/fingerprint label instead of the
        // ScriptManager generation.
        PostEffectCandidatePlan plan = PostEffectCandidatePlan.beginBatch(
                this, activeGeneration + 1L, activeIds);
        try {
            handle.dispatch(ClientEvents.POST_EFFECTS, new PostEffectEventJS(plan));
        } finally {
            // Even when a listener callback throws and the candidate fails, the plan is already frozen, so
            // a failed candidate never accumulates more declarations.
            plan.finish();
        }
        handle.registerPlan(plan);
    }

    // ---- Initial generation (collection point after the first client script load) ----

    /**
     * Collection point for the initial CLIENT generation. The non-transactional
     * {@code loadScripts()} path has no candidate/commit, so the declarations are dispatched to
     * the <b>active</b> bus and applied in this owner once preflight passes. A rejected batch
     * ({@link Outcome#BLOCKED}) keeps the previous active generation and never half-applies.
     * Unlike the candidate path there is no publish step to join, so the whole batch is validated
     * here and installed in one step.
     */
    public void applyInitialPlan() {
        PostEffectCandidatePlan plan = PostEffectCandidatePlan.beginBatch(
                this, activeGeneration + 1L, activeIds);
        try {
            ClientEvents.POST_EFFECTS.post(new PostEffectEventJS(plan));
        } catch (Throwable dispatchFailure) {
            plan.finish();
            lastDiagnostics = new Diagnostics(Outcome.RECOVERY_FAILED, "initial-dispatch",
                    activeGeneration, 0, 0, List.of(), String.valueOf(dispatchFailure));
            NekoJS.LOGGER.error("NekoJS post-effect initial dispatch failed; keeping the previous"
                    + " active generation", dispatchFailure);
            return;
        }
        plan.finish();
        try {
            plan.preflight();
        } catch (Throwable rejected) {
            lastDiagnostics = new Diagnostics(Outcome.BLOCKED, "initial", activeGeneration,
                    plan.installCount(), 0, List.of(), String.valueOf(rejected));
            NekoJS.LOGGER.error("NekoJS post-effect initial batch rejected (keeping the previous"
                    + " active generation): {}", rejected.toString());
            return;
        }
        apply(plan.declarations());
    }

    // ---- PostEffectApplier: apply at preflight / commit point ----

    @Override
    public String adapterId() {
        return "1.21.1-client";
    }

    /**
     * Candidate preflight: parses every declared chain (the same validation the old
     * {@code register} binding performed) and rejects the whole batch on the first invalid
     * payload. No live state is touched.
     */
    /**
     * Candidate preflight: parses every declared chain and rejects the whole batch on the first
     * invalid payload.
     *
     * <p><b>Publishes nothing.</b> A candidate preflight that passes is not an outcome: another
     * domain's preflight, a joint STATE_PLAN conflict or a close preemption can still discard
     * the whole candidate, and {@link #lastDiagnostics()} is a public read face for what the
     * <b>active</b> generation is. Publishing "preflight passed" here would leave a
     * "preflight passed but never committed" observation behind. The commit point re-parses and
     * publishes {@link Outcome#APPLIED}.
     */
    @Override
    public void preflight(List<PostEffectDeclaration> declarations) {
        if (declarations == null || declarations.isEmpty()) return;
        for (PostEffectDeclaration declaration : declarations) {
            if (declaration.kind() != PostEffectDeclaration.Kind.INSTALL) continue;
            ResourceLocation id = ResourceLocation.tryParse(declaration.id());
            if (id == null) {
                throw new IllegalArgumentException("Invalid post-effect id: " + declaration.id());
            }
            if (PostEffectManager.parseDefinition(id, declaration.chainJson()) == null) {
                throw new IllegalArgumentException(
                        "Invalid post-effect chain JSON for " + declaration.id());
            }
        }
    }

    /**
     * Commit point (called by the joint publish): installs the full new generation and
     * retires everything the batch did not declare. Runs on the owner thread.
     */
    @Override
    public void apply(List<PostEffectDeclaration> declarations) {
        List<PostEffectDeclaration> batch = declarations == null ? List.of() : declarations;
        Map<ResourceLocation, PostEffectManager.Definition> installed = new LinkedHashMap<>();
        Set<ResourceLocation> retired = new LinkedHashSet<>();
        List<String> released = new ArrayList<>();
        long generation = activeGeneration + 1L;
        try {
            for (PostEffectDeclaration declaration : batch) {
                ResourceLocation id = ResourceLocation.tryParse(declaration.id());
                if (id == null) {
                    throw new IllegalArgumentException("Invalid post-effect id: " + declaration.id());
                }
                if (declaration.kind() == PostEffectDeclaration.Kind.RETIRE) {
                    retired.add(id);
                    released.add(declaration.id());
                    continue;
                }
                PostEffectManager.Definition definition =
                        PostEffectManager.parseDefinition(id, declaration.chainJson());
                if (definition == null) {
                    throw new IllegalArgumentException(
                            "Invalid post-effect chain JSON for " + declaration.id());
                }
                installed.put(id, definition);
            }
            // Ids the old generation declared but the new batch no longer declares are retired with it
            // (declaration removal).
            for (String previous : activeIds) {
                ResourceLocation id = ResourceLocation.tryParse(previous);
                if (id != null && !installed.containsKey(id)) {
                    retired.add(id);
                    released.add(previous);
                }
            }
            PostEffectManager.installGeneration(generation, installed, retired);
            activeGeneration = generation;
            activeIds = installed.keySet().stream().map(ResourceLocation::toString)
                    .collect(java.util.stream.Collectors.toUnmodifiableSet());
            lastDiagnostics = new Diagnostics(Outcome.APPLIED, "commit", generation,
                    installed.size(), retired.size(), List.copyOf(released), null);
        } catch (Throwable failure) {
            // The candidate contract says apply must not throw once preflight has passed. If it does
            // anyway, record it as it is and do not claim success.
            lastDiagnostics = new Diagnostics(Outcome.RECOVERY_FAILED, "commit", activeGeneration,
                    installed.size(), retired.size(), List.copyOf(released), String.valueOf(failure));
            throw failure;
        }
    }

    // ---- Observation (read-only generation / stale queries) ----

    /** Active declaration generation number ({@code -1} before the first commit). */
    public long activeGeneration() {
        return activeGeneration;
    }

    /** Whether the active declaration generation owns a definition for {@code id}. */
    public boolean hasActiveDefinition(ResourceLocation id) {
        return id != null && activeIds.contains(id.toString());
    }

    /** Last collection/apply outcome (diagnostics read face). */
    public Diagnostics lastDiagnostics() {
        return lastDiagnostics;
    }

    /**
     * Post-effect declaration domain outcome (diagnostics distinguish active / blocked / failed).
     * Only outcomes the owner actually publishes are listed: a passing candidate preflight is
     * deliberately <b>not</b> one of them (see {@link #preflight}).
     */
    public enum Outcome {
        /** No declaration batch has been processed yet. */
        INITIAL,
        /** New generation installed at the commit point. */
        APPLIED,
        /** The batch was rejected: the previous active generation keeps serving. */
        BLOCKED,
        /** Apply failed after a passing preflight (never claims success). */
        RECOVERY_FAILED
    }

    /** Diagnostics of the most recent declaration batch. */
    public record Diagnostics(Outcome outcome, String source, long generation, int installed,
                              int retired, List<String> releasedIds, String detail) {
    }
}
