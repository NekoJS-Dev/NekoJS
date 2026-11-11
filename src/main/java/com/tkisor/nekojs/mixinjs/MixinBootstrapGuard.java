//? if neoforge {
package com.tkisor.nekojs.mixinjs;

import net.neoforged.fml.loading.FMLLoader;

/** Early metadata-only gate; never touches Minecraft classes or the runtime root. */
public final class MixinBootstrapGuard {
    private MixinBootstrapGuard() {}

    public static boolean active(String loader, String minecraft, String version, String side, String hash, boolean managed) {
        if (managed) {
            try {
                if (!hash.equals(com.tkisor.nekojs.core.mixin.MixinLibrarySelection.selected(net.neoforged.fml.loading.FMLPaths.GAMEDIR.get()))) return false;
            } catch (java.io.IOException invalidSelection) {
                return false; // Workspace preparation reports invalid selection data before root creation.
            }
        }
        var fml = FMLLoader.getCurrent();
        if (!loader.equals("neoforge") || fml.isProduction()
                || fml.getDist().isClient() != side.equals("client")) return false;
        var mods = fml.getLoadingModList().getMods();
        return mods.stream().anyMatch(mod -> mod.getModId().equals("nekojs"))
                && mods.stream().anyMatch(mod -> mod.getModId().equals("minecraft") && mod.getVersion().toString().equals(minecraft))
                && mods.stream().anyMatch(mod -> mod.getModId().equals("neoforge") && mod.getVersion().toString().equals(version));
    }
}
//?}
