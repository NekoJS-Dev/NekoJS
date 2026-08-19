package com.tkisor.nekojs.network;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.tkisor.nekojs.NekoJS;
import com.tkisor.nekojs.wrapper.clientdata.ClientDataStore;
import net.minecraft.client.Minecraft;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

/**
 * Handles {@link ClientDataSyncPacket} on the client. Incoming messages run on
 * a network thread, so the store update is scheduled on the main client thread
 * via {@code IThreadListener.addScheduledTask} (same pattern as
 * {@link NetworkMessageHandler}) before writing into the shared
 * {@link ClientDataStore} that backs the {@code clientData} binding.
 */
public class ClientDataMessageHandler implements IMessageHandler<ClientDataSyncPacket, IMessage> {

    @Override
    public IMessage onMessage(ClientDataSyncPacket message, MessageContext ctx) {
        final String key = message.key();
        final String json = message.json();
        Minecraft.getMinecraft().addScheduledTask(() -> {
            try {
                JsonElement element = JsonParser.parseString(json);
                ClientDataStore.SHARED.accept(key, element);
            } catch (IllegalArgumentException ignored) {
                // invalid key (blank/oversized) — the sending side already validates; drop bad packets
            } catch (Exception e) {
                NekoJS.LOGGER.warn("Discarding malformed client data sync for key '{}': {}", key, e.getMessage());
            }
        });
        return null;
    }
}
