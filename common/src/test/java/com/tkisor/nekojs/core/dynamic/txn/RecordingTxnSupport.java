package com.tkisor.nekojs.core.dynamic.txn;

import com.tkisor.nekojs.core.dynamic.plan.DynamicAdapterRequest;
import com.tkisor.nekojs.core.dynamic.plan.DynamicDefinition;
import com.tkisor.nekojs.core.dynamic.plan.DynamicDefinitionType;
import com.tkisor.nekojs.core.dynamic.plan.DynamicItemBuilder;
import com.tkisor.nekojs.core.dynamic.plan.DynamicMobEffectBuilder;
import com.tkisor.nekojs.core.dynamic.plan.DynamicRegistryPlanStore;
import com.tkisor.nekojs.core.dynamic.plan.DynamicSoundEventBuilder;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Shared fixtures of the ticket 21 transaction tests: a recording Adapter (the only
 * "surgery" the JVM tests may observe), a scripted Transport that records every
 * outbound message, and definition builders for the frozen three candidate types.
 */
final class RecordingTxnSupport {

    private RecordingTxnSupport() {}

    /** Adapter double: records calls, holds the "live registry" fingerprint map. */
    static final class RecordingAdapter implements DynamicRegistryAdapter {
        final List<List<DynamicAdapterRequest>> prepareCalls = new ArrayList<>();
        final List<List<DynamicAdapterRequest>> activateCalls = new ArrayList<>();
        final List<List<DynamicAdapterRequest>> rollbackCalls = new ArrayList<>();
        /** The simulated live registry: key → fingerprint (the activated state). */
        final Map<String, String> live = new LinkedHashMap<>();
        RuntimeException prepareRejection;
        RuntimeException activateFailure;
        RuntimeException rollbackFailure;
        /** When set, a failing activate applies the first request before throwing (partial surgery). */
        boolean mutatePartiallyBeforeFailure;
        private Map<String, String> liveAtPrepare;

        @Override
        public void prepareActivation(List<DynamicAdapterRequest> requests) {
            prepareCalls.add(List.copyOf(requests));
            if (prepareRejection != null) {
                throw prepareRejection;
            }
            liveAtPrepare = new LinkedHashMap<>(live);
        }

        @Override
        public void activate(List<DynamicAdapterRequest> requests) {
            activateCalls.add(List.copyOf(requests));
            if (activateFailure != null) {
                if (mutatePartiallyBeforeFailure && !requests.isEmpty()) {
                    DynamicAdapterRequest first = requests.get(0);
                    live.put(first.registryKey() + "|" + first.id(), first.fingerprint());
                }
                throw activateFailure;
            }
            requests.forEach(request -> live.put(request.registryKey() + "|" + request.id(), request.fingerprint()));
        }

        @Override
        public void rollbackActivation(List<DynamicAdapterRequest> requests) {
            rollbackCalls.add(List.copyOf(requests));
            if (rollbackFailure != null) {
                throw rollbackFailure;
            }
            if (liveAtPrepare != null) {
                live.clear();
                live.putAll(liveAtPrepare);
            }
        }
    }

    /** One recorded outbound transport message (target null = broadcast). */
    record Sent(String participantId, DynamicSyncMessage message) {
        DynamicSyncMessage.Kind kind() {
            return message.kind();
        }
    }

    /** Transport double: fixed participant set, records every send/broadcast. */
    static final class ScriptedTransport implements DynamicSyncTransport {
        final List<String> participants;
        final List<Sent> sent = new ArrayList<>();

        ScriptedTransport(String... participants) {
            this.participants = List.of(participants);
        }

        @Override
        public List<String> participants() {
            return participants;
        }

        @Override
        public void send(String participantId, DynamicSyncMessage message) {
            sent.add(new Sent(participantId, message));
        }

        @Override
        public void broadcast(DynamicSyncMessage message) {
            sent.add(new Sent(null, message));
        }

        List<Sent> ofKind(DynamicSyncMessage.Kind kind) {
            return sent.stream().filter(s -> s.kind() == kind).toList();
        }
    }

    // ---- definition factories (normalized, same path as production collection) ----

    static DynamicDefinition item(String id, int maxStackSize, String rarity) {
        DynamicItemBuilder builder = new DynamicItemBuilder();
        builder.setMaxStackSize(maxStackSize);
        builder.setRarity(rarity);
        return DynamicDefinition.of(DynamicDefinitionType.ITEM, id, builder);
    }

    static DynamicDefinition soundEvent(String id, Float fixedRange) {
        DynamicSoundEventBuilder builder = new DynamicSoundEventBuilder();
        builder.setFixedRange(fixedRange);
        return DynamicDefinition.of(DynamicDefinitionType.SOUND_EVENT, id, builder);
    }

    static DynamicDefinition mobEffect(String id, String category, int color) {
        DynamicMobEffectBuilder builder = new DynamicMobEffectBuilder();
        builder.setCategory(category);
        builder.setColor(color);
        return DynamicDefinition.of(DynamicDefinitionType.MOB_EFFECT, id, builder);
    }

    /** Commits a declaration batch into a store (the ledger-commit side of a reload). */
    static void commitBatch(DynamicRegistryPlanStore store, DynamicDefinition... definitions) {
        var plan = store.beginBatch();
        for (DynamicDefinition definition : definitions) {
            plan.stageExistingDefinition(definition, "server_scripts/entry.js");
        }
        plan.preflight();
        plan.publish();
    }

    /** Full target state of a store as wire entries (production computes it the same way). */
    static List<DynamicSyncMessage.Entry> targetState(DynamicRegistryPlanStore store) {
        List<DynamicSyncMessage.Entry> entries = new ArrayList<>();
        store.exposedSnapshot().values()
                .forEach(entry -> entries.add(new DynamicSyncMessage.Entry(entry.definition(), entry.ownerScriptId())));
        return entries;
    }
}
