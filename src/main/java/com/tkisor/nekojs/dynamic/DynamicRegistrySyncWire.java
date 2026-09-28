//? if neoforge {
//? if >=26 {
package com.tkisor.nekojs.dynamic;

import com.tkisor.nekojs.NekoJS;
import com.tkisor.nekojs.core.dynamic.facade.DynamicRegistryFacadeRuntime;
import com.tkisor.nekojs.core.dynamic.txn.DynamicSyncReply;
import com.tkisor.nekojs.core.dynamic.txn.DynamicSyncWireCodec;
import com.tkisor.nekojs.core.dynamic.txn.DynamicRegistryTransactionCoordinator;
import com.tkisor.nekojs.core.fs.ClassFilter;
import com.tkisor.nekojs.network.DynamicRegistrySyncPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerAboutToStartEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.function.Consumer;

/**
 * Ticket 21 platform wiring (server assembly + event driving): binds the ticket 21 batch
 * transaction engine to the existing {@link DynamicRegistryFacadeRuntime}
 * (bindActivationEngine seam) and provides the lifecycle/participant/reply event entries.
 * This class is the whole platform assembly of the dynamic registry face on >=26 NeoForge
 * — the shared listener files (ServerEventListener/PlayerEventListener) are not touched.
 *
 * <p><b>Bind timing is order-independent</b>: binding happens at
 * {@code ServerAboutToStart} (when the gate is on); there is no ordering guarantee
 * against {@code ServerEventListener#onServerAboutToStart}'s fireInitialCollection — the
 * facade's pendingCandidates is a pull queue, so an engine bound after the initial
 * collection picks up the same batch at the next {@link #onServerTickPost} pump
 * (the lastStagedPlanGeneration watermark prevents double staging).
 *
 * <p><b>Gate</b>: the existing {@code engine.toml} {@code [dynamicRegistry]} toggle (the
 * same switch as the legacy direct-injection path). Disabled means no engine is bound —
 * this face stays exactly the ticket 16 inert surface (declarations + ledger) and
 * activation stays gated (capability table keeps the not-verified wording until the
 * maintainer ruling plus real-machine verification).
 *
 * <p><b>Threading discipline</b> (ticket 17 receive side): network thread receipt →
 * {@code enqueueWork} onto the server main thread → only then into the coordinator (the
 * coordinator is owner-thread-only by contract, not thread-safe).
 */
@EventBusSubscriber(modid = NekoJS.MODID)
public final class DynamicRegistrySyncWire {

    /** Ack deadline: a conservative upper bound for one play-phase round trip plus client main-thread queueing (ms). */
    static final long ACK_TIMEOUT_MILLIS = 10_000L;

    private DynamicRegistrySyncWire() {}

    // ---- server lifecycle ----

    @SubscribeEvent
    public static void onServerAboutToStart(ServerAboutToStartEvent event) {
        if (!activationGateEnabled()) {
            NekoJS.LOGGER.info("DynamicRegistry activation engine stays unbound: [dynamicRegistry] disabled"
                    + " (ticket 16 inert declaration surface only)");
            return;
        }
        MinecraftServer server = event.getServer();
        DynamicRegistryFacade.runtime().bindActivationEngine(
                new NeoForgeDynamicRegistryAdapter("server", server::registryAccess),
                new NeoForgeDynamicSyncTransport(server),
                ACK_TIMEOUT_MILLIS,
                System::currentTimeMillis);
        NekoJS.LOGGER.info("DynamicRegistry activation engine bound at server start (ack timeout {} ms,"
                + " payload rides the existing register-once channel family)",
                ACK_TIMEOUT_MILLIS);
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        DynamicRegistryFacade.runtime().clearActivationEngine("server-stopped");
    }

    // ---- participant lifecycle (join = STATE_SYNC catch-up / mid-transaction prepare; leave = whole-batch abort decision) ----

    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            withEngine(engine -> engine.onParticipantJoined(player.getStringUUID()));
        }
    }

    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            withEngine(engine -> engine.onParticipantLeft(player.getStringUUID()));
        }
    }

    // ---- owner-thread pump and ack-timeout driving ----

    @SubscribeEvent
    public static void onServerTickPost(ServerTickEvent.Post event) {
        DynamicRegistryFacadeRuntime runtime = DynamicRegistryFacade.runtime();
        runtime.pumpActivation();
        DynamicRegistryTransactionCoordinator engine = runtime.activationEngine();
        if (engine != null) {
            engine.tick(System.currentTimeMillis());
        }
    }

    // ---- receive side (network thread → enqueueWork → server main thread → coordinator) ----

    /** Server handler for client ACK / ACTIVATION_REPORT replies; registered by Nf26xPlatformCompat. */
    public static void handleOnServer(DynamicRegistrySyncPacket payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer sender)) {
            return; // play-phase reply: only a logged-in player can be the participant
        }
        context.enqueueWork(() -> {
            DynamicSyncReply reply;
            try {
                reply = DynamicSyncWireCodec.decodeReply(payload.json());
            } catch (IllegalArgumentException e) {
                NekoJS.LOGGER.warn("Dropping malformed dynamic registry sync reply from {}: {}",
                        sender.getName().getString(), e.getMessage());
                return;
            }
            withEngine(engine -> {
                if (reply.kind() == DynamicSyncReply.Kind.ACK) {
                    engine.onAck(sender.getStringUUID(), reply.generation(),
                            Boolean.TRUE.equals(reply.accepted()), reply.reason());
                } else {
                    engine.onParticipantActivationReport(sender.getStringUUID(), reply.generation(),
                            Boolean.TRUE.equals(reply.activated()), reply.detail());
                }
            });
        });
    }

    // ---- helpers ----

    private static boolean activationGateEnabled() {
        var config = ClassFilter.INSTANCE.config();
        return config != null && config.dynamicRegistryEnabled();
    }

    private static void withEngine(Consumer<DynamicRegistryTransactionCoordinator> action) {
        DynamicRegistryTransactionCoordinator engine = DynamicRegistryFacade.runtime().activationEngine();
        if (engine != null) {
            action.accept(engine);
        }
    }
}
//?}
//?}
