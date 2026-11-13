package com.tkisor.nekojs.platform;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import net.fabricmc.api.EnvType;
import net.fabricmc.loader.api.FabricLoader;

/**
 * Fabric-side {@link IPlatform} implementation.
 *
 * <p>It maps one to one onto {@code NeoForgePlatform} on the NeoForge side, with Fabric Loader as
 * the data source:
 * <ul>
 *   <li>{@code FMLLoader.getCurrent().getDist()} → {@link FabricLoader#getEnvironmentType()}</li>
 *   <li>{@code FMLLoader#isProduction} → {@link FabricLoader#isDevelopmentEnvironment()}</li>
 *   <li>{@code ModList.get().getMods()} → {@link FabricLoader#getAllMods()}</li>
 *   <li>{@code FMLPaths.GAMEDIR} → {@link FabricLoader#getGameDir()}</li>
 * </ul>
 *
 * <p>capabilities only declares the bits that already work on Fabric: TAGS / RESOURCE_PACKS /
 * NETWORK_CUSTOM_CHANNEL (script custom channels are registered in both directions). Recipe hot
 * reload and the recipe viewer stay dark until their Fabric implementations land. The
 * {@code IPlatform} default methods (nbtBinaryCodec / registryQueryService) keep their
 * not-implemented semantics, so scripts get empty results instead of a crash.
 */
public final class FabricPlatform implements IPlatform {
    @Override
    public Map<String, Path> loadedPackRoots() {
        Map<String, Path> roots = new LinkedHashMap<>();
        for (var mod : FabricLoader.getInstance().getAllMods()) {
            mod.findPath("nekojs/packs").filter(java.nio.file.Files::isDirectory)
                    .ifPresent(root -> roots.put(mod.getMetadata().getId(), root));
        }
        return Map.copyOf(roots);
    }

    @Override
    public void visitLoadedMixinPlans(java.util.function.BiConsumer<String, java.net.URI> visitor) {
        if (!isMixinJsEnabled()) return;
        for (var mod : FabricLoader.getInstance().getAllMods()) {
            mod.findPath("META-INF/nekojs/mixin-plan.json")
                    .ifPresent(path -> visitor.accept(mod.getMetadata().getId(), path.toUri()));
        }
    }

    private Map<String, IModInfo> modCache;

    @Override
    public boolean isClient() {
        return FabricLoader.getInstance().getEnvironmentType() == EnvType.CLIENT;
    }

    @Override
    public boolean isMixinJsEnabled() {
        return com.tkisor.nekojs.mixinjs.MixinBootstrapGuard.enabled();
    }

    @Override
    public boolean isDevelopment() {
        return FabricLoader.getInstance().isDevelopmentEnvironment();
    }

    @Override
    public String getMcVersion() {
        return FabricLoader.getInstance()
                .getModContainer("minecraft")
                .map(container -> container.getMetadata().getVersion().getFriendlyString())
                .orElse("unknown");
    }

    @Override
    public Path getGameDir() {
        return FabricLoader.getInstance().getGameDir().normalize().toAbsolutePath();
    }

    @Override
    public Map<String, IModInfo> getMods() {
        if (modCache == null) {
            modCache = new LinkedHashMap<>();
            for (var mod : FabricLoader.getInstance().getAllMods()) {
                var metadata = mod.getMetadata();
                modCache.put(
                        metadata.getId(),
                        new FabricModInfo(metadata.getId(), metadata.getName(), metadata.getVersion().getFriendlyString()));
            }
        }
        return modCache;
    }

    @Override
    public IModInfo getInfo(String modID) {
        return getMods().computeIfAbsent(modID, id -> new FabricModInfo(id, id, "unknown"));
    }

    @Override
    public Set<PlatformCapability> capabilities() {
        return Set.of(PlatformCapability.TAGS, PlatformCapability.RESOURCE_PACKS,
                PlatformCapability.NETWORK_CUSTOM_CHANNEL, PlatformCapability.CAPABILITIES);
    }

    @Override
    public String getLoaderId() {
        return "fabric";
    }

    @Override
    public String getLoaderVersion() {
        return FabricLoader.getInstance()
                .getModContainer("fabricloader")
                .map(container -> container.getMetadata().getVersion().getFriendlyString())
                .orElse("unknown");
    }
}
