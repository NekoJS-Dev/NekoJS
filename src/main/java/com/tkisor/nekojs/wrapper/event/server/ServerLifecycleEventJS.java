package com.tkisor.nekojs.wrapper.event.server;

import lombok.Getter;
import net.minecraft.server.MinecraftServer;

/**
 * Loader-neutral payload for the server lifecycle events (first used by the fabric bridge; member names match the
 * {@code event.server} getter the contract fixes, and the native NeoForge event has the same shape, so scripts look
 * the same on either loader).
 * Covers five moments: aboutToStart / starting / started / stopping / stopped.
 */
public class ServerLifecycleEventJS {

    @Getter
    private final MinecraftServer server;

    public ServerLifecycleEventJS(MinecraftServer server) {
        this.server = server;
    }
}
