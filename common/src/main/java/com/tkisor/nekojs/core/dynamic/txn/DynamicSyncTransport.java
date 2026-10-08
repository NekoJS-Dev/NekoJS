package com.tkisor.nekojs.core.dynamic.txn;

import java.util.List;

/**
 * Delivery seam between the Dynamic Registry transaction coordinator and the network
 * owner's payload transport (ticket 21, built on ticket 17's register-once payload
 * foundation). Implementations live in the version tree and wrap the existing
 * configuration/play payload channel; they never register a second channel and never touch a
 * script Context directly.
 *
 * <p>Contract:
 * <ul>
 *   <li>all methods are called on the transaction owner thread (the server main
 *       thread in production); implementations handle any thread hop themselves;</li>
 *   <li>{@link #participants()} lists the <b>remote</b> participants that must
 *       complete sync before a batch may commit — same-JVM/integrated participants
 *       are excluded by the implementation (they share the registry), which is a
 *       platform fact owned by the Adapter, not by the coordinator;</li>
 *   <li>implementations must not throw: a delivery to a vanished participant is
 *       dropped by the transport (the participant-left/ack-timeout paths already
 *       define the outcome), and a transport that is not installed records that
 *       fact itself.</li>
 * </ul>
 */
public interface DynamicSyncTransport extends AutoCloseable {

    /** Remote participant ids (stable across the transaction; typically player UUIDs). */
    List<String> participants();

    /** Delivers a PREPARE or STATE_SYNC message to one participant. */
    void send(String participantId, DynamicSyncMessage message);

    /** Delivers a COMMIT or ABORT message to every current participant. */
    void broadcast(DynamicSyncMessage message);

    /** Releases connection-scoped resources when the activation engine's owner stops. */
    @Override
    default void close() {}
}
