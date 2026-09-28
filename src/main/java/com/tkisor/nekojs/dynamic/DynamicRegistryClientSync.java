//? if neoforge {
//? if >=26 {
package com.tkisor.nekojs.dynamic;

import com.tkisor.nekojs.NekoJS;
import com.tkisor.nekojs.core.dynamic.txn.DynamicRegistryClientParticipant;
import com.tkisor.nekojs.core.dynamic.txn.DynamicSyncMessage;
import com.tkisor.nekojs.core.dynamic.txn.DynamicSyncReply;
import com.tkisor.nekojs.core.dynamic.txn.DynamicSyncWireCodec;
import com.tkisor.nekojs.core.fs.ClassFilter;
import com.tkisor.nekojs.network.DynamicRegistrySyncPacket;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Ticket 21 platform wiring (client node): holds this node's
 * {@link DynamicRegistryClientParticipant}, turns server S2C protocol messages (through
 * the ticket 17 receive discipline: network thread → {@code enqueueWork} → client main
 * thread) into local all-or-nothing activation, and answers with ACK /
 * ACTIVATION_REPORT (same payload type in the reverse direction).
 *
 * <p><b>Registry value source</b>: the process-wide read-only projection
 * {@code RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY)} — surgery
 * still goes only through the {@link DynamicRegistries} freeze-bypassing path. If the
 * tag backing of a fire-resistant item is not bound yet at receipt time, activation
 * fails per contract and reports back (the server's catch-up STATE_SYNC repairs it; no
 * silent divergence).
 *
 * <p><b>Gate</b>: with this client's {@code [dynamicRegistry]} disabled, PREPARE and
 * STATE_SYNC are rejected outright (ack-rejected; the whole batch aborts on the server)
 * — both ends must explicitly opt in.
 *
 * <p>Client-only assembly ({@code NekoJSMod#registerClient} calls {@link #install()});
 * {@code ClientPlayerNetworkEvent} is a client-only event class, so a dedicated server
 * never loads this class.
 */
public final class DynamicRegistryClientSync {

    private static volatile DynamicRegistryClientParticipant participant;

    private DynamicRegistryClientSync() {}

    /** Client-dist assembly (drop staged prepare on disconnect; ledger and watermark survive for rejoin catch-up). */
    public static void install() {
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(DynamicRegistryClientSync::onLoggingOut);
    }

    /** Client handler for S2C protocol messages; registered by Nf26xPlatformCompat. */
    public static void handleOnClient(DynamicRegistrySyncPacket payload, IPayloadContext context) {
        context.enqueueWork(() -> onServerMessage(payload.json()));
    }

    // ---- protocol dispatch (client main thread) ----

    private static void onServerMessage(String json) {
        DynamicSyncMessage message;
        try {
            message = DynamicSyncWireCodec.decodeServerMessage(json);
        } catch (IllegalArgumentException e) {
            NekoJS.LOGGER.warn("Dropping malformed dynamic registry sync message from server: {}", e.getMessage());
            return;
        }
        DynamicRegistryClientParticipant node = participant();
        switch (message.kind()) {
            case PREPARE -> {
                DynamicRegistryClientParticipant.PrepareDecision decision = gateRejected()
                        ? gateDecision()
                        : node.onPrepare(message);
                sendReply(DynamicSyncReply.ack(message.generation(), decision.accepted(), decision.reason()));
            }
            case STATE_SYNC -> {
                DynamicRegistryClientParticipant.PrepareDecision decision = gateRejected()
                        ? gateDecision()
                        : node.onStateSync(message);
                sendReply(DynamicSyncReply.ack(message.generation(), decision.accepted(), decision.reason()));
            }
            case COMMIT -> {
                DynamicRegistryClientParticipant.ActivationResult result =
                        node.onCommit(message);
                sendReply(DynamicSyncReply.activationReport(message.generation(), result.activated(),
                        result.detail() == null ? result.outcome() : result.detail()));
            }
            case ABORT -> node.onAbort(message);
            default -> NekoJS.LOGGER.warn("Unknown dynamic registry sync kind '{}' ignored", message.kind());
        }
    }

    private static DynamicRegistryClientParticipant.PrepareDecision gateDecision() {
        return new DynamicRegistryClientParticipant.PrepareDecision(false,
                "dynamic registry sync is disabled on this client: set [dynamicRegistry] enabled = true in"
                        + " nekojs/config/engine.toml and reconnect");
    }

    private static boolean gateRejected() {
        var config = ClassFilter.INSTANCE.config();
        return config == null || !config.dynamicRegistryEnabled();
    }

    private static DynamicRegistryClientParticipant participant() {
        DynamicRegistryClientParticipant current = participant;
        if (current == null) {
            synchronized (DynamicRegistryClientSync.class) {
                current = participant;
                if (current == null) {
                    current = new DynamicRegistryClientParticipant(new NeoForgeDynamicRegistryAdapter(
                            "client", () -> net.minecraft.core.RegistryAccess
                                    .fromRegistryOfRegistries(net.minecraft.core.registries.BuiltInRegistries.REGISTRY)));
                    participant = current;
                }
            }
        }
        return current;
    }

    private static void sendReply(DynamicSyncReply reply) {
        try {
            net.neoforged.neoforge.client.network.ClientPacketDistributor.sendToServer(
                    new DynamicRegistrySyncPacket(DynamicSyncWireCodec.encodeReply(reply)));
        } catch (Exception e) {
            NekoJS.LOGGER.warn("DynamicRegistry sync reply {} (generation {}) could not be sent: {}",
                    reply.kind(), reply.generation(),
                    e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
        }
    }

    private static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        DynamicRegistryClientParticipant node = participant;
        if (node != null) {
            node.onDisconnect();
        }
    }
}
//?}
//?}
