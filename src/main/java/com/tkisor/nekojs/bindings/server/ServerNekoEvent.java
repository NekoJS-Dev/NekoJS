package com.tkisor.nekojs.bindings.server;

import com.tkisor.nekojs.bindings.event.NekoEvent;
import lombok.Getter;
import net.minecraft.server.MinecraftServer;

public class ServerNekoEvent implements NekoEvent {
    @Getter
    public final MinecraftServer server;

    public ServerNekoEvent(MinecraftServer server) {
        this.server = server;
    }
}
