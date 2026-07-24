package com.tkisor.nekojs.platform;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.common.ModContainer;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

public class ForgePlatform implements IPlatform {
    @Override
    public boolean isClient() {
        return FMLCommonHandler.instance().getSide() == Side.CLIENT;
    }

    @Override
    public boolean isDevelopment() {
        // In 1.12.2, check if running in deobfuscated (dev) environment
        try {
            Object val = net.minecraft.launchwrapper.Launch.blackboard.get("fml.deobfuscatedEnvironment");
            return val instanceof Boolean && (Boolean) val;
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public String getMcVersion() {
        return net.minecraftforge.common.MinecraftForge.MC_VERSION;
    }

    @Override
    public Path getGameDir() {
        // Use Loader's config dir parent (the game directory)
        return Loader.instance().getConfigDir().getParentFile().toPath().toAbsolutePath().normalize();
    }

    private Map<String, IModInfo> modCache;

    @Override
    public Map<String, IModInfo> getMods() {
        if (modCache == null) {
            modCache = new LinkedHashMap<>();
            for (ModContainer mod : Loader.instance().getActiveModList()) {
                String id = mod.getModId();
                IModInfo info = new ModInfo(
                        id,
                        mod.getName(),
                        mod.getVersion()
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
                PlatformCapability.NETWORK_CUSTOM_CHANNEL,
                PlatformCapability.RECIPE_VIEWER
        );
    }
}
