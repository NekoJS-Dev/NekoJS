package com.tkisor.nekojs.core.posteffect;

import com.tkisor.nekojs.core.state.CandidateStatePlan;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Generation-scoped candidate plan for the client post-effect declaration lifecycle
 * (ticket 28): {@code ClientEvents.postEffects} listeners collect declarations while the
 * candidate generation is being built, and only the joint commit point turns them into the
 * active generation.
 *
 * <p><b>Inert by construction.</b> This class lives in {@code common} and holds no
 * Minecraft, loader, renderer or resource type: collecting a batch cannot mount a
 * production listener, activate a post chain or touch the current client frame. The
 * platform Adapter ({@code PostEffectDomainOwner}) owns every renderer/resource effect and
 * applies them exactly once from {@link #publish()}.
 *
 * <p><b>Whole-batch failure.</b> A collection error poisons the plan, so joint preflight
 * rejects the whole batch, the candidate is discarded and the previously active generation
 * keeps serving ({@code PostEffectDomainOwner.Outcome.BLOCKED}). No partial commit exists.
 *
 * <p><b>Retire semantics.</b> {@link #beginBatch} records which ids the previously active
 * generation owns; {@link #finish()} keeps every collected install and retires every
 * previous id the candidate did not declare. Retirement releases the old generation's
 * renderer/resource state instead of leaving a silently stale runtime definition.
 */
public final class PostEffectCandidatePlan implements CandidateStatePlan {

    /** Plan identifier (enters the structured reload failure as the domain). */
    public static final String DOMAIN = "post-effects";

    private final PostEffectApplier applier;
    private final long generation;
    private final Set<String> previousIds;
    private final Map<String, PostEffectDeclaration> installs = new LinkedHashMap<>();
    private final Set<String> explicitlyRetired = new LinkedHashSet<>();
    private final List<String> collectionErrors = new ArrayList<>();
    private boolean finished;
    private List<PostEffectDeclaration> declarations = List.of();

    private PostEffectCandidatePlan(PostEffectApplier applier, long generation, Set<String> previousIds) {
        this.applier = applier;
        this.generation = generation;
        this.previousIds = previousIds == null ? Set.of() : new LinkedHashSet<>(previousIds);
    }

    /**
     * Starts a batch for the given candidate generation.
     *
     * @param applier     platform Adapter that owns the live renderer/resources
     * @param generation  candidate generation number (diagnostics/fingerprint)
     * @param previousIds ids owned by the previously active generation (retire set)
     */
    public static PostEffectCandidatePlan beginBatch(PostEffectApplier applier, long generation,
                                                     Set<String> previousIds) {
        if (applier == null) throw new NullPointerException("applier");
        return new PostEffectCandidatePlan(applier, generation, previousIds);
    }

    /** Candidate generation this batch belongs to. */
    public long generation() {
        return generation;
    }

    /**
     * Records one install declaration. Re-declaring the same id in one batch replaces the
     * earlier declaration (last write wins within a batch), matching the previous runtime
     * registration behaviour where a second {@code register} overwrote the first.
     */
    public synchronized void install(PostEffectDeclaration declaration) {
        if (declaration.kind() != PostEffectDeclaration.Kind.INSTALL) {
            throw new IllegalArgumentException("install() requires an INSTALL declaration: " + declaration.id());
        }
        installs.put(declaration.id(), declaration);
    }

    /**
     * Explicitly retires an id ({@code event.unregister}): the id is dropped from the
     * install set and released when the batch commits, even if no previous generation
     * declared it (a redundant retire is a no-op on the renderer).
     */
    public synchronized void retireDeclared(String id) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("post-effect declaration id must not be blank");
        }
        installs.remove(id);
        explicitlyRetired.add(id);
    }

    /**
     * Records a collection error. The batch stays collectible (later listeners still run, so
     * the observable order matches the event bus) but can never commit: the error is
     * reported by {@link #preflight()}, which fails the whole candidate.
     */
    public synchronized void fail(String reason) {
        if (reason == null || reason.isBlank()) return;
        if (!collectionErrors.contains(reason)) {
            collectionErrors.add(reason);
        }
    }

    /** First collection error (null when the batch collected cleanly). */
    public synchronized String collectionError() {
        return collectionErrors.isEmpty() ? null : collectionErrors.get(0);
    }

    /** Freezes collection and derives the full declaration list (installs + retires). */
    public synchronized List<PostEffectDeclaration> finish() {
        if (finished) return declarations;
        List<PostEffectDeclaration> result = new ArrayList<>(installs.values());
        // 显式 unregister 先于「旧 generation 未再声明」的隐式退役，两者并集去重。
        for (String explicit : explicitlyRetired) {
            result.add(PostEffectDeclaration.retire(explicit));
        }
        for (String previous : previousIds) {
            if (!installs.containsKey(previous) && !explicitlyRetired.contains(previous)) {
                result.add(PostEffectDeclaration.retire(previous));
            }
        }
        declarations = List.copyOf(result);
        finished = true;
        return declarations;
    }

    /** Declarations of this batch ({@link #finish()} must have been called). */
    public synchronized List<PostEffectDeclaration> declarations() {
        return declarations;
    }

    /** How many install declarations this batch carries (diagnostics). */
    public synchronized int installCount() {
        return installs.size();
    }

    /**
     * Deterministic fingerprint of the whole declaration list: adapter identity + order +
     * canonical form of each declaration. Used by the Adapter to skip an identical replay.
     */
    public synchronized String fingerprint() {
        StringBuilder sb = new StringBuilder("post-effects-plan[v1]:").append(applier.adapterId()).append(';');
        for (PostEffectDeclaration declaration : finish()) {
            sb.append(declaration.canonicalForm()).append(';');
        }
        if (!collectionErrors.isEmpty()) {
            sb.append("failed(").append(String.join("|", collectionErrors)).append(')');
        }
        return sb.toString();
    }

    @Override
    public String domain() {
        return DOMAIN;
    }

    /** Joint preflight: a poisoned batch (collection error or unfinished collection) is rejected whole. */
    @Override
    public void preflight() {
        String error = collectionError();
        if (error != null) {
            throw new IllegalStateException("post-effect declaration collection failed: " + error);
        }
        if (!finished) {
            throw new IllegalStateException("post-effect declaration batch was never finished");
        }
        applier.preflight(finish());
    }

    /**
     * Joint publish (commit point): hands the frozen declarations to the platform Adapter.
     * Per the {@link CandidateStatePlan} contract this must not throw after a passing
     * preflight; the Adapter records a failed apply in its diagnostics instead of leaving a
     * half-updated generation behind.
     */
    @Override
    public void publish() {
        applier.apply(finish());
    }
}
