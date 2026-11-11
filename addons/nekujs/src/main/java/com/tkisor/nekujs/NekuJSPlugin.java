package com.tkisor.nekujs;

import com.tkisor.nekojs.api.annotation.RegisterNekoJSPlugin;
import com.tkisor.nekojs.core.plugin.RecipeSchemaRegister;


/** Publishes legacy plugin contributions through the existing NekoJS collection and lifecycle hooks. */
@RegisterNekoJSPlugin
public final class NekuJSPlugin implements com.tkisor.nekojs.api.NekoJSPlugin {
    private com.tkisor.nekujs.bridge.LegacyRuntimeBridge legacy;

    private com.tkisor.nekujs.bridge.LegacyRuntimeBridge legacy() {
        if (legacy == null) legacy = new com.tkisor.nekujs.bridge.LegacyRuntimeBridge();
        return legacy;
    }

    @Override
    public void registerEvents(com.tkisor.nekojs.api.event.EventGroupRegistry registry) {
        legacy().registerEvents(registry, false);
    }

    @Override
    public void registerClientEvents(com.tkisor.nekojs.api.event.EventGroupRegistry registry) {
        legacy().registerEvents(registry, true);
    }

    @Override
    public void registerBinding(com.tkisor.nekojs.api.data.BindingRegistry registry) {
        if (registry.scriptType() != com.tkisor.nekojs.api.ScriptType.TEST) legacy().registerBindings(registry);
    }

    @Override
    public void registerAdapters(com.tkisor.nekojs.api.data.JSTypeAdapterRegistry registry) {
        registry.register(new com.tkisor.nekujs.bridge.LegacyItemPredicateAdapter());
        registry.register(new com.tkisor.nekujs.bridge.LegacyTickDurationAdapter());
        legacy().registerAdapters(registry);
    }

    @Override public void init() { legacy().init(); }
    @Override public void initStartup() { legacy().initStartup(); }
    @Override public void afterInit() { legacy().afterInit(); }

    @Override
    public void registerRecipeSchemas(RecipeSchemaRegister registry) {
        legacy().registerRecipeSchemas(registry);
    }
}
