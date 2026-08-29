package com.tkisor.nekojs.wrapper.event.server;

import lombok.Getter;
import net.minecraft.server.MinecraftServer;

/**
 * 服务端 tick 事件的中立 payload（tickPre / tickPost 共用；成员 {@code event.server}）。
 */
public class ServerTickEventJS {

    @Getter
    private final MinecraftServer server;

    public ServerTickEventJS(MinecraftServer server) {
        this.server = server;
    }
}
