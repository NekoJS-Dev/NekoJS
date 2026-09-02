package com.tkisor.nekojs.network;

import com.tkisor.nekojs.bindings.event.NetworkEvents;
import com.tkisor.nekojs.wrapper.network.NetworkDataEventJS;
import net.minecraft.server.level.ServerPlayer;
//? if neoforge {
import net.neoforged.neoforge.network.handling.IPayloadContext;
//?}

/**
 * {@link NekoScriptPayload} 的接收投递：按 channel 投给脚本监听器
 * （{@link NetworkEvents#SERVER} / {@link NetworkEvents#CLIENT}）。
 *
 * <p>主线程切换由各加载器的接收端负责（脚本回调里常访问 level/entity/player 等
 * Minecraft 对象，这些对象只能在主线程读写）：NeoForge 走
 * {@code IPayloadContext#enqueueWork}（见下方 loader 守卫内的包装方法），
 * fabric 走 {@code server().execute()} / {@code client().execute()}（见
 * FabricPlayNetwork 的 receiver）。
 */
public final class NetworkMessageHandler {

    private NetworkMessageHandler() {}

    /** 中立投递核心：客户端 → 服务端的包投给 SERVER 监听器（调用方须已在服务端主线程）。 */
    public static void postServerEvent(NekoScriptPayload payload, ServerPlayer sender) {
        NetworkDataEventJS event = new NetworkDataEventJS(payload.channel(), payload.data(), sender);
        NetworkEvents.SERVER.post(event, payload.channel());
    }

    /** 中立投递核心：服务端 → 客户端的包投给 CLIENT 监听器（调用方须已在客户端主线程）。 */
    public static void postClientEvent(NekoScriptPayload payload) {
        NetworkDataEventJS event = new NetworkDataEventJS(payload.channel(), payload.data(), null);
        NetworkEvents.CLIENT.post(event, payload.channel());
    }

//? if neoforge {
    // NeoForge 接收端：网络线程收包 → enqueueWork 切主线程 → 中立投递
    public static void handleScriptPayloadOnServer(NekoScriptPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            ServerPlayer sender = context.player() instanceof ServerPlayer sp ? sp : null;
            postServerEvent(payload, sender);
        });
    }

    // NeoForge 接收端：网络线程收包 → enqueueWork 切主线程 → 中立投递
    public static void handleScriptPayloadOnClient(NekoScriptPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> postClientEvent(payload));
    }
//?}
}
