package com.tkisor.nekojs.network;

import com.tkisor.nekojs.NekoJS;
import com.tkisor.nekojs.client.gui.NekoErrorDashboardScreen;
import com.tkisor.nekojs.network.dto.ErrorSummaryDTO;
import net.minecraft.client.Minecraft;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

import java.util.List;

@EventBusSubscriber(modid = NekoJS.MODID)
public class NekoJSNetwork {

    @SubscribeEvent
    public static void register(final RegisterPayloadHandlersEvent event) {
        final PayloadRegistrar registrar = event.registrar("1");

        // 🌟 因为界面已经合并为 Dashboard，我们统一使用列表数据包
        registrar.playToClient(
                ShowErrorListPacket.TYPE,
                ShowErrorListPacket.STREAM_CODEC,
                NekoJSNetwork::handleShowErrorListOnClient
        );

        /* * 💡 建议：在你的服务端代码中，删掉 ShowErrorScreenPacket 的发送逻辑。
         * 如果脚本报了一个错，直接用 List.of(dto) 包装成 ShowErrorListPacket 发送过来。
         * 这样能让你的网络层更干净！
         */
    }

    private static void handleShowErrorListOnClient(ShowErrorListPacket data, IPayloadContext context) {
        // 🌟 1. 去掉多余的 mc.execute
        // 🌟 2. 必须调用内部静态隔离类，防止服务端崩溃
        context.enqueueWork(() -> ClientHandler.openDashboard(data.errors()));
    }

    /**
     * 🌟 核心：客户端安全隔离层 (Client Safe Isolation)
     * Java 虚拟机的类加载机制保证了：只要你不调用 ClientHandler 里的方法，
     * 服务端就不会去尝试加载 Minecraft.class 和 NekoErrorDashboardScreen.class。
     * 这样你的 Mod 就能完美兼容物理服务端！
     */
    private static class ClientHandler {
        private static void openDashboard(List<ErrorSummaryDTO> errors) {
            Minecraft.getInstance().setScreen(new NekoErrorDashboardScreen(errors));
        }
    }
}