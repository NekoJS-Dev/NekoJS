package com.tkisor.nekojs.core.api.facade;

import com.tkisor.nekojs.api.facade.ModInfoValue;
import com.tkisor.nekojs.api.facade.PlatformFacade;
import com.tkisor.nekojs.platform.IModInfo;
import com.tkisor.nekojs.platform.IPlatform;
import com.tkisor.nekojs.platform.PlatformCapability;

import java.util.List;
import java.util.Locale;
import java.util.Objects;

public final class DefaultPlatformFacade implements PlatformFacade {
    private final IPlatform platform;

    public DefaultPlatformFacade(IPlatform platform) {
        this.platform = Objects.requireNonNull(platform, "platform");
    }

    @Override
    public boolean isClient() {
        return platform.isClient();
    }

    @Override
    public boolean isDevelopment() {
        return platform.isDevelopment();
    }

    @Override
    public String getMcVersion() {
        return platform.getMcVersion();
    }

    @Override
    public String getLoaderId() {
        return platform.getLoaderId();
    }

    @Override
    public String getLoaderVersion() {
        return platform.getLoaderVersion();
    }

    @Override
    public boolean isLoaded(String modId) {
        return platform.isLoaded(requireModId(modId));
    }

    @Override
    public ModInfoValue getInfo(String modId) {
        String id = requireModId(modId);
        if (!platform.isLoaded(id)) {
            return null;
        }
        IModInfo info = platform.getInfo(id);
        return info == null ? null : new ModInfoValue(info.getId(), info.getName(), info.getVersion());
    }

    @Override
    public List<String> getList() {
        return platform.getList().stream().sorted().toList();
    }

    @Override
    public List<String> capabilities() {
        return platform.capabilities().stream()
                .map(DefaultPlatformFacade::capabilityId)
                .sorted()
                .toList();
    }

    private static String capabilityId(PlatformCapability capability) {
        return capability.name().toLowerCase(Locale.ROOT).replace('_', '-');
    }

    private static String requireModId(String modId) {
        Objects.requireNonNull(modId, "modId");
        if (modId.isBlank()) {
            throw new IllegalArgumentException("modId cannot be blank");
        }
        return modId;
    }
}
