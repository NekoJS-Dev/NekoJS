package com.tkisor.nekojs.network;

import com.tkisor.nekojs.NekoJS;
import com.tkisor.nekojs.client.gui.NekoErrorScreen;
import net.minecraft.client.Minecraft;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;

@EventBusSubscriber(modid = NekoJS.MODID)
public class NekoJSNetwork {

    @SubscribeEvent
    public static void register(final RegisterPayloadHandlersEvent event) {
        event.registrar("1")
                .playToClient(
                        ShowErrorScreenPacket.TYPE,
                        ShowErrorScreenPacket.STREAM_CODEC,
                        NekoJSNetwork::handleShowErrorOnClient
                );
    }

    private static void handleShowErrorOnClient(ShowErrorScreenPacket data, IPayloadContext context) {
        context.enqueueWork(() -> {
            Minecraft mc = Minecraft.getInstance();
            mc.execute(() -> {
                mc.setScreen(new NekoErrorScreen(data.scriptId(), data.errorDetails()));
            });
        });
    }
}