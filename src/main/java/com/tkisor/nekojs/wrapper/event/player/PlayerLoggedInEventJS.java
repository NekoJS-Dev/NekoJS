package com.tkisor.nekojs.wrapper.event.player;

import com.tkisor.nekojs.bindings.event.NekoEvent;
import com.tkisor.nekojs.wrapper.entity.PlayerWrapper;
import lombok.Getter;
import net.minecraft.server.MinecraftServer;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

public class PlayerLoggedInEventJS implements NekoEvent {

    private final PlayerEvent.PlayerLoggedInEvent rawEvent;

    @Getter
    private final PlayerWrapper player;

    public PlayerLoggedInEventJS(PlayerEvent.PlayerLoggedInEvent rawEvent) {
        this.rawEvent = rawEvent;
        this.player = new PlayerWrapper(rawEvent.getEntity());
    }

    public MinecraftServer getServer() {
        return rawEvent.getEntity().level().getServer();
    }
}