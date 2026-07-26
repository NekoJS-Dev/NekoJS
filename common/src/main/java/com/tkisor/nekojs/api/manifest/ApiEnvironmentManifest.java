package com.tkisor.nekojs.api.manifest;

import com.tkisor.nekojs.api.module.ActiveModule;
import com.tkisor.nekojs.api.module.InactiveModule;
import com.tkisor.nekojs.api.surface.ApiSurfaceSnapshot;
import com.tkisor.nekojs.api.surface.ApiSymbol;
import com.tkisor.nekojs.api.surface.EnvironmentKey;
import com.tkisor.nekojs.api.surface.ScriptTypeId;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public record ApiEnvironmentManifest(
        ScriptTypeId scriptType,
        EnvironmentKey environmentKey,
        String portableSurfaceHash,
        String environmentSurfaceHash,
        Set<String> activeCapabilities,
        List<String> activeModuleIds,
        List<String> inactiveModuleIds,
        List<ApiSymbol> symbols
) {
    public ApiEnvironmentManifest {
        Objects.requireNonNull(scriptType, "scriptType");
        Objects.requireNonNull(environmentKey, "environmentKey");
        Objects.requireNonNull(portableSurfaceHash, "portableSurfaceHash");
        Objects.requireNonNull(environmentSurfaceHash, "environmentSurfaceHash");
        activeCapabilities = Set.copyOf(activeCapabilities == null ? Set.of() : activeCapabilities);
        activeModuleIds = List.copyOf(activeModuleIds == null ? List.of() : activeModuleIds);
        inactiveModuleIds = List.copyOf(inactiveModuleIds == null ? List.of() : inactiveModuleIds);
        symbols = List.copyOf(symbols == null ? List.of() : symbols);
    }

    public static ApiEnvironmentManifest fromSnapshot(
            ApiSurfaceSnapshot snapshot,
            String portableSurfaceHash,
            String environmentSurfaceHash) {
        EnvironmentKey key = snapshot.environmentKey();
        return new ApiEnvironmentManifest(
                key.scriptType(),
                key,
                portableSurfaceHash,
                environmentSurfaceHash,
                snapshot.activeCapabilityNames(),
                snapshot.activeModules().stream()
                        .map(m -> m.descriptor().moduleId())
                        .toList(),
                snapshot.inactiveModules().stream()
                        .map(m -> m.descriptor().moduleId())
                        .toList(),
                snapshot.symbols());
    }
}
