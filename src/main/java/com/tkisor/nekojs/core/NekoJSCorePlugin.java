package com.tkisor.nekojs.core;

import com.tkisor.nekojs.api.AdapterRegister;
import com.tkisor.nekojs.api.BindingsRegister;
import com.tkisor.nekojs.api.EventGroupRegistry;
import com.tkisor.nekojs.api.NekoJSPlugin;
import com.tkisor.nekojs.api.annotation.RegisterNekoJSPlugin;
import com.tkisor.nekojs.bindings.event.*;
import com.tkisor.nekojs.bindings.static_access.ItemStatic;
import com.tkisor.nekojs.js.type_adapter.ItemStackAdapter;

@RegisterNekoJSPlugin
public class NekoJSCorePlugin implements NekoJSPlugin {
    @Override
    public void registerEvents(EventGroupRegistry registry) {
        registry.register(PlayerEvents.GROUP);
        registry.register(ServerEvents.GROUP);
        registry.register(BlockEvents.GROUP);
        registry.register(ItemEvents.GROUP);
        registry.register(EntityEvents.GROUP);
        registry.register(CommandEvents.GROUP);
        registry.register(RegistryEvents.GROUP);
    }

    @Override
    public void registerBindings(BindingsRegister registry) {
        registry.register("Item", new ItemStatic());
    }

    @Override
    public void registerAdapters(AdapterRegister registry) {
        registry.register(new ItemStackAdapter());
    }
}