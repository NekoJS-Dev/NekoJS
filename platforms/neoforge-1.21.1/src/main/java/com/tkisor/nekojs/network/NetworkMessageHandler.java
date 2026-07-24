package com.tkisor.nekojs.network;

import com.tkisor.nekojs.bindings.event.NetworkEvents;
import com.tkisor.nekojs.wrapper.network.NetworkDataEventJS;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public final class NetworkMessageHandler {

    private NetworkMessageHandler() {}

    public static void handleScriptPayloadOnServer(NekoScriptPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            ServerPlayer sender = context.player() instanceof ServerPlayer sp ? sp : null;
            NetworkDataEventJS event = new NetworkDataEventJS(payload.channel(), payload.data(), sender);
            NetworkEvents.SERVER.post(event, payload.channel());
        });
    }

    public static void handleScriptPayloadOnClient(NekoScriptPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            NetworkDataEventJS event = new NetworkDataEventJS(payload.channel(), payload.data(), null);
            NetworkEvents.CLIENT.post(event, payload.channel());
        });
    }
}
