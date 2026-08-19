package com.tkisor.nekojs.network;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.tkisor.nekojs.wrapper.clientdata.ClientDataStore;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * {@link ClientDataSyncPacket} 的客户端接收处理器：NeoForge 在网络线程收包，
 * 经 {@link IPayloadContext#enqueueWork} 切到主线程后解析 JSON 并写入
 * {@link ClientDataStore#SHARED}（{@code clientData} 绑定的底层存储）。
 *
 * <p>主线程切换与 {@link NetworkMessageHandler} 的脚本通道一致：后续消费方
 * （HUD/界面脚本）会在主线程读取，避免可见性问题。
 */
public final class ClientDataMessageHandler {

    private ClientDataMessageHandler() {}

    /** 服务端 → 客户端：解析 JSON 文本并覆盖写入对应 key。 */
    public static void handleOnClient(ClientDataSyncPacket payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            try {
                JsonElement json = JsonParser.parseString(payload.json());
                ClientDataStore.SHARED.accept(payload.key(), json);
            } catch (IllegalArgumentException ignored) {
                // key 非法（空/超长）——构造端已校验，坏包直接丢弃
            } catch (Exception e) {
                // 非法 JSON 等：丢弃并打日志，不让坏包炸掉客户端网络线程
                com.tkisor.nekojs.NekoJS.LOGGER.warn(
                        "Discarding malformed client data sync for key '{}': {}", payload.key(), e.getMessage());
            }
        });
    }
}
