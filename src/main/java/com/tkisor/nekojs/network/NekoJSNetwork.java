//? if neoforge {
// All version differences go through the compat facade, so this file has no version guards: error
// panel screen access goes through McClientCompat, and the dist check, OP permission and script
// payload registration go through McPlatformCompat.
package com.tkisor.nekojs.network;

import com.tkisor.nekojs.NekoJS;
import com.tkisor.nekojs.client.gui.ErrorDashboardScreens;
import com.tkisor.nekojs.network.ErrorSummaryDTO;
import com.tkisor.nekojs.platform.compat.McPlatformCompat;
import com.tkisor.nekojs.wrapper.pdata.PDataSyncService;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import java.util.List;

@EventBusSubscriber(modid = NekoJS.MODID
//? if <26 {
        /*, bus = EventBusSubscriber.Bus.MOD
*///?}
)
public class NekoJSNetwork {
    @SubscribeEvent
    public static void register(final RegisterPayloadHandlersEvent event) {
        final PayloadRegistrar registrar = event.registrar("1");

        // Neutral send channel for the play phase: business code only depends on PlayPacketDispatcher.
        PlayPacketDispatchers.install(new NeoForgePlayPacketDispatcher());

        // Basic feature packets
        registrar.playToClient(ShowErrorListPacket.TYPE, ShowErrorListPacket.STREAM_CODEC, NekoJSNetwork::handleShowErrorListOnClient);

        // PData sync packet
        registrar.playToClient(PDataSyncPacket.TYPE, PDataSyncPacket.STREAM_CODEC, NekoJSNetwork::handlePDataSyncOnClient);

        // ClientData key/value sync packet (ClientData.sync / clientData.get, server to client only)
        registrar.playToClient(ClientDataSyncPacket.TYPE, ClientDataSyncPacket.STREAM_CODEC, ClientDataMessageHandler::handleOnClient);

        // Script-defined network channel packets (Network.sendToServer / sendToPlayer / sendToAllPlayers).
        // A payload type can only be registered once (NeoForge 26 deduplicates by type). The version
        // difference in registration shape (4 args on 26.x, 3 args plus a flow discriminator on 1.21.1)
        // lives in the McPlatformCompat implementations.
        McPlatformCompat.get().registerScriptPayload(registrar);

        // Dynamic Registry batch transaction sync packet: the 7th payload type (bidirectional) on the same
        // RegisterPayloadHandlersEvent and the same channel "1". Only nodes with dynamic registry support
        // register it in the compat implementations (Nf261/Nf262); the other nodes keep the explicit
        // payload subset, and the default implementation registers nothing.
        McPlatformCompat.get().registerDynamicSyncPayload(registrar);

        // Multiplayer script pack distribution: configuration-phase payloads (S2C). The server pushes the
        // hash list and the bundle from PackSyncConfigurationTask, which hooks the official
        // RegisterConfigurationTasksEvent entry point and needs no mixin.
        registrar.configurationToClient(PackHashListPayload.TYPE, PackHashListPayload.STREAM_CODEC, PackSyncMessageHandler::handleHashListOnClient);
        registrar.configurationToClient(PackBundlePayload.TYPE, PackBundlePayload.STREAM_CODEC, PackSyncMessageHandler::handleBundleOnClient);

        // Client-side wiring: the hook that makes pack distribution trigger a CLIENT script reload, plus
        // unloading remote packs on disconnect.
        if (McPlatformCompat.get().isClientDist()) {
            PackSyncClientConnections.install();
        }
    }

    /** Registers the configuration-phase task, once per player connection. When packSync is off, no task is registered. */
    @SubscribeEvent
    public static void registerConfigurationTasks(final net.neoforged.neoforge.network.event.RegisterConfigurationTasksEvent event) {
        PackSyncConfigurationTask.register(event);
    }

    /* ================= Client Handlers ================= */
    private static void handleShowErrorListOnClient(ShowErrorListPacket data, IPayloadContext context) {
        context.enqueueWork(() -> ClientHandler.showOrUpdateDashboard(data.errors(), data.openIfMissing()));
    }

    private static void handlePDataSyncOnClient(PDataSyncPacket data, IPayloadContext context) {
        context.enqueueWork(() -> PDataSyncService.acceptClientSync(data));
    }

    private static class ClientHandler {
        private static void showOrUpdateDashboard(List<ErrorSummaryDTO> errors, boolean openIfMissing) {
            ErrorDashboardScreens.showOrUpdate(errors, openIfMissing);
        }
    }

}
//?}
