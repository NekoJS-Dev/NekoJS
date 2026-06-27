package com.tkisor.nekojs.network;

import com.tkisor.nekojs.NekoJS;
import com.tkisor.nekojs.client.gui.NekoErrorDashboardScreen;
import com.tkisor.nekojs.client.gui.NekoWorkspaceScreen;
import com.tkisor.nekojs.network.dto.ErrorSummaryDTO;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import com.tkisor.nekojs.wrapper.pdata.PDataSyncService;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

import java.util.List;
import java.util.Map;

@EventBusSubscriber(modid = NekoJS.MODID)
public class NekoJSNetwork {
    @SubscribeEvent
    public static void register(final RegisterPayloadHandlersEvent event) {
        final PayloadRegistrar registrar = event.registrar("1");

        // 基础功能包
        registrar.playToClient(ShowErrorListPacket.TYPE, ShowErrorListPacket.STREAM_CODEC, NekoJSNetwork::handleShowErrorListOnClient);
        registrar.playToServer(FetchScriptRequestPacket.TYPE, FetchScriptRequestPacket.STREAM_CODEC, NekoJSNetwork::handleFetchRequestOnServer);
        registrar.playToServer(SaveScriptPacket.TYPE, SaveScriptPacket.STREAM_CODEC, NekoJSNetwork::handleSaveScriptOnServer);
        registrar.playToClient(FetchScriptResponsePacket.TYPE, FetchScriptResponsePacket.STREAM_CODEC, NekoJSNetwork::handleFetchResponseOnClient);

        // 通用回执包
        registrar.playToClient(SyncFeedbackPacket.TYPE, SyncFeedbackPacket.STREAM_CODEC, NekoJSNetwork::handleSyncFeedbackOnClient);

        // 批量同步包
        registrar.playToServer(FetchAllScriptsRequestPacket.TYPE, FetchAllScriptsRequestPacket.STREAM_CODEC, NekoJSNetwork::handleFetchAllRequestOnServer);
        registrar.playToServer(UploadAllScriptsPacket.TYPE, UploadAllScriptsPacket.STREAM_CODEC, NekoJSNetwork::handleUploadAllOnServer);
        registrar.playToClient(DownloadAllScriptsPacket.TYPE, DownloadAllScriptsPacket.STREAM_CODEC, NekoJSNetwork::handleDownloadAllOnClient);

        // 打开工作区包
        registrar.playToClient(OpenWorkspacePacket.TYPE, OpenWorkspacePacket.STREAM_CODEC, NekoJSNetwork::handleOpenWorkspaceOnClient);

        // PData 同步包
        registrar.playToClient(PDataSyncPacket.TYPE, PDataSyncPacket.STREAM_CODEC, NekoJSNetwork::handlePDataSyncOnClient);
    }

    /* ================= Client Handlers ================= */
    private static void handleShowErrorListOnClient(ShowErrorListPacket data, IPayloadContext context) {
        context.enqueueWork(() -> ClientHandler.showOrUpdateDashboard(data.errors(), data.openIfMissing()));
    }

    private static void handleFetchResponseOnClient(FetchScriptResponsePacket data, IPayloadContext context) {
        context.enqueueWork(() -> ClientHandler.receiveServerScript(data.content()));
    }

    private static void handleSyncFeedbackOnClient(SyncFeedbackPacket data, IPayloadContext context) {
        context.enqueueWork(() -> ClientHandler.processFeedback(data.success(), data.message()));
    }

    private static void handleDownloadAllOnClient(DownloadAllScriptsPacket data, IPayloadContext context) {
        context.enqueueWork(() -> ClientHandler.receiveAllScripts(data.files()));
    }

    private static void handleOpenWorkspaceOnClient(OpenWorkspacePacket data, IPayloadContext context) {
        context.enqueueWork(ClientHandler::openWorkspace);
    }

    private static void handlePDataSyncOnClient(PDataSyncPacket data, IPayloadContext context) {
        context.enqueueWork(() -> PDataSyncService.acceptClientSync(data));
    }

    private static class ClientHandler {
        private static void showOrUpdateDashboard(List<ErrorSummaryDTO> errors, boolean openIfMissing) {
            if (Minecraft.getInstance().screen instanceof NekoErrorDashboardScreen screen) {
                screen.updateErrors(errors);
            } else if (openIfMissing) {
                Minecraft.getInstance().setScreen(new NekoErrorDashboardScreen(errors));
            }
        }

        private static void receiveServerScript(String content) {
            if (Minecraft.getInstance().screen instanceof NekoErrorDashboardScreen screen) {
                screen.loadServerScript(content);
            }
        }

