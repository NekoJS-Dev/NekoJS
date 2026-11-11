package com.tkisor.nekojs.mixinjs;

import net.neoforged.fml.loading.FMLLoader;

/** Early metadata-only gate for the legacy loader. */
public final class MixinBootstrapGuard {
    private MixinBootstrapGuard() {}

    public static boolean active(String loader, String minecraft, String version, String side, String hash, boolean managed) {
        if (managed) return false;
        if (!loader.equals("neoforge") || FMLLoader.isProduction()
                || FMLLoader.getDist().isClient() != side.equals("client")) return false;
        var mods = FMLLoader.getLoadingModList().getMods();
        return mods.stream().anyMatch(mod -> mod.getModId().equals("nekojs"))
                && mods.stream().anyMatch(mod -> mod.getModId().equals("minecraft") && mod.getVersion().toString().equals(minecraft))
                && mods.stream().anyMatch(mod -> mod.getModId().equals("neoforge") && mod.getVersion().toString().equals(version));
    }
}
