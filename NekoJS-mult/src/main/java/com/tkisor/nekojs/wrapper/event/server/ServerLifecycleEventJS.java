package com.tkisor.nekojs.wrapper.event.server;

import lombok.Getter;
import net.minecraft.server.MinecraftServer;

/**
 * 服务端生命周期事件的中立 payload（fabric 桥首用；成员名对齐契约约定的
 * {@code event.server} getter——NeoForge 侧原生事件同形，脚本写法跨加载器一致）。
 * 覆盖 aboutToStart / starting / started / stopping / stopped 五个时机。
 */
public class ServerLifecycleEventJS {

    @Getter
    private final MinecraftServer server;

    public ServerLifecycleEventJS(MinecraftServer server) {
        this.server = server;
    }
}
