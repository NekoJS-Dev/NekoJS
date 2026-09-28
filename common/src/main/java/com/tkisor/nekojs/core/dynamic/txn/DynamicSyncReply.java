package com.tkisor.nekojs.core.dynamic.txn;

/**
 * Client-to-server reply of the Dynamic Registry batch transaction protocol (ticket 21
 * platform wiring): the ack of a PREPARE / STATE_SYNC message and the post-commit
 * activation report of a COMMIT or catch-up STATE_SYNC. Same wire-safety rule as
 * {@link DynamicSyncMessage} — every field is a string/enum/number/boolean, so the
 * record crosses the in-JVM test transport and the platform payload codec without a
 * second model.
 *
 * <p>Like the server messages, a reply is a protocol observation, not a distributed
 * atomicity proof: an ack says the participant accepted the target state; an
 * activation report says how that participant's own activation attempt ended.
 */
public record DynamicSyncReply(Kind kind, long generation, Boolean accepted, String reason,
        Boolean activated, String detail) {

    /** Reply kinds; see class doc for direction and semantics. */
    public enum Kind { ACK, ACTIVATION_REPORT }

    /** Answers a PREPARE or STATE_SYNC: accepted plus a rejection reason when declined. */
    public static DynamicSyncReply ack(long generation, boolean accepted, String reason) {
        return new DynamicSyncReply(Kind.ACK, generation, accepted, reason, null, null);
    }

    /**
     * Reports the local outcome of a COMMIT / STATE_SYNC activation attempt (the
     * participant executes its own activation when the commit arrives; a failure cannot
     * un-commit the server batch, so the server records it and repairs with a fresh
     * catch-up STATE_SYNC).
     */
    public static DynamicSyncReply activationReport(long generation, boolean activated, String detail) {
        return new DynamicSyncReply(Kind.ACTIVATION_REPORT, generation, null, null, activated, detail);
    }

    public DynamicSyncReply {
        if (kind == Kind.ACK) {
            if (accepted == null) {
                throw new IllegalArgumentException("an ACK reply must carry its accepted flag");
            }
            activated = null;
            detail = null;
        } else {
            if (activated == null) {
                throw new IllegalArgumentException("an ACTIVATION_REPORT reply must carry its activated flag");
            }
            accepted = null;
            reason = null;
        }
    }
}
