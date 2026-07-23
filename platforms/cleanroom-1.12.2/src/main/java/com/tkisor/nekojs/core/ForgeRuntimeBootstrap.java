package com.tkisor.nekojs.core;

import com.tkisor.nekojs.api.ForgeCatalogPlatformProvider;
import com.tkisor.nekojs.api.catalog.NekoScriptCatalog;
import com.tkisor.nekojs.api.event.EventBusJS;
import net.minecraftforge.fml.common.eventhandler.Event;

public final class ForgeRuntimeBootstrap {
    private ForgeRuntimeBootstrap() {}

    @SuppressWarnings("unchecked")
    public static void setup() {
        NekoScriptCatalog.setPlatformProvider(new ForgeCatalogPlatformProvider());
        EventBusJS.setExternalCancellabilityPredicate(
            Event.class::isAssignableFrom
        );
    }
}
