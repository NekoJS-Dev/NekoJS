package com.tkisor.nekojs.platform;

import com.tkisor.nekojs.api.nbt.NbtBinaryCodec;
import com.tkisor.nekojs.api.registry.ForgeRegistryQueryService;
import com.tkisor.nekojs.api.registry.RegistryQueryService;
import com.tkisor.nekojs.platform.nbt.CleanroomNbtBinaryCodec;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.common.ModContainer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

public class ForgePlatform implements IPlatform {
    private static final Logger LOGGER = LogManager.getLogger("ForgePlatform");
    private static final NbtBinaryCodec NBT_BINARY_CODEC = CleanroomNbtBinaryCodec.INSTANCE;

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
                PlatformCapability.NBT_BINARY_IO,
                PlatformCapability.RECIPE_VIEWER
        );
    }

    @Override
    public NbtBinaryCodec nbtBinaryCodec() {
        return NBT_BINARY_CODEC;
    }

    @Override
    public RegistryQueryService registryQueryService() {
        return ForgeRegistryQueryService.INSTANCE;
    }

    @Override
    public String getLoaderId() {
        return "cleanroom";
    }

    @Override
    public String getLoaderVersion() {
        IModInfo cleanroom = getMods().get("cleanroom");
        if (cleanroom != null) return cleanroom.getVersion();
        IModInfo forge = getMods().get("forge");
        if (forge != null) return forge.getVersion();
        LOGGER.warn("Neither 'cleanroom' nor 'forge' mod found for loader version; defaulting to 0.0.0");
        return "0.0.0";
    }
}
