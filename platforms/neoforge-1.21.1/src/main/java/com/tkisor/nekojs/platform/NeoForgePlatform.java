package com.tkisor.nekojs.platform;

import com.tkisor.nekojs.api.nbt.NbtBinaryCodec;
import com.tkisor.nekojs.api.registry.NeoForgeRegistryQueryService;
import com.tkisor.nekojs.api.registry.RegistryQueryService;
import com.tkisor.nekojs.platform.nbt.NeoForgeNbtBinaryCodec;
import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.FMLLoader;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.common.NeoForge;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class NeoForgePlatform implements IPlatform {
    @Override
    public boolean isClient() {
        return FMLLoader.getDist().isClient();
    }

    @Override
    public boolean isDevelopment() {
        return !FMLLoader.isProduction();
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

    @Override
    public Set<PlatformCapability> capabilities() {
        return Set.of(
                PlatformCapability.RECIPE_HOT_RELOAD,
                PlatformCapability.RECIPE_SCHEMA_AWARE,
                PlatformCapability.NETWORK_CUSTOM_CHANNEL,
                PlatformCapability.NBT_BINARY_IO,
                PlatformCapability.RESOURCE_PACKS,
                PlatformCapability.CLIENT_SCREENS,
                PlatformCapability.CLIENT_KEYBINDS,
                PlatformCapability.CLIENT_RENDERERS,
                PlatformCapability.TAGS
        );
    }

    @Override
    public NbtBinaryCodec nbtBinaryCodec() {
        return NeoForgeNbtBinaryCodec.INSTANCE;
    }

    @Override
    public RegistryQueryService registryQueryService() {
        return NeoForgeRegistryQueryService.INSTANCE;
    }

    @Override
    public String getLoaderId() {
        return "neoforge";
    }

    @Override
    public String getLoaderVersion() {
        IModInfo neoforge = getMods().get("neoforge");
        return neoforge != null ? neoforge.getVersion() : "0.0.0";
    }

    @Override
    public List<String> defaultScanPackages() {
        // com.mojang: MC API 签名大量引用 datafixers(Either)/brigadier/blaze3d/authlib——
        // 不放行则 probe 只生成引用它们的 import 而无声明，IDE 里这些类型全部悬空
        return List.of("net.minecraft", "net.neoforged", "com.mojang");
    }
}
