package com.tkisor.nekojs.api.surface;

import com.tkisor.nekojs.api.module.ActiveModule;
import com.tkisor.nekojs.api.module.InactiveModule;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public record ApiSurfaceSnapshot(
        List<ApiSymbol> symbols,
        Set<String> activeCapabilityNames,
        List<ActiveModule> activeModules,
        List<InactiveModule> inactiveModules,
        EnvironmentKey environmentKey
) {
    public ApiSurfaceSnapshot {
        symbols = List.copyOf(symbols == null ? List.of() : symbols);
        activeCapabilityNames = Set.copyOf(activeCapabilityNames == null ? Set.of() : activeCapabilityNames);
        activeModules = List.copyOf(activeModules == null ? List.of() : activeModules);
        inactiveModules = List.copyOf(inactiveModules == null ? List.of() : inactiveModules);
        Objects.requireNonNull(environmentKey, "environmentKey");
    }
}
