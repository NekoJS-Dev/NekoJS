// 26.x 实现，本文件不应再出现版本守卫。1.21.1 的实现是 versions/1.21.1/src 下的同名文件，
// 改本文件行为时须同步它。
//? if neoforge {
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
    public void visitLoadedPackRoots(java.util.function.BiConsumer<String, Path> visitor) {
        for (var mod : ModList.get().getMods()) {
            mod.getOwningFile().getFile().getContents()
                    .findFile("nekojs/packs/" + mod.getModId() + "/manifest.json")
                    .ifPresent(uri -> com.tkisor.nekojs.core.pack.LoadedModPackSource.visitManifestUri(mod.getModId(), uri, visitor));
        }
    }
    @Override
    public void visitLoadedMixinPlans(java.util.function.BiConsumer<String, java.net.URI> visitor) {
        if (!isMixinJsEnabled()) return;
        for (var mod : ModList.get().getMods()) {
            mod.getOwningFile().getFile().getContents().findFile("META-INF/nekojs/mixin-plan.json")
                    .ifPresent(uri -> visitor.accept(mod.getModId(), uri));
        }
        String selected;
        try {
            selected = com.tkisor.nekojs.core.mixin.MixinLibrarySelection.selected(getGameDir());
        } catch (java.io.IOException invalidSelection) {
            throw new IllegalStateException("[NEKO-4090] Managed mixin selection could not be read", invalidSelection);
        }
        for (var library : FMLLoader.getCurrent().getLoadingModList().getGameLibraries()) {
            var contents = library.getContents();
            var manifest = contents.getManifest();
            if (manifest == null) continue;
            String managed = manifest.getMainAttributes().getValue("NekoJS-Mixin-Managed");
            if (managed != null && !managed.equals(selected)) continue;
            String owner = manifest.getMainAttributes().getValue("NekoJS-Mixin-Owner");
            if (owner != null) contents.findFile("META-INF/nekojs/mixin-plan.json")
                    .ifPresent(uri -> visitor.accept(owner, uri));
        }
    }
    private static final NbtBinaryCodec NBT_BINARY_CODEC = NeoForgeNbtBinaryCodec.INSTANCE;

    @Override
    public boolean isClient() {
        return FMLLoader.getCurrent().getDist().isClient();
    }

    @Override
    public boolean isMixinJsEnabled() {
        return com.tkisor.nekojs.mixinjs.MixinBootstrapGuard.enabled();
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
        return NBT_BINARY_CODEC;
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
//?}
