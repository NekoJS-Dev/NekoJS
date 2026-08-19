package com.tkisor.nekojs.network;

import com.tkisor.nekojs.NekoJS;
import net.minecraftforge.common.MinecraftForge;
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
     * server (discriminator 0) and client (discriminator 1) sides, the client
     * data sync handler on the client side (discriminator 2, server to client
     * only), and the pack sync handlers on the client side (discriminators 3/4;
     * the server only sends them, see {@link PackSyncServerPusher} — 1.12.2
     * degraded mode pushes on the play channel after login).
     */
    public static void init() {
        if (registered) {
            return;
        }
        CHANNEL.registerMessage(NetworkMessageHandler.class, NekoScriptPayload.class, 0, Side.SERVER);
        CHANNEL.registerMessage(NetworkMessageHandler.class, NekoScriptPayload.class, 1, Side.CLIENT);
        CHANNEL.registerMessage(ClientDataMessageHandler.class, ClientDataSyncPacket.class, 2, Side.CLIENT);
        CHANNEL.registerMessage(PackSyncMessageHandler.HashList.class, PackHashListPacket.class, 3, Side.CLIENT);
        CHANNEL.registerMessage(PackSyncMessageHandler.Bundle.class, PackBundlePacket.class, 4, Side.CLIENT);
        // 服务器侧：登录后推哈希 + bundle（packSync 关闭时零动作）
        MinecraftForge.EVENT_BUS.register(PackSyncServerPusher.class);
        // 客户端侧：断线卸载远端包 + 安装 CLIENT 重载钩子
        if (net.minecraftforge.fml.common.FMLCommonHandler.instance().getSide() == Side.CLIENT) {
            PackSyncClientConnections.install();
            MinecraftForge.EVENT_BUS.register(PackSyncClientConnections.class);
        }
        registered = true;
        NekoJS.LOGGER.debug("NekoJS Network channel initialized (1.12.2 script payload)");
    }
}
