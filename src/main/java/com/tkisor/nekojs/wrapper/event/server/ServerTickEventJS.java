package com.tkisor.nekojs.wrapper.event.server;

import com.tkisor.nekojs.bindings.event.NekoEvent;
import lombok.Getter;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.minecraft.server.MinecraftServer;

public class ServerTickEventJS implements NekoEvent {

    private final ServerTickEvent rawEvent;
    @Getter
    private final String phase;

    public ServerTickEventJS(ServerTickEvent rawEvent, String phase) {
        this.rawEvent = rawEvent;
        this.phase = phase;
    }

    public MinecraftServer getServer() {
        return rawEvent.getServer();
    }

}