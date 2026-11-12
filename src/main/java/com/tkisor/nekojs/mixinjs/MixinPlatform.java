//? if neoforge {
package com.tkisor.nekojs.mixinjs;

import com.tkisor.nekojs.core.lifecycle.NekoRuntimeRoot;
import com.tkisor.nekojs.core.mixin.MixinCarriers;
import com.tkisor.nekojs.platform.Platform;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

/** Native thread checks run before any script Context can be entered. */
public final class MixinPlatform {
    private MixinPlatform() {}
    public static void install(NekoRuntimeRoot root) {
        if (!Platform.instance().isMixinJsEnabled()) return;
        root.mixins().install(MixinCarriers.discover(), MixinCarriers.currentProfile(), thread -> {
            if (thread.equals("SERVER_MAIN")) {
                var server = ServerLifecycleHooks.getCurrentServer();
                return server != null && server.isSameThread();
            }
            return thread.equals("CLIENT_MAIN") && Platform.isClient() && MixinClientThread.isCurrent();
        });
    }
}
//?}
