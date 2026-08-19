package com.tkisor.nekojs.network;

import com.tkisor.nekojs.NekoJS;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import net.minecraftforge.fml.relauncher.Side;

/**
 * 1.12.2 NekoJS Network channel.
 *
 * <p>Provides the {@link SimpleNetworkWrapper} used for script-authored
 * bidirectional communication. {@link NekoScriptPayload} messages are
 * registered on both sides with a shared {@link NetworkMessageHandler}.
 */
@Mod.EventBusSubscriber(modid = NekoJS.MODID)
public class NekoJSNetwork {

    public static final SimpleNetworkWrapper CHANNEL = new SimpleNetworkWrapper(NekoJS.MODID);

    private static boolean registered = false;

    private NekoJSNetwork() {}

    /**
     * Initialize the network channel. Idempotent so it is safe to call from
     * multiple entry points. Registers the script payload handler on both the
     * server (discriminator 0) and client (discriminator 1) sides, and the
     * client data sync handler on the client side (discriminator 2, server to
     * client only).
     */
    public static void init() {
        if (registered) {
            return;
        }
        CHANNEL.registerMessage(NetworkMessageHandler.class, NekoScriptPayload.class, 0, Side.SERVER);
        CHANNEL.registerMessage(NetworkMessageHandler.class, NekoScriptPayload.class, 1, Side.CLIENT);
        CHANNEL.registerMessage(ClientDataMessageHandler.class, ClientDataSyncPacket.class, 2, Side.CLIENT);
        registered = true;
        NekoJS.LOGGER.debug("NekoJS Network channel initialized (1.12.2 script payload)");
    }
}
