package com.tkisor.nekojs.wrapper.event.server;

import lombok.Getter;
import net.minecraft.server.MinecraftServer;

/**
 * 统一的服务器生命周期事件 wrapper（跨平台字段名一致）。
 *
 * <p>用于 {@code ServerEvents.aboutToStart}/{@code starting}/{@code started}/
 * {@code stopping}/{@code stopped}。脚本侧 {@code event.server} 在 NeoForge 与
 * Cleanroom 上一致可用（均为 {@code MinecraftServer}）。
 *
 * <p>这些是非分发事件（Void key），也是脚本最常监听的服务器事件，统一类型价值最高。
 *
 * <p>Cleanroom 注意：{@code FMLServer*Event} 不走 {@code MinecraftForge.EVENT_BUS}，
 * 由 {@code NekoJSMod} 的 {@code @Mod.EventHandler} 手动 post（构造 wrapper 后投递）。
 */
@Getter
public class ServerLifecycleEventJS {
    private final MinecraftServer server;

    public ServerLifecycleEventJS(MinecraftServer server) {
        this.server = server;
    }
}
