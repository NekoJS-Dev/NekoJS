package com.tkisor.nekojs.core.dynamic.txn;

import com.tkisor.nekojs.core.dynamic.plan.DynamicDefinition;

import java.util.List;

/**
 * Wire-safe message of the Dynamic Registry batch transaction protocol (ticket 21).
 * Every field is a string/enum/number, so the same record crosses the in-JVM test
 * transport and the platform payload codec without a second model.
 *
 * <p><b>prepare/ack are protocol phases, not a distributed atomicity proof</b>: the
 * protocol guarantees only that a batch becomes visible on a node when that node has
 * accepted the prepare and then received the commit for the same generation. Success
 * assertions must observe final consistent visibility on every activated node (or an
 * explicit reject/degrade result), never the mere existence of these messages.
 *
 * <p>Message kinds and their direction:
 * <ul>
 *   <li>{@link Kind#PREPARE} (server→client): full target definition set for a batch
 *       generation; the client validates it and answers with an ack;</li>
 *   <li>{@link Kind#STATE_SYNC} (server→client): catch-up of the already-activated
 *       state to a participant that joined after activation — never activates a
 *       generation newer than the server's activated generation;</li>
 *   <li>{@link Kind#COMMIT} (server→client, broadcast): the batch is activated on the
 *       server; participants activate their staged prepare of the same generation;</li>
 *   <li>{@link Kind#ABORT} (server→client, broadcast): the batch is not activated
 *       anywhere; participants discard their staged prepare.</li>
 * </ul>
 *
 * <p>The definition set is always <b>full state</b>, not a delta: the server's
 * first-version conflict rule keeps {@code key → fingerprint} immutable once exposed,
 * so a full set is idempotent for up-to-date clients and self-healing for clients
 * that joined late (they simply activate the entries they are missing).
 */
public record DynamicSyncMessage(Kind kind, long generation, List<Entry> entries, String reason) {

    /** Protocol message kinds; see class doc for direction and semantics. */
    public enum Kind { PREPARE, STATE_SYNC, COMMIT, ABORT }

    /** One wire entry: the fully-normalized definition plus its declaring owner script. */
    public record Entry(DynamicDefinition definition, String ownerScriptId) {

        /** Stable conflict/comparison key ({@code minecraft:item|mymod:ruby}). */
        public String key() {
            return definition.key();
        }
    }

    public DynamicSyncMessage {
        entries = List.copyOf(entries == null ? List.of() : entries);
    }

    static DynamicSyncMessage prepare(long generation, List<Entry> entries) {
        return new DynamicSyncMessage(Kind.PREPARE, generation, entries, null);
    }

    static DynamicSyncMessage stateSync(long generation, List<Entry> entries) {
        return new DynamicSyncMessage(Kind.STATE_SYNC, generation, entries, null);
    }

    static DynamicSyncMessage commit(long generation) {
        return new DynamicSyncMessage(Kind.COMMIT, generation, List.of(), null);
    }

    static DynamicSyncMessage abort(long generation, String reason) {
        return new DynamicSyncMessage(Kind.ABORT, generation, List.of(), reason);
    }
}
