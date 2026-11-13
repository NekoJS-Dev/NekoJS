// 1.21.1 implementation, paired with the same-named 26.x file under the version tree src/. Its
// content is that file as evaluated at this node. Keep this file in sync when the 26.x side changes.
package com.tkisor.nekojs.platform;

import com.tkisor.nekojs.api.nbt.NbtBinaryCodec;
import com.tkisor.nekojs.api.registry.RegistryQueryService;
import com.tkisor.nekojs.api.registry.NeoForgeRegistryQueryService;
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
    public Map<String, Path> loadedPackRoots() {
        Map<String, Path> roots = new LinkedHashMap<>();
        for (var mod : ModList.get().getMods()) {
            Path root = mod.getOwningFile().getFile().findResource("nekojs", "packs");
            if (java.nio.file.Files.isDirectory(root)) roots.put(mod.getModId(), root);
        }
        return Map.copyOf(roots);
    }

    @Override
    public void visitLoadedMixinPlans(java.util.function.BiConsumer<String, java.net.URI> visitor) {
        if (!isMixinJsEnabled()) return;
        for (var mod : ModList.get().getMods()) {
            Path plan = mod.getOwningFile().getFile().findResource("META-INF", "nekojs", "mixin-plan.json");
            if (java.nio.file.Files.isRegularFile(plan)) visitor.accept(mod.getModId(), plan.toUri());
        }
    }

    @Override
    public boolean isClient() {
        return FMLLoader.getDist().isClient();
    }

    @Override
    public boolean isMixinJsEnabled() {
        return com.tkisor.nekojs.mixinjs.MixinBootstrapGuard.enabled();
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
                // Builds our own ModInfo implementation.
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
                PlatformCapability.TAGS,
                PlatformCapability.RECIPE_VIEWER,
                PlatformCapability.CAPABILITIES
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
        // com.mojang: Minecraft API signatures reference datafixers (Either), brigadier, blaze3d and authlib
        // heavily. Without it the probe emits imports for those types but no declarations, leaving them
        // unresolved in the IDE.
        return List.of("net.minecraft", "net.neoforged", "com.mojang");
    }
}