        private static void processFeedback(boolean success, String message) {
            if (Minecraft.getInstance().screen instanceof NekoErrorDashboardScreen screen) {
                screen.onSyncFeedback(success, message);
            } else if (Minecraft.getInstance().screen instanceof NekoWorkspaceScreen wsScreen) {
                wsScreen.onSyncFeedback(success, message);
            } else if (Minecraft.getInstance().player != null) {
                String prefix = success ? "§a✔ " : "§c✖ ";
                Minecraft.getInstance().player.sendSystemMessage(Component.literal(prefix + message));
            }
        }

        private static void receiveAllScripts(Map<String, String> files) {
            try {
                int count = ScriptSyncService.writeBatch(files);
                processFeedback(true, "成功从服务端强制拉取了 " + count + " 个文件！");
            } catch (Exception e) {
                processFeedback(false, "批量写入本地失败，请检查日志");
            }
        }

        private static void openWorkspace() {
            Minecraft.getInstance().setScreen(new NekoWorkspaceScreen());
        }
    }

    /* ================= Server Handlers ================= */
    private static void handleFetchRequestOnServer(FetchScriptRequestPacket data, IPayloadContext context) {
        context.enqueueWork(() -> {
            Player player = context.player();
            if (!Commands.LEVEL_GAMEMASTERS.check(player.permissions())) {
                PacketDistributor.sendToPlayer((ServerPlayer) player, new SyncFeedbackPacket(false, "权限不足，无法拉取服务端代码！"));
                return;
            }
            try {
                String content = ScriptSyncService.readScript(data.path());
                if (content != null) {
                    PacketDistributor.sendToPlayer((ServerPlayer) player, new FetchScriptResponsePacket(data.path(), content));
                } else {
                    PacketDistributor.sendToPlayer((ServerPlayer) player, new SyncFeedbackPacket(false, "服务端找不到该文件: " + data.path()));
                }
            } catch (Exception e) {
                NekoJS.LOGGER.error("Failed to read script for client", e);
                PacketDistributor.sendToPlayer((ServerPlayer) player, new SyncFeedbackPacket(false, "读取文件失败，请检查服务端日志"));
            }
        });
    }

    private static void handleSaveScriptOnServer(SaveScriptPacket data, IPayloadContext context) {
        context.enqueueWork(() -> {
            Player player = context.player();
            if (!Commands.LEVEL_GAMEMASTERS.check(player.permissions())) {
                PacketDistributor.sendToPlayer((ServerPlayer) player, new SyncFeedbackPacket(false, "权限不足，无法修改服务端代码！"));
                return;
            }
            try {
                ScriptSyncService.saveScript(data.path(), data.content());
                PacketDistributor.sendToPlayer((ServerPlayer) player, new SyncFeedbackPacket(true, "保存成功！"));
            } catch (Exception e) {
                NekoJS.LOGGER.error("Failed to save script from client", e);
                PacketDistributor.sendToPlayer((ServerPlayer) player, new SyncFeedbackPacket(false, "保存文件失败，请检查服务端日志"));
            }
        });
    }

    private static void handleFetchAllRequestOnServer(FetchAllScriptsRequestPacket data, IPayloadContext context) {
        context.enqueueWork(() -> {
            Player player = context.player();
            if (!Commands.LEVEL_GAMEMASTERS.check(player.permissions())) {
                PacketDistributor.sendToPlayer((ServerPlayer) player, new SyncFeedbackPacket(false, "权限不足！"));
                return;
            }
            Map<String, String> files = ScriptSyncService.collectAllScripts();
            PacketDistributor.sendToPlayer((ServerPlayer) player, new DownloadAllScriptsPacket(files));
        });
    }

    private static void handleUploadAllOnServer(UploadAllScriptsPacket data, IPayloadContext context) {
        context.enqueueWork(() -> {
            Player player = context.player();
            if (!Commands.LEVEL_GAMEMASTERS.check(player.permissions())) {
                PacketDistributor.sendToPlayer((ServerPlayer) player, new SyncFeedbackPacket(false, "权限不足！"));
                return;
            }
            try {
                int count = ScriptSyncService.writeBatch(data.files());
                PacketDistributor.sendToPlayer((ServerPlayer) player, new SyncFeedbackPacket(true, "成功接收并覆盖了 " + count + " 个脚本文件！(请使用 /reload 生效)"));
            } catch (Exception e) {
                NekoJS.LOGGER.debug("Failed to sync scripts from client", e);
                PacketDistributor.sendToPlayer((ServerPlayer) player, new SyncFeedbackPacket(false, "批量同步失败，请检查服务端日志"));
            }
        });
    }

}
