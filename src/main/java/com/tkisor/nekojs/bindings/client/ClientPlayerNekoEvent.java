package com.tkisor.nekojs.bindings.client;

import com.tkisor.nekojs.bindings.player.NekoPlayerEvent;
import net.minecraft.client.player.LocalPlayer;

public class ClientPlayerNekoEvent implements NekoPlayerEvent, ClientNekoEvent {
    private final LocalPlayer player;

    public ClientPlayerNekoEvent(LocalPlayer player) {
        this.player = player;
    }

    @Override
    public LocalPlayer getEntity() {
        return player;
    }

    @Override
    public LocalPlayer getPlayer() {
        return player;
    }
}
