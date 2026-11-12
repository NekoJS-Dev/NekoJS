//? if neoforge {
package com.tkisor.nekojs.mixinjs;

import com.tkisor.nekojs.core.mixin.MixinCarriers;
import com.tkisor.nekojs.core.mixin.tool.MixinPreparation;
import com.tkisor.nekojs.platform.Platform;

/** Owns next-launch preparation, before any script generation or runtime root is created. */
public final class MixinWorkspace {
    private MixinWorkspace() {}

    public static void prepare() {
        var platform = Platform.instance();
        if (!platform.isMixinJsEnabled()) return;
        try {
            boolean restart = MixinPreparation.prepare(platform.getGameDir(), MixinCarriers.currentProfile(),
                    platform.isDevelopment(), MixinCarriers.readAdmitted(), name -> {
                        String resource = name.replace('.', '/') + ".class";
                        try (var input = MixinWorkspace.class.getClassLoader().getResourceAsStream(resource)) {
                            if (input == null) throw new IllegalArgumentException("[NEKO-4090] Mixin target bytecode is unavailable: " + name);
                            return input.readAllBytes();
                        }
                    });
            if (restart) throw new IllegalStateException("[NEKO-4090] Managed MixinJS library updated; restart Minecraft before scripts can load");
        } catch (Exception failure) {
            throw new IllegalStateException("[NEKO-4090] MixinJS workspace preparation stopped startup: " + failure.getMessage(), failure);
        }
    }
}
//?}
