package com.tkisor.nekojs.platform;

import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.FMLLoader;
import net.neoforged.fml.loading.FMLPaths;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

public class NeoForgePlatform implements IPlatform {
    @Override
    public boolean isClient() {
        return FMLLoader.getCurrent().getDist().isClient();
    }

    @Override
    public boolean isDevelopment() {
        return !FMLLoader.getCurrent().isProduction();
    }

    @Override
    public String getMcVersion() {
        return ModList.get()
                .getModContainerById("minecraft")
                .map(container -> container.getModInfo().getVersion().toString())
                .orElse("unknown");
    }

    @Override
    public Path getGameDir() {
        return FMLPaths.GAMEDIR.get().normalize().toAbsolutePath();
    }

    private Map<String, IModInfo> modCache;
    @Override
    public Map<String, IModInfo> getMods() {
        if (modCache == null) {
            modCache = new LinkedHashMap<>();
            for (var mod : ModList.get().getMods()) {
                String id = mod.getModId();
                // 初始化我们自己的 ModInfo 实现
                IModInfo info = new ModInfo(
                        id,
                        mod.getDisplayName(),
                        mod.getVersion().toString()
                );
                modCache.put(id, info);
            }
        }
        return modCache;
    }

    @Override
    public IModInfo getInfo(String modID) {
        return getMods().computeIfAbsent(modID, ModInfo::new);
    }
}
