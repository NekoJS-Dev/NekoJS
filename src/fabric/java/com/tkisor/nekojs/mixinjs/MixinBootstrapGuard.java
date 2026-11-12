package com.tkisor.nekojs.mixinjs;

import net.fabricmc.loader.api.FabricLoader;

/** Fabric metadata gate available before normal mod initialization. */
public final class MixinBootstrapGuard {
    private static final boolean ENABLED = com.tkisor.nekojs.core.config.SandboxConfigLoader
            .readMixinJsEnabled(FabricLoader.getInstance().getGameDir());

    private MixinBootstrapGuard() {}

    /** Startup snapshot shared by transformation guards and normal runtime admission. */
    public static boolean enabled() { return ENABLED; }

    public static boolean active(String loader, String minecraft, String version, String side, String hash, boolean managed) {
        if (!enabled()) return false;
        if (managed) return false;
        var fabric = FabricLoader.getInstance();
        return loader.equals("fabric") && fabric.isDevelopmentEnvironment() && fabric.isModLoaded("nekojs")
                && fabric.getEnvironmentType().name().equalsIgnoreCase(side)
                && fabric.getModContainer("minecraft").map(mod -> mod.getMetadata().getVersion().getFriendlyString().equals(minecraft)).orElse(false)
                && fabric.getModContainer("fabricloader").map(mod -> mod.getMetadata().getVersion().getFriendlyString().equals(version)).orElse(false);
    }
}
