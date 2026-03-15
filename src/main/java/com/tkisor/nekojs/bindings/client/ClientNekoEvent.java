package com.tkisor.nekojs.bindings.client;

import com.tkisor.nekojs.bindings.event.NekoEvent;
import net.minecraft.client.Minecraft;

public interface ClientNekoEvent extends NekoEvent {
    default Minecraft getClient() {
        return Minecraft.getInstance();
    }

    ClientNekoEvent BASIC = new ClientNekoEvent() {};
}
