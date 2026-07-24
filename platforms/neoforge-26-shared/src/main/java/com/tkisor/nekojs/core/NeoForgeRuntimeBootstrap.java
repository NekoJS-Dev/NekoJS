package com.tkisor.nekojs.core;

import com.tkisor.nekojs.api.NeoForgeCatalogPlatformProvider;
import com.tkisor.nekojs.api.catalog.NekoScriptCatalog;
import com.tkisor.nekojs.api.event.EventBusJS;
import net.neoforged.bus.api.ICancellableEvent;

public final class NeoForgeRuntimeBootstrap {
    private NeoForgeRuntimeBootstrap() {}

    public static void setup() {
        NekoScriptCatalog.setPlatformProvider(new NeoForgeCatalogPlatformProvider());
        EventBusJS.setExternalCancellabilityPredicate(ICancellableEvent.class::isAssignableFrom);
    }
}
