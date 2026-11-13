package com.tkisor.nekojs.network;

import com.tkisor.nekojs.bindings.event.NetworkEvents;
import com.tkisor.nekojs.wrapper.network.NetworkDataEventJS;
import net.minecraft.server.level.ServerPlayer;
//? if neoforge {
import net.neoforged.neoforge.network.handling.IPayloadContext;
//?}
//? if >=26 {
import com.tkisor.nekojs.wrapper.event.server.ModificationSyncWire;
//?}

/**
 * Receives {@link NekoScriptPayload} and delivers it to the script listeners for its channel
 * ({@link NetworkEvents#SERVER} / {@link NetworkEvents#CLIENT}).
 *
 * <p>Switching to the main thread is up to each loader's receiver, because script callbacks often
 * touch Minecraft objects such as level/entity/player, which may only be read and written on the
 * main thread: NeoForge uses {@code IPayloadContext#enqueueWork} (see the wrapper methods inside
 * the loader guards below), fabric uses {@code server().execute()} / {@code client().execute()}
 * (see the receiver in FabricPlayNetwork).
 */
public final class NetworkMessageHandler {

    private NetworkMessageHandler() {}

    /**
     * Neutral delivery: a client to server packet goes to the SERVER listeners. The caller must already
     * be on the server main thread.
     */
    public static void postServerEvent(NekoScriptPayload payload, ServerPlayer sender) {
        NetworkDataEventJS event = new NetworkDataEventJS(payload.channel(), payload.data(), sender);
        NetworkEvents.SERVER.post(event, payload.channel());
    }

    /**
     * Neutral delivery: a server to client packet goes to the CLIENT listeners. The caller must already
     * be on the client main thread.
     */
    public static void postClientEvent(NekoScriptPayload payload) {
//? if >=26 {
        if (ModificationSyncWire.CHANNEL.equals(payload.channel())) {
            ModificationSyncWire.handleClient(payload.data());
            return;
        }
//?}
        NetworkDataEventJS event = new NetworkDataEventJS(payload.channel(), payload.data(), null);
        NetworkEvents.CLIENT.post(event, payload.channel());
    }

//? if neoforge {
// NeoForge receiver: the network thread receives the packet, enqueueWork switches to the main
// thread, then neutral delivery
    public static void handleScriptPayloadOnServer(NekoScriptPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            ServerPlayer sender = context.player() instanceof ServerPlayer sp ? sp : null;
            postServerEvent(payload, sender);
        });
    }

    // NeoForge receiver: the network thread receives the packet, enqueueWork switches to the main
    // thread, then neutral delivery
    public static void handleScriptPayloadOnClient(NekoScriptPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> postClientEvent(payload));
    }
//?}
}
