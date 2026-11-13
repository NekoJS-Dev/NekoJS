package com.tkisor.nekojs.core.dynamic.plan;

import com.tkisor.nekojs.core.state.CandidateStatePlan;
import com.tkisor.nekojs.core.state.GlobalStateException;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A batch of generation-scoped <b>inert</b> dynamic registration candidate plans. During the
 * candidate phase it only collects definitions and errors; the preflight performs same-key
 * conflict detection. Only at the commit point, through the {@link CandidateStatePlan}, is the
 * batch published into the {@link DynamicRegistryPlanStore}.
 *
 * <p><b>Inert guarantee:</b> this class and the whole plan package hold no MC or loader types.
 * The candidate phase does not modify live registries, register production callbacks, or publish
 * external bindings ahead of time. Adapter requests are described by
 * {@link DynamicAdapterRequest}, which has no execution channel. If the candidate phase fails the
 * plan is discarded and nothing is written to the store: cleanup is the plan object becoming
 * unreachable, and no temporary resource is left behind.
 *
 * <p><b>Conflict semantics (version 1):</b> a definition change (different fingerprint) for an
 * already-exposed key, whether active or stale/retired, <b>fails the entire batch</b> and the old
 * active definition keeps serving. Removal, replacement and modification do not exist in this
 * plan; the action set is closed to registration. For duplicate declarations of the same key in
 * the same batch, the same fingerprint is an idempotent deduplication and a different fingerprint
 * fails fast during collection. Identical definitions across repeated reloads have the same
 * fingerprint, so re-declaring one is only re-claiming and is not a conflict.
 */
public final class DynamicCandidateRegistryPlan implements CandidateStatePlan {

    /** Domain prefix in joint failure results (state-plan-preflight:<domain> / ReloadFailureReport.domain). */
    public static final String DOMAIN = "dynamic-registry";

    private final DynamicRegistryPlanStore store;
    private final long generation;
    private final Map<String, DynamicDefinition> definitions = new LinkedHashMap<>();
    private final Map<String, String> owners = new LinkedHashMap<>();
    private final List<String> collectionErrors = new ArrayList<>();
    private boolean preflightPassed;
    private boolean published;

    DynamicCandidateRegistryPlan(DynamicRegistryPlanStore store, long generation) {
        this.store = store;
        this.generation = generation;
    }

    // ---- Collection: event payloads and candidate collector calls ----

    /**
     * Collects a definition. Same batch, same key: same fingerprint → idempotent return of the
     * already-collected entry; different → fail-fast during collection (with locatable difference
     * between the two definitions). The caller (payload) is responsible for marking the exception
     * as a collection error (poisoning the entire batch) before propagating to listeners.
     */
    public DynamicDefinition add(
            DynamicDefinitionType type, String rawId, DynamicDefinitionBuilder builder,
            String ownerScriptId, String origin) {
        DynamicDefinition definition = DynamicDefinition.of(type, rawId, builder);
        return stageUnique(definition, ownerScriptId);
    }

    /**
     * Stages an already-normalized definition that came from a server sync message rather than
     * from a builder callback. Same-batch dedupe and conflict semantics as {@link #add}.
     */
    public DynamicDefinition stageExistingDefinition(DynamicDefinition definition, String ownerScriptId) {
        return stageUnique(definition, ownerScriptId);
    }

    /**
     * Same-batch dedupe/conflict gate shared by builder collection and sync staging: an
     * identical fingerprint is an idempotent return of the already-staged entry; a
     * different definition for the same key fails the whole batch with both owners and
     * both definitions (remove/replace/modify is not part of the first version).
     */
    private DynamicDefinition stageUnique(DynamicDefinition definition, String ownerScriptId) {
        String key = definition.key();
        DynamicDefinition existing = definitions.get(key);
        if (existing != null) {
            if (!existing.fingerprint().equals(definition.fingerprint())) {
                throw new IllegalArgumentException(
                        "Duplicate declaration of '" + key + "' in the same batch with a different definition: "
                                + existing.describe() + " (from " + owners.get(key) + ") vs "
                                + definition.describe() + " (from " + safeOwner(ownerScriptId) + ");"
                                + " the whole batch fails — remove/replace/modify is not part of the first version");
            }
            return existing;
        }
        definitions.put(key, definition);
        owners.put(key, safeOwner(ownerScriptId));
        return definition;
    }

