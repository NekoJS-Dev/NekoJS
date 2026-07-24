package com.tkisor.nekojs.network;

import com.tkisor.nekojs.bindings.event.NetworkEvents;
import com.tkisor.nekojs.wrapper.network.NetworkDataEventJS;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * {@link NekoScriptPayload} 的接收处理器：NeoForge 在网络线程收包，这里通过
 * {@link IPayloadContext#enqueueWork} 切到主线程后，按 channel 投递给脚本监听器
 * （{@link NetworkEvents#SERVER} / {@link NetworkEvents#CLIENT}）。
 *
 * <p>主线程切换是必须的：脚本回调里常访问 level/entity/player 等 Minecraft 对象，
 * 这些对象只能在主线程读写。
 */
public final class NetworkMessageHandler {

    private NetworkMessageHandler() {}

    /** 客户端 → 服务端：在服务端主线程投递到 {@link NetworkEvents#SERVER}，附带发送玩家。 */
    public static void handleScriptPayloadOnServer(NekoScriptPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            ServerPlayer sender = context.player() instanceof ServerPlayer sp ? sp : null;
            NetworkDataEventJS event = new NetworkDataEventJS(payload.channel(), payload.data(), sender);
            NetworkEvents.SERVER.post(event, payload.channel());
        });
    }

    /** 服务端 → 客户端：在客户端主线程投递到 {@link NetworkEvents#CLIENT}（无发送玩家）。 */
    public static void handleScriptPayloadOnClient(NekoScriptPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            NetworkDataEventJS event = new NetworkDataEventJS(payload.channel(), payload.data(), null);
            NetworkEvents.CLIENT.post(event, payload.channel());
        });
    }
}
