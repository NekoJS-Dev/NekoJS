package com.tkisor.nekojs.api.surface;

import com.tkisor.nekojs.api.ScriptType;

import java.util.Map;
import java.util.Objects;
import java.util.Set;

public record EnvironmentScope(
        ScriptType scriptType,
        RuntimeDist dist,
        Set<String> requiredMods,
        Set<String> allowedLoaderIds,
        LoaderVersionRange loaderVersionRange,
        String minecraftVersionRange
) {
    public EnvironmentScope {
        requiredMods = Set.copyOf(requiredMods == null ? Set.of() : requiredMods);
        allowedLoaderIds = Set.copyOf(allowedLoaderIds == null ? Set.of() : allowedLoaderIds);
    }

    public boolean matches(EnvironmentKey key) {
        Objects.requireNonNull(key, "key");
        if (scriptType != null && scriptType != key.scriptType()) return false;
        if (dist != null && dist != key.dist()) return false;
        if (!allowedLoaderIds.isEmpty() && key.loaderId() != null
                && !allowedLoaderIds.contains(key.loaderId())) return false;
        if (loaderVersionRange != null && key.loaderVersion() != null
                && !loaderVersionRange.matches(key.loaderVersion())) return false;
        if (requiredMods != null && !requiredMods.isEmpty()) {
            Map<String, String> installed = key.installedMods();
            for (String mod : requiredMods) {
                if (!installed.containsKey(mod)) return false;
            }
        }
        // minecraftVersionRange matching is simplified for phase 0-1
        return true;
    }
}