    /** Records a collection-phase error (payload capture poisons the entire batch: preflight is guaranteed to fail, batch not published). */
    public void noteCollectionError(String scriptId, Throwable error) {
        collectionErrors.add("[" + safeOwner(scriptId) + "] " + error.getClass().getSimpleName() + ": "
                + error.getMessage());
    }

    // ---- CandidateStatePlan ----

    @Override
    public String domain() {
        return DOMAIN;
    }

    /**
     * Runs the preflight for the STATE_PLAN phase: a collection error fails the whole batch, and
     * for each definition whose key is already exposed with a different fingerprint the whole
     * batch fails with a conflict, leaving the old active definition in service. Throws
     * {@link GlobalStateException} with the domain attributed precisely and produces no side
     * effect visible outside the plan.
     */
    @Override
    public void preflight() {
        if (!collectionErrors.isEmpty()) {
            throw new GlobalStateException("dynamic-registry-collection",
                    "dynamic registry candidate batch " + generation + " failed collection: "
                            + String.join("; ", collectionErrors));
        }
        for (DynamicDefinition definition : definitions.values()) {
            DynamicRegistryPlanStore.ExposedEntry exposed = store.exposedEntry(definition.key());
            if (exposed == null) {
                continue;
            }
            if (!exposed.definition().fingerprint().equals(definition.fingerprint())) {
                boolean retired = store.isRetired(definition.key());
                throw new GlobalStateException("dynamic-registry-conflict",
                        "definition of '" + definition.key() + "' changed" + (retired ? " (stale/retired)" : "")
                                + ": exposed fingerprint " + exposed.definition().fingerprint()
                                + " (" + exposed.definition().describe() + ", owner " + exposed.ownerScriptId()
                                + ", generation " + exposed.committedGeneration()
                                + ") vs batch " + generation + " fingerprint " + definition.fingerprint()
                                + " (" + definition.describe() + "); the whole batch fails and the old active"
                                + " definitions keep serving — replace/update is not part of the first version");
            }
        }
        preflightPassed = true;
    }

    /**
     * Publishes the batch at the commit point: writes it into the store, refreshing claims,
     * marking stale entries and updating the exposed ledger. Once the preflight has passed this
     * method does not throw; it only performs the deterministic writes the preflight already
     * covered. Calling publish without a preflight is rejected, since there is no other publish
     * path.
     */
    @Override
    public void publish() {
        if (published) {
            throw new IllegalStateException("dynamic registry plan " + generation + " already published");
        }
        if (!preflightPassed) {
            throw new IllegalStateException(
                    "dynamic registry plan " + generation + " was never preflighted; publish joins the joint"
                            + " candidate boundary only (STATE_PLAN preflight → commit publish)");
        }
        store.commitBatch(this);
        published = true;
    }

    // ---- Observation surface: tests, diagnostics and Adapter requests ----

    /** The generation of the batch, which anchors its generation-scoped semantics. */
    public long generation() {
        return generation;
    }

    /** The definitions in the batch, in submission order, as a defensive copy. */
    public List<DynamicDefinition> definitions() {
        return List.copyOf(definitions.values());
    }

    /** The owner scriptId of a key in the batch, or {@code <unknown>} when no owner is recorded. */
    public String ownerOf(String key) {
        return owners.getOrDefault(key, "<unknown>");
    }

    /** Whether the batch has collection errors; a set flag marks it as poisoned. */
    public boolean hasCollectionErrors() {
        return !collectionErrors.isEmpty();
    }

    /** A copy of the collection errors, for diagnostics. */
    public List<String> collectionErrors() {
        return List.copyOf(collectionErrors);
    }

    /** Whether this plan has been published; observe it to see a successful commit. */
    public boolean isPublished() {
        return published;
    }

    /**
     * The inert Adapter requests of this batch: one {@code register} description per definition,
     * generation-scoped. This is not an execution channel; Adapter activation is decided by the
     * transaction and sync gate.
     */
    public List<DynamicAdapterRequest> adapterRequests() {
        List<DynamicAdapterRequest> requests = new ArrayList<>();
        definitions.values().forEach(definition ->
                requests.add(new DynamicAdapterRequest(definition, generation, owners.get(definition.key()))));
        return requests;
    }

    /** Whether the batch declares a key, for tests and diagnostics. */
    public boolean declaresKey(String key) {
        return definitions.containsKey(key);
    }

    private static String safeOwner(String ownerScriptId) {
        return ownerScriptId == null || ownerScriptId.isBlank() ? "<unknown>" : ownerScriptId;
    }
}
