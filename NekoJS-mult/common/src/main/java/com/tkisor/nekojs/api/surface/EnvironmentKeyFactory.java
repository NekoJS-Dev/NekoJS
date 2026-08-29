package com.tkisor.nekojs.api.surface;

import com.tkisor.nekojs.api.ScriptType;
import com.tkisor.nekojs.platform.IModInfo;
import com.tkisor.nekojs.platform.Platform;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public final class EnvironmentKeyFactory {

    private EnvironmentKeyFactory() {
    }

    public static EnvironmentKey current(ScriptType scriptType) {
        Objects.requireNonNull(scriptType, "scriptType");

        RuntimeDist dist = Platform.isClient() ? RuntimeDist.CLIENT : RuntimeDist.DEDICATED_SERVER;
        String loaderId = Platform.getLoaderId();
        String loaderVersionRaw = Platform.getLoaderVersion();
        LoaderVersion loaderVersion = LoaderVersion.parse(loaderVersionRaw);
        String minecraftVersion = Platform.getMcVersion();

        Map<String, String> installedMods = new LinkedHashMap<>();
        for (Map.Entry<String, IModInfo> entry : Platform.getMods().entrySet()) {
            installedMods.put(entry.getKey(), entry.getValue().getVersion());
        }

        return new EnvironmentKey(
                ScriptTypeId.fromScriptType(scriptType),
                dist,
                loaderId,
                loaderVersionRaw,
                loaderVersion,
                minecraftVersion,
                installedMods);
    }
}
