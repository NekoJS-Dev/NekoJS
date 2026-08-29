package com.tkisor.nekojs.platform;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import net.fabricmc.api.EnvType;
import net.fabricmc.loader.api.FabricLoader;

/**
 * Fabric 侧 {@link IPlatform} 实现（B3：LoaderBridge 的第一块）。
 *
 * <p>与 NeoForge 侧的 {@code NeoForgePlatform} 一一对应，只是数据源换成 Fabric Loader：
 * <ul>
 *   <li>{@code FMLLoader.getCurrent().getDist()} → {@link FabricLoader#getEnvironmentType()}</li>
 *   <li>{@code FMLLoader#isProduction} → {@link FabricLoader#isDevelopmentEnvironment()}</li>
 *   <li>{@code ModList.get().getMods()} → {@link FabricLoader#getAllMods()}</li>
 *   <li>{@code FMLPaths.GAMEDIR} → {@link FabricLoader#getGameDir()}</li>
 * </ul>
 *
 * <p>capabilities 只声明 fabric 侧已经成立的位：TAGS / RESOURCE_PACKS。网络通道、
 * 配方热重载、配方查看器等要等 LoaderBridge 对应实现落地后再点亮——`IPlatform` 的
 * 默认方法（nbtBinaryCodec / registryQueryService）保持未实现语义，脚本侧会拿到空结果
 * 而不是崩溃。
 */
public final class FabricPlatform implements IPlatform {

    private Map<String, IModInfo> modCache;

    @Override
    public boolean isClient() {
        return FabricLoader.getInstance().getEnvironmentType() == EnvType.CLIENT;
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
        return Set.of(PlatformCapability.TAGS, PlatformCapability.RESOURCE_PACKS);
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
