//? if neoforge {
// 版本差异全部走 compat 门面，本文件零版本守卫：错误面板屏幕访问走 McClientCompat，
// dist 判定、OP 权限、脚本 payload 注册走 McPlatformCompat。
package com.tkisor.nekojs.network;

import com.tkisor.nekojs.NekoJS;
import com.tkisor.nekojs.client.gui.NekoErrorDashboardScreen;
import com.tkisor.nekojs.network.ErrorSummaryDTO;
import com.tkisor.nekojs.platform.compat.McClientCompat;
import com.tkisor.nekojs.platform.compat.McPlatformCompat;
import com.tkisor.nekojs.wrapper.pdata.PDataSyncService;
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

        // play 阶段发送面的中立通道：业务代码只依赖 PlayPacketDispatcher
        PlayPacketDispatchers.install(new NeoForgePlayPacketDispatcher());

        // 基础功能包
        registrar.playToClient(ShowErrorListPacket.TYPE, ShowErrorListPacket.STREAM_CODEC, NekoJSNetwork::handleShowErrorListOnClient);

        // PData 同步包
        registrar.playToClient(PDataSyncPacket.TYPE, PDataSyncPacket.STREAM_CODEC, NekoJSNetwork::handlePDataSyncOnClient);

        // ClientData 键值同步包（ClientData.sync / clientData.get，服务端→客户端单向）
        registrar.playToClient(ClientDataSyncPacket.TYPE, ClientDataSyncPacket.STREAM_CODEC, ClientDataMessageHandler::handleOnClient);

        // 脚本自定义网络通道包（Network.sendToServer / sendToPlayer / sendToAllPlayers）
        // 同一个 payload type 只能注册一次（NeoForge 26 按 type 去重）；注册形状的版本差异
        //（26.x 4 参 / 1.21.1 3 参 + flow 判别）下沉进 McPlatformCompat 实现。
        McPlatformCompat.get().registerScriptPayload(registrar);

        // 多人脚本包分发：配置阶段 payload（S2C）——服务器在 PackSyncConfigurationTask
        // （RegisterConfigurationTasksEvent 官方入口，免 mixin）中推送哈希清单 + bundle。
        registrar.configurationToClient(PackHashListPayload.TYPE, PackHashListPayload.STREAM_CODEC, PackSyncMessageHandler::handleHashListOnClient);
        registrar.configurationToClient(PackBundlePayload.TYPE, PackBundlePayload.STREAM_CODEC, PackSyncMessageHandler::handleBundleOnClient);

        // 客户端接线：包分发触发 CLIENT 脚本重载的钩子 + 断线卸载远端包
        if (McPlatformCompat.get().isClientDist()) {
            PackSyncClientConnections.install();
        }
    }

    /** 配置阶段任务注册（每个玩家连接一次；packSync 关闭时零任务注册）。 */
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
            if (McClientCompat.get().currentScreen() instanceof NekoErrorDashboardScreen screen) {
                screen.updateErrors(errors);
            } else if (openIfMissing) {
                McClientCompat.get().showScreen(NekoErrorDashboardScreen.create(errors));
            }
        }
    }

}
//?}
