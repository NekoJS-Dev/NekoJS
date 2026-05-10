package com.tkisor.nekojs.core;

import com.tkisor.nekojs.api.NeoForgeCatalogPlatformProvider;
import com.tkisor.nekojs.api.catalog.NekoScriptCatalog;
import com.tkisor.nekojs.api.event.EventBusJS;
import com.tkisor.nekojs.platform.NekoIdCompat;
import com.tkisor.nekojs.platform.NeoForgeIdCompat;
import com.tkisor.nekojs.platform.NeoForgePlatform;
import com.tkisor.nekojs.platform.Platform;
import graal.mod.api.MemberRemapper;
import net.neoforged.bus.api.ICancellableEvent;

public final class NeoForgeRuntimeBootstrap {
    private NeoForgeRuntimeBootstrap() {}

    public static void setup() {
        MemberRemapper.GLOBAL.set(new NekoJSMemberRemapper());
        Platform.init(new NeoForgePlatform());
        NekoIdCompat.init(new NeoForgeIdCompat());
        NekoScriptCatalog.setPlatformProvider(new NeoForgeCatalogPlatformProvider());
        EventBusJS.setExternalCancellabilityPredicate(ICancellableEvent.class::isAssignableFrom);
        NekoJSScriptManager.setEventBridge(new NeoForgeScriptEventBridge());
    }
}
