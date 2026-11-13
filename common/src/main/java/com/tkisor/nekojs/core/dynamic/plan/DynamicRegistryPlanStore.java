package com.tkisor.nekojs.core.dynamic.plan;

import com.tkisor.nekojs.core.dynamic.DynamicRegistrationBookkeeping;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Committed state of the dynamic registration plans (the Registry Runtime ledger):
 * <b>one instance per store, zero static mutable state</b> — production keeps a single instance
 * in the platform Adapter, while tests and harnesses construct isolated instances freely.
 *
 * <p>It holds two complementary ledgers:
 * <ul>
 *   <li><b>the exposed definition ledger</b> ({@code key → ExposedEntry}): the fingerprint and
 *       owner of every committed definition. <b>A normal reload does not remove entries</b> —
 *       items a script no longer declares stay on the ledger (stale/retired) and their
 *       fingerprint keeps participating in conflict detection (a change to an already exposed
 *       key is always a first-version conflict, so being stale never allows a replace).
 *       Explicit cleanup is later work, out of scope here;</li>
 *   <li><b>the claim/stale/mode ledger</b> (one {@link DynamicRegistrationBookkeeping} per type,
 *       the prior-art class that shares its label with the existing
 *       {@code DynamicRegistries}): on a successful commit beginReload marks everything stale and
 *       each claimed entry is then refreshed — claim, stale and mode are observable from there.</li>
 * </ul>
 *
 * <p>There is exactly one write path: {@link #commitBatch}, called by
 * {@link DynamicCandidateRegistryPlan#publish()} at the commit point. A failed or cancelled
 * candidate never gets here — the ledger is untouched and the definitions active before the batch
 * keep serving. This class holds no MC types, so it cannot modify the live registry.
 */
public final class DynamicRegistryPlanStore {

    /** Ledger entry for one committed (exposed) definition. */
    public record ExposedEntry(
            DynamicDefinition definition,
            String ownerScriptId,
            long committedGeneration) {}

    /** Registry label, the same one {@code DynamicRegistries} uses; it keys the claim/stale/mode ledger. */
    public static final String LABEL_ITEM = "item";
    public static final String LABEL_SOUND_EVENT = "sound_event";
    public static final String LABEL_MOB_EFFECT = "mob_effect";

    private final Map<String, ExposedEntry> exposed = new LinkedHashMap<>();
    private final DynamicRegistrationBookkeeping itemBookkeeping = new DynamicRegistrationBookkeeping(LABEL_ITEM);
    private final DynamicRegistrationBookkeeping soundEventBookkeeping =
            new DynamicRegistrationBookkeeping(LABEL_SOUND_EVENT);
    private final DynamicRegistrationBookkeeping mobEffectBookkeeping =
            new DynamicRegistrationBookkeeping(LABEL_MOB_EFFECT);
    private long generation;
    private long committedGeneration = -1;
    private Map<String, ExposedEntry> lastCommittedBatch = Map.of();

    /** Opens a candidate plan batch and increments the generation counter; the batch is scoped to that generation. */
    public synchronized DynamicCandidateRegistryPlan beginBatch() {
        return new DynamicCandidateRegistryPlan(this, ++generation);
    }

    /** Whether no definition has ever been committed; reload integration uses it to decide whether an
     * empty candidate plan needs to be attached. */
    public synchronized boolean isEmpty() {
        return exposed.isEmpty();
    }

    /** Committed generation number, or -1 when nothing was ever committed. Only a successful commit
     * advances it, so this is how observers see the published plan. */
    public synchronized long committedGeneration() {
        return committedGeneration;
    }

    /** Exposed definition for one plan key, the input to conflict detection; returns null when the key is not exposed. */
    public synchronized ExposedEntry exposedEntry(String key) {
        return exposed.get(key);
    }

    /** Defensive snapshot of every exposed key, stale ones included, for diagnostics and tests. */
    public synchronized Map<String, ExposedEntry> exposedSnapshot() {
        return new LinkedHashMap<>(exposed);
    }

    /** Definitions from the last successfully committed batch, which is how the published plan is observed. */
    public synchronized List<DynamicDefinition> lastCommittedBatch() {
        return List.copyOf(lastCommittedBatch.values().stream().map(ExposedEntry::definition).toList());
    }

    /** The claim/stale/mode ledger for one type, the observation point for the Registry Runtime and the Adapter. */
    public DynamicRegistrationBookkeeping bookkeeping(DynamicDefinitionType type) {
        return switch (type) {
            case ITEM -> itemBookkeeping;
            case SOUND_EVENT -> soundEventBookkeeping;
            case MOB_EFFECT -> mobEffectBookkeeping;
        };
    }

    /** Stale ids of one type. A normal reload only marks them; it never deletes anything physically. */
    public List<String> staleIds(DynamicDefinitionType type) {
        return bookkeeping(type).staleIds();
    }

    /** Keys of stale or retired definitions across all types, in {@code minecraft:item|mymod:old} form. */
    public synchronized List<String> retiredKeys() {
        List<String> retired = new ArrayList<>();
        for (Map.Entry<String, ExposedEntry> entry : exposed.entrySet()) {
            DynamicDefinition definition = entry.getValue().definition();
            if (bookkeeping(definition.type()).staleIds().contains(definition.id())) {
                retired.add(entry.getKey());
            }
        }
        return retired;
    }

    /** Snapshot of every tracked id, stale ones included, as {@code label:id}; used for diagnostics. */
    public synchronized Set<String> trackedClaims() {
        Set<String> claims = new LinkedHashSet<>();
        for (DynamicDefinitionType type : DynamicDefinitionType.values()) {
            DynamicRegistrationBookkeeping bookkeeping = bookkeeping(type);
            for (String id : bookkeeping.trackedIds()) {
                claims.add(bookkeeping.registry() + ":" + id);
            }
        }
        return claims;
    }

    /** Current claim owner/mode of one exposed definition; null means there is no ledger entry. */
    public synchronized DynamicRegistrationBookkeeping.Entry claimOf(DynamicDefinitionType type, String id) {
        return bookkeeping(type).entry(id);
    }

    /**
     * Commits one batch of plans. This is the only write path and is called by
     * {@link DynamicCandidateRegistryPlan#publish()} at the commit point. Preflight has already
     * passed, so this method must not throw.
     *
     * <p>Order: every ledger runs beginReload first (marking entries stale), then each claim is
     * refreshed entry by entry, so a re-declaration clears stale. Exposed entries the batch does
     * not declare again therefore stay stale/retired; neither the ledger entries nor the
     * definitions are ever deleted physically.
     */
    synchronized void commitBatch(DynamicCandidateRegistryPlan plan) {
        long batchGeneration = plan.generation();
        itemBookkeeping.beginReload();
        soundEventBookkeeping.beginReload();
        mobEffectBookkeeping.beginReload();
        Map<String, ExposedEntry> batch = new LinkedHashMap<>();
        for (DynamicDefinition definition : plan.definitions()) {
            String owner = plan.ownerOf(definition.key());
            bookkeeping(definition.type()).claim(definition.id(), owner, definition.mode());
            ExposedEntry entry = new ExposedEntry(definition, owner, batchGeneration);
            exposed.put(definition.key(), entry);
            batch.put(definition.key(), entry);
        }
        this.lastCommittedBatch = batch;
        this.committedGeneration = batchGeneration;
    }

    /** For conflict messages: whether this key is stale right now, meaning its definition was declared
     * before but not by the current batch. */
    synchronized boolean isRetired(String key) {
        ExposedEntry entry = exposed.get(key);
        if (entry == null) {
            return false;
        }
        DynamicDefinition definition = entry.definition();
        DynamicRegistrationBookkeeping.Entry claim = bookkeeping(definition.type()).entry(definition.id());
        return claim != null && claim.stale();
    }

}
