package com.tkisor.nekojs.mixinjs;

import com.tkisor.nekojs.core.lifecycle.NekoRuntimeRoot;
import com.tkisor.nekojs.core.mixin.MixinCarriers;
import com.tkisor.nekojs.fabric.event.FabricServerEventBindings;
import com.tkisor.nekojs.platform.Platform;

/** Fabric distribution and server identity are independent of the logical script type. */
public final class MixinPlatform {
    private MixinPlatform() {}
    public static void install(NekoRuntimeRoot root) {
        if (!Platform.instance().isMixinJsEnabled()) return;
        root.mixins().install(MixinCarriers.discover(), MixinCarriers.currentProfile(), thread -> {
            if (thread.equals("SERVER_MAIN")) {
                var server = FabricServerEventBindings.currentServer();
                return server != null && server.isSameThread();
            }
            return thread.equals("CLIENT_MAIN") && Platform.isClient() && MixinClientThread.isCurrent();
        });
    }
}
