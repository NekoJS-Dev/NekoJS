package com.tkisor.nekojs.api.module;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

public record ModuleResolution(
        List<ActiveModule> active,
        List<InactiveModule> inactive
) {
    public ModuleResolution {
        active = List.copyOf(active == null ? List.of() : active);
        inactive = List.copyOf(inactive == null ? List.of() : inactive);
    }

    public List<String> activeIds() {
        return active.stream()
                .map(m -> m.descriptor().moduleId())
                .toList();
    }

    public Optional<InactiveModule> inactive(String moduleId) {
        return inactive.stream()
                .filter(m -> m.descriptor().moduleId().equals(moduleId))
                .findFirst();
    }
}
