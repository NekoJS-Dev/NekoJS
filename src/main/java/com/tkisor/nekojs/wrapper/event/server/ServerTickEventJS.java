package com.tkisor.nekojs.wrapper.event.server;

import lombok.Getter;
import net.minecraft.server.MinecraftServer;

/**
 * Loader-neutral payload for server tick events (shared by tickPre / tickPost; the member is {@code event.server}).
 */
public class ServerTickEventJS {

    @Getter
    private final MinecraftServer server;

    public ServerTickEventJS(MinecraftServer server) {
        this.server = server;
    }
}
