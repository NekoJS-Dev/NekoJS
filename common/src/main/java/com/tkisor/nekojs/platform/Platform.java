package com.tkisor.nekojs.platform;

import com.tkisor.nekojs.api.annotation.HideFromJS;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class Platform {
    private static volatile IPlatform INSTANCE;

    private Platform() {}

    @HideFromJS
    public static synchronized void init(IPlatform platform) {
        if (INSTANCE != null) {
            throw new IllegalStateException("Platform has already been initialized");
        }
        if (platform == null) {
            throw new NullPointerException("Platform cannot be null");
        }
        INSTANCE = platform;
    }

    private static IPlatform get() {
        if (INSTANCE == null) {
            throw new IllegalStateException("Platform has not been initialized");
        }
        return INSTANCE;
    }

    @HideFromJS
    public static IPlatform instance() {
        return get();
    }

    public static boolean isClient() {
        return get().isClient();
    }

    public static boolean isDevelopment() {
        return get().isDevelopment();
    }

    public static String getCurrentThreadName() {
        return get().getCurrentThreadName();
    }

    public static String getMcVersion() {
        return get().getMcVersion();
    }

    public static int getMcVersionInt() {
        return get().getMcVersionInt();
    }

    public static Path getGameDir() {
        return get().getGameDir();
    }

    public static Map<String, IModInfo> getMods() {
        return get().getMods();
    }

    public static IModInfo getInfo(String modID) {
        return get().getInfo(modID);
    }

    public static boolean isLoaded(String modId) {
        return get().isLoaded(modId);
    }

    public static Set<String> getList() {
        return get().getList();
    }

    public static Set<PlatformCapability> capabilities() {
        return get().capabilities();
    }

    public static String getLoaderId() {
        return get().getLoaderId();
    }

    public static String getLoaderVersion() {
        return get().getLoaderVersion();
    }

    public static List<String> defaultScanPackages() {
        return get().defaultScanPackages();
    }

    public static String recipeFieldKindPackage(com.tkisor.nekojs.api.recipe.definition.RecipeFieldKind kind) {
        return get().recipeFieldKindPackage(kind);
    }
}
