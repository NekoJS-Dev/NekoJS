// Fabric has no equivalent implementation yet; remove the whole-file guard once the port is
// done.
//? if neoforge {
// 26.x implementation, so this file must not contain version guards. The 1.21.1 implementation
// is the same-named file under versions/1.21.1/src; changing this file's behavior means
// updating that one too.
// The @Doc/@Param text on the script side already differs between the two trees and the
// probe-type golden checks it, so do not copy it into the twin file.
package com.tkisor.nekojs.client.posteffect;

import com.tkisor.nekojs.NekoJS;
import com.tkisor.nekojs.core.lifecycle.CandidateDomainCollector;
import com.tkisor.nekojs.core.posteffect.PostEffectApplier;
import com.tkisor.nekojs.core.posteffect.PostEffectCandidatePlan;
import com.tkisor.nekojs.core.posteffect.PostEffectDeclaration;
import com.tkisor.nekojs.bindings.event.client.ClientEvents;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Owns the post-effect declaration lifecycle on the client and acts as the client-side
 * adapter: a root-authorized {@link CandidateDomainCollector} that keeps the
 * <b>declaration</b> lifecycle separate from the runtime binding.
 *
 * <p><b>Collection (candidate, {@code ReloadPhase.DOMAIN_PLAN}).</b> {@link #collect}
 * dispatches {@code ClientEvents.postEffects} into the <b>candidate's</b> pending listeners,
 * collects {@code register}/{@code unregister} declarations into an inert
 * {@link PostEffectCandidatePlan} and hands that plan to the candidate's preflight and
 * publish steps. Nothing here mounts a production listener, activates a post chain or
 * touches the current client frame.
 *
 * <p><b>Commit (single point).</b> {@link PostEffectCandidatePlan#publish()} calls
 * {@link #apply} exactly once on the owner thread; there the whole new generation is
 * installed and the previous generation's definitions, listeners and cached chains are
 * released, so no old-generation callback survives and no double render or half update can
 * happen. A failed or interrupted candidate never reaches {@code apply}, and the previous
 * active generation keeps serving.
 *
 * <p><b>Runtime binding parity.</b> {@code PostEffects.set/clear/toggle/current} keep their
 * existing caller-visible behaviour and are not replaced by this event.
 *
 * <p><b>Generation and stale read face.</b> {@link #activeGeneration()} and
 * {@link #hasActiveDefinition} report which declaration generation owns a definition, so a
 * caller can tell a live definition from a stale or retired one. This adapter lives in the
 * shared MC-facing tree because it touches client renderer and resource state, while the
 * inert plan stays in {@code common}.
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
        // The batch generation is the domain ledger's own counter: the initial collection point and
        // the transactional commit share the same increasing sequence. The domain plan is not
        // visible before commit, so it labels diagnostics and fingerprints instead of
        // ScriptManager's generation.
        PostEffectCandidatePlan plan = PostEffectCandidatePlan.beginBatch(
                this, activeGeneration + 1L, activeIds);
        try {
            handle.dispatch(ClientEvents.POST_EFFECTS, new PostEffectEventJS(plan));
        } finally {
            // Even if a listener callback throws and the candidate fails, the plan is already frozen and
            // the failed candidate collects no further declarations.
            plan.finish();
        }
        handle.registerPlan(plan);
    }

    // ---- Initial generation (collection point after the first client script load) ----

    /**
     * Collection point of the initial CLIENT generation: the non-transactional
     * {@code loadScripts()} path has no candidate or commit step, so the declarations are
     * dispatched to the <b>active</b> bus and applied in this owner after a passing preflight.
     * A rejected batch ({@link Outcome#BLOCKED}) keeps the previous active generation and never
     * half-applies. Unlike the candidate path, nothing is deferred to a later commit, so the
     * whole batch is validated here and installed in one step.
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

    // ---- PostEffectApplier: preflight and commit-time application ----

    @Override
    public String adapterId() {
        return "26.x-client";
    }

    /**
     * Candidate preflight: parses every declared chain (the same validation the old
     * {@code register} binding performed) and rejects the whole batch on the first invalid
     * payload.
     *
     * <p><b>Publishes nothing.</b> A candidate preflight that passes is not an outcome: another
     * domain's preflight, a joint STATE_PLAN conflict or a close preemption can still discard
     * the whole candidate, and {@link #lastDiagnostics()} is a public read face for what the
     * <b>active</b> generation is. Publishing "preflight passed" here would leave a
     * "preflight passed but never committed" observation behind. The parsed definitions are
     * therefore kept local; the commit point re-parses and publishes {@link Outcome#APPLIED}.
     */
    @Override
    public void preflight(List<PostEffectDeclaration> declarations) {
        if (declarations == null || declarations.isEmpty()) return;
        for (PostEffectDeclaration declaration : declarations) {
            if (declaration.kind() != PostEffectDeclaration.Kind.INSTALL) continue;
            Identifier id = Identifier.tryParse(declaration.id());
            if (id == null) {
                throw new IllegalArgumentException("Invalid post-effect id: " + declaration.id());
            }
            PostEffectManager.Definition definition = PostEffectManager.parseDefinition(id,
                    declaration.chainJson(),
                    PostEffectManager.parseShaderMap(declaration.fragmentShaders()),
                    PostEffectManager.parseShaderMap(declaration.vertexShaders()));
            if (definition == null) {
                throw new IllegalArgumentException(
                        "Invalid post-effect chain JSON for " + declaration.id());
            }
        }
    }

    /**
     * Commit point (called by the joint publish): installs the full new generation and
     * retires everything the batch did not declare. Runs on the owner thread; the previous
     * generation's cached chains are closed by the Adapter, so releases are observable in
     * {@link #lastDiagnostics()}.
     */
    @Override
    public void apply(List<PostEffectDeclaration> declarations) {
        List<PostEffectDeclaration> batch = declarations == null ? List.of() : declarations;
        Map<Identifier, PostEffectManager.Definition> installed = new LinkedHashMap<>();
        Set<Identifier> retired = new LinkedHashSet<>();
        List<String> released = new ArrayList<>();
        long generation = activeGeneration + 1L;
        try {
            for (PostEffectDeclaration declaration : batch) {
                Identifier id = Identifier.tryParse(declaration.id());
                if (id == null) {
                    throw new IllegalArgumentException("Invalid post-effect id: " + declaration.id());
                }
                if (declaration.kind() == PostEffectDeclaration.Kind.RETIRE) {
                    retired.add(id);
                    released.add(declaration.id());
                    continue;
                }
                PostEffectManager.Definition definition = PostEffectManager.parseDefinition(id,
                        declaration.chainJson(),
                        PostEffectManager.parseShaderMap(declaration.fragmentShaders()),
                        PostEffectManager.parseShaderMap(declaration.vertexShaders()));
                if (definition == null) {
                    throw new IllegalArgumentException(
                            "Invalid post-effect chain JSON for " + declaration.id());
                }
                installed.put(id, definition);
            }
            // Ids the old generation declared but this batch no longer declares are retired with the new
            // batch too (declaration removal semantics), not just the ones explicitly unregistered
            // in this batch.
            for (String previous : activeIds) {
                Identifier id = Identifier.tryParse(previous);
                if (id != null && !installed.containsKey(id)) {
                    retired.add(id);
                    released.add(previous);
                }
            }
            PostEffectManager.installGeneration(generation, installed, retired);
            activeGeneration = generation;
            activeIds = installed.keySet().stream().map(Identifier::toString)
                    .collect(java.util.stream.Collectors.toUnmodifiableSet());
            lastDiagnostics = new Diagnostics(Outcome.APPLIED, "commit", generation,
                    installed.size(), retired.size(), List.copyOf(released), null);
        } catch (Throwable failure) {
            // Candidate contract: apply must not throw once preflight passes; if it does anyway, record
            // that faithfully instead of claiming success.
            lastDiagnostics = new Diagnostics(Outcome.RECOVERY_FAILED, "commit", activeGeneration,
                    installed.size(), retired.size(), List.copyOf(released), String.valueOf(failure));
            throw failure;
        }
    }

    // ---- Observation face (generation and stale-state read-only queries) ----

    /** Active declaration generation number ({@code -1} before the first commit). */
    public long activeGeneration() {
        return activeGeneration;
    }

    /** Whether the active declaration generation owns a definition for {@code id}. */
    public boolean hasActiveDefinition(Identifier id) {
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
//?}
