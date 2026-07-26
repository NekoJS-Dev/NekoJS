package com.tkisor.nekojs.api.surface;

import com.tkisor.nekojs.api.ScriptType;

import java.util.Map;
import java.util.Objects;

public record EnvironmentKey(
        ScriptType scriptType,
        RuntimeDist dist,
        String loaderId,
        String loaderVersionRaw,
        LoaderVersion loaderVersion,
        String minecraftVersion,
        Map<String, String> installedMods
) {
    public EnvironmentKey {
        installedMods = Map.copyOf(installedMods == null ? Map.of() : installedMods);
    }
}
