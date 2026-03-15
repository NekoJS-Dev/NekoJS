package com.tkisor.nekojs.bindings.event;

import com.tkisor.nekojs.api.NekoJSPlugin;
import com.tkisor.nekojs.core.NekoJSPluginManager;
import net.neoforged.bus.api.Event;
import net.neoforged.fml.event.IModBusEvent;

public class RegisterNekoJSPluginEvent extends Event implements IModBusEvent {
    public void register(NekoJSPlugin plugin) {
        NekoJSPluginManager.register(plugin);
    }
}
