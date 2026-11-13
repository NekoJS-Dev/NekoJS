//? if neoforge {
package com.tkisor.nekojs.network;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.tkisor.nekojs.wrapper.clientdata.ClientDataStore;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Client-side handler for {@link ClientDataSyncPacket}. NeoForge receives the packet on the network
 * thread; the handler switches to the main thread through {@link IPayloadContext#enqueueWork}, then
 * parses the JSON and writes it into {@link ClientDataStore#SHARED}, the backing store of the
 * {@code clientData} binding.
 *
 * <p>The main-thread switch matches the script channel of {@link NetworkMessageHandler}: the
 * consumers (HUD and screen scripts) read on the main thread, which avoids visibility problems.
 */
public final class ClientDataMessageHandler {

    private ClientDataMessageHandler() {}

    /** Server to client: parses the JSON text and overwrites the entry for that key. */
    public static void handleOnClient(ClientDataSyncPacket payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            try {
                JsonElement json = JsonParser.parseString(payload.json());
                ClientDataStore.SHARED.accept(payload.key(), json);
            } catch (IllegalArgumentException ignored) {
                // Invalid key (blank or too long). The builder already validates it, so a bad packet is dropped here.
            } catch (Exception e) {
                // Invalid JSON and the like: drop the packet and log it, so a bad packet cannot take down the
                // client network thread.
                com.tkisor.nekojs.NekoJS.LOGGER.warn(
                        "Discarding malformed client data sync for key '{}': {}", payload.key(), e.getMessage());
            }
        });
    }
}
//?}
