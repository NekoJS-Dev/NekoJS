package com.tkisor.nekojs.testfixture;

import java.util.List;

/** Provides an empty NeoForge mod list to isolated native tests in a bare JVM. */
public final class IsolatedNativeLoaderMetadata {
    private IsolatedNativeLoaderMetadata() {}

    public static AutoCloseable open() throws Exception {
//? if neoforge {
//? if <=1.21.1 {
        /*if (net.neoforged.fml.loading.LoadingModList.get() != null) return () -> {};
        var instance = net.neoforged.fml.loading.LoadingModList.class.getDeclaredField("INSTANCE");
        instance.setAccessible(true);
        net.neoforged.fml.loading.LoadingModList.of(List.of(), List.of(), List.of(), List.of(), java.util.Map.of());
        return () -> instance.set(null, null);
*///?} else {
        if (net.neoforged.fml.loading.FMLLoader.getCurrentOrNull() != null) return () -> {};
        var constructor = net.neoforged.fml.loading.FMLLoader.class.getDeclaredConstructor(
                ClassLoader.class, String[].class, net.neoforged.api.distmarker.Dist.class, boolean.class, java.nio.file.Path.class);
        constructor.setAccessible(true);
        var loader = constructor.newInstance(IsolatedNativeLoaderMetadata.class.getClassLoader(), new String[0],
                net.neoforged.api.distmarker.Dist.DEDICATED_SERVER, false, java.nio.file.Path.of("."));
        try {
            var modList = net.neoforged.fml.loading.FMLLoader.class.getDeclaredField("loadingModList");
            modList.setAccessible(true);
            modList.set(loader, net.neoforged.fml.loading.LoadingModList.of(
                    List.of(), List.of(), List.of(), List.of(), List.of(), java.util.Map.of()));
            return loader;
        } catch (Exception failure) {
            loader.close();
            throw failure;
        }
//?}
//?} else {
        /*return () -> {};
*///?}
    }
}
