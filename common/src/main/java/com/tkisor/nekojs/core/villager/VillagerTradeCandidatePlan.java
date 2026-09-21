package com.tkisor.nekojs.core.villager;

import com.tkisor.nekojs.core.state.CandidateStatePlan;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Generation-scoped, inert candidate plan for villager trade declarations (ticket 22 AC1/AC4).
 *
 * <p>Collection only accumulates declarations (and, for the reload sub-event, the trade sets a
 * script marked as no longer managed) — the plan holds pure JVM data and cannot mutate a live
 * registry. It joins the same joint preflight/publish boundary as the global/shared write set
 * ({@code CandidateStatePlan}), so a failed candidate publishes nothing and the old active
 * trades keep serving.
 *
 * <p>Idempotence inside one batch: declarations are keyed by
 * {@link VillagerTradeDeclaration#key()}, so the same declaration twice in one batch (same
 * script or not) becomes one listing.
 */
public final class VillagerTradeCandidatePlan implements CandidateStatePlan {

    /** Domain id used by reload failure reports and diagnostics. */
    public static final String DOMAIN = "villager-trades";

    private final VillagerTradeApplier applier;
    private final VillagerTradeDomainState state;
    private final Map<String, VillagerTradeDeclaration> declarations = new LinkedHashMap<>();
    private final Set<String> obsoleteTradeSets = new LinkedHashSet<>();
    private volatile String collectionFailure;
    private volatile Throwable collectionError;
    private volatile boolean published;

    public VillagerTradeCandidatePlan(VillagerTradeApplier applier, VillagerTradeDomainState state) {
        this.applier = java.util.Objects.requireNonNull(applier, "applier");
        this.state = java.util.Objects.requireNonNull(state, "state");
    }

    @Override
    public String domain() {
        return DOMAIN;
    }

    // ---- collection ----

    /** Adds one declaration; a repeated canonical key keeps the first (idempotent re-declaration). */
    public synchronized void add(VillagerTradeDeclaration declaration) {
        if (collectionFailure != null) {
            return; // an already failed batch keeps only its first error
        }
        declarations.putIfAbsent(declaration.key(), declaration);
    }

    /** Marks a trade set as no longer managed by scripts (runs in the commit, never mid-collection). */
    public synchronized void markObsoleteTradeSet(String tradeSetId) {
        if (tradeSetId == null || tradeSetId.isBlank()) {
            throw new IllegalArgumentException("obsolete trade set id must not be empty");
        }
        obsoleteTradeSets.add(tradeSetId.trim());
    }

    /** First collection error wins; the plan then fails its joint preflight (no silent stale). */
    public synchronized void fail(String reason, Throwable error) {
        if (collectionFailure == null) {
            collectionFailure = reason;
            collectionError = error;
        }
    }

    public synchronized List<VillagerTradeDeclaration> declarations() {
        return List.copyOf(declarations.values());
    }

    public synchronized Set<String> obsoleteTradeSets() {
        return Set.copyOf(obsoleteTradeSets);
    }

    public synchronized String collectionFailure() {
        return collectionFailure;
    }

    public synchronized boolean isEmpty() {
        return declarations.isEmpty() && obsoleteTradeSets.isEmpty() && collectionFailure == null;
    }

    /** Declared keys per trade set (unrestored/stale bookkeeping input). */
    public synchronized Map<String, List<String>> declaredKeysByTradeSet() {
        Map<String, List<String>> bySet = new LinkedHashMap<>();
        for (VillagerTradeDeclaration declaration : declarations.values()) {
            bySet.computeIfAbsent(declaration.tradeSetId(), key -> new java.util.ArrayList<>())
                    .add(declaration.key());
        }
        return bySet;
    }

    /** Deterministic fingerprint of the batch (parity assertions and equivalent-skip diagnostics). */
    public synchronized String fingerprint() {
        StringBuilder builder = new StringBuilder("villager-trade-plan[v1]:").append(applier.adapterId()).append(';');
        declarations.values().forEach(declaration -> builder.append(declaration.key()).append(';'));
        new java.util.TreeSet<>(obsoleteTradeSets).forEach(set -> builder.append("obsolete:").append(set).append(';'));
        if (collectionFailure != null) {
            builder.append("failed(").append(collectionFailure).append(')');
        }
        return builder.toString();
    }

    // ---- CandidateStatePlan: joint preflight / joint publish ----

    @Override
    public void preflight() {
        String failure = collectionFailure;
        if (failure != null) {
            throw new IllegalStateException("villager trade collection failed: " + failure, collectionError);
        }
        applier.preflight(declarations());
    }

    @Override
    public void publish() {
        if (published) {
            throw new IllegalStateException("villager trade plan already published");
        }
        applier.apply(declarations(), Set.copyOf(obsoleteTradeSets));
        published = true;
        state.noteCommitted(this, applier.committedSnapshot());
    }

    /** Whether this batch reached the commit point (diagnostics/tests). */
    public boolean isPublished() {
        return published;
    }
}
