package com.tkisor.nekojs.core.modification;

import com.tkisor.nekojs.core.state.CandidateStatePlan;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/**
 * Generation-scoped candidate plan for item and block modifications.
 * During collection, it only accumulates inert declarations, invisible to production routing,
 * other generations, and external nodes. Through {@link CandidateStatePlan}, it hooks into
 * candidate preflight and publication so that modifications commit or fail atomically
 * alongside global and shared write sets without partial commits.
 *
 * <p>Candidate collection creates this plan. Failure, preemption or Context termination
 * discards it with {@code GenerationGlobals.discard()} without publication;
 * {@link ModificationApplier} restores the baseline and applies the plan once at commit.
 *
 * <p>Collection failure flags: declaration errors during collection events (unknown targets,
 * invalid values, callback exceptions) are recorded on the plan rather than failing immediately,
 * keeping the observable order where listener errors do not abort event posting. A plan marked
 * with a failure always fails during preflight (failing the entire batch with no partial modifications);
 * silent staleness does not count as success.
 */
public final class ModificationCandidatePlan implements CandidateStatePlan {

    /** Domain identifier for the plan, used in failure results. */
    public static final String DOMAIN = "item-block-modification";

    private final ModificationApplier applier;
    private final List<ModificationDeclaration> declarations = new ArrayList<>();
    private volatile String collectionFailure;
    private volatile Throwable collectionError;

    public ModificationCandidatePlan(ModificationApplier applier) {
        this.applier = Objects.requireNonNull(applier, "applier");
    }

    @Override
    public String domain() {
        return DOMAIN;
    }

    /** Appends a declaration in collection order. A later declaration for the same target replaces earlier ones on apply. */
    public synchronized void add(ModificationDeclaration declaration) {
        if (collectionFailure != null) {
            return; // Keep only the first error for a failed batch and stop accumulating declarations
        }
        declarations.add(Objects.requireNonNull(declaration, "declaration"));
    }

    /**
     * Marks collection as failed (the first error wins, causing preflight to always fail).
     *
     * <p><b>Optional collector API</b>: used by domain collectors that catch collection errors
     * but still require the entire batch to fail. Recording the error on the plan instead of
     * throwing immediately preserves the observable order where posting does not abort.
     * <b>The production item and block modification path does not use this</b>:
     * {@code ModificationDomainOwner#collect} lets exceptions from {@code ItemModificationEventJS#modify}
     * propagate directly, allowing {@code ScriptManager} to attribute them to
     * {@code domain-collect:<domain>} and fail the entire batch during DOMAIN_PLAN.
     */
    public synchronized void fail(String reason, Throwable error) {
        if (collectionFailure == null) {
            collectionFailure = reason;
            collectionError = error;
        }
    }

    public synchronized List<ModificationDeclaration> declarations() {
        return List.copyOf(declarations);
    }

    public synchronized String collectionFailure() {
        return collectionFailure;
    }

    /** Returns whether the plan has no declarations and no failure. Publishing an empty plan only restores the baseline. */
    public synchronized boolean isEmpty() {
        return declarations.isEmpty() && collectionFailure == null;
    }

    /**
     * Deterministic text fingerprint of normalized declarations (declaration order plus normalized values
     * sorted by property name). Explicit setters and JavaBean-style property assignments produce the same
     * fingerprint after normalization. Used for equality skipping and assertions, not for persistence.
     */
    public synchronized String fingerprint() {
        return fingerprintOf(applier.adapterId(), declarations, collectionFailure);
    }

    /**
     * Static fingerprint calculation used by adapters for equality skipping and diagnostics.
     * Evaluates any declaration sequence with the same algorithm.
     */
    public static String fingerprintOf(String adapterId, List<ModificationDeclaration> declarations,
            String collectionFailure) {
        StringBuilder sb = new StringBuilder("modification-plan[v1]:");
        sb.append(adapterId).append(';');
        for (ModificationDeclaration declaration : declarations) {
            sb.append(declaration.kind()).append('|').append(declaration.targetId()).append('|');
            // Sort properties by name so write order within a declaration does not change semantics
            new TreeMap<>(declaration.properties()).forEach((name, value) ->
                    sb.append(name).append('=').append(canonicalValue(value)).append(','));
            sb.append(';');
        }
        if (collectionFailure != null) {
            sb.append("failed(").append(collectionFailure).append(')');
        }
        return sb.toString();
    }

    private static String canonicalValue(Object value) {
        if (value == null) {
            return "null";
        }
        if (value instanceof Map<?, ?> map) {
            StringBuilder sb = new StringBuilder("{");
            TreeMap<String, String> sorted = new TreeMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                sorted.put(String.valueOf(entry.getKey()), canonicalValue(entry.getValue()));
            }
            sorted.forEach((k, v) -> sb.append(k).append(':').append(v).append(','));
            return sb.append('}').toString();
        }
        if (value instanceof Double || value instanceof Float) {
            // Normalize numbers so values like 5 and 5.0 produce the same fingerprint regardless of int or double representation
            double d = ((Number) value).doubleValue();
            if (d == Math.rint(d) && !Double.isInfinite(d) && Math.abs(d) < 1e15) {
                return String.valueOf((long) d);
            }
            return String.valueOf(d);
        }
        return String.valueOf(value);
    }

    // ---- CandidateStatePlan: preflight and publication ----

    @Override
    public void preflight() {
        String failure = collectionFailure;
        if (failure != null) {
            throw new IllegalStateException("modification collection failed: " + failure,
                    collectionError);
        }
        applier.preflight(declarations());
    }

    @Override
    public void publish() {
        applier.apply(declarations());
    }
}
