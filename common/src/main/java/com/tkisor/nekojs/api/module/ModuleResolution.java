package com.tkisor.nekojs.api.module;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Result of module resolution: the activated modules and the modules that were not activated.
 *
 * <p>Both lists are copied into immutable lists. Call {@link #inactive(String)} to look up why a
 * given module was not activated.
 */
public record ModuleResolution(
        List<ActiveModule> active,
        List<InactiveModule> inactive
) {
    public ModuleResolution {
        active = List.copyOf(active == null ? List.of() : active);
        inactive = List.copyOf(inactive == null ? List.of() : inactive);
    }

    /**
     * Returns the ids of all activated modules.
     */
    public List<String> activeIds() {
        return active.stream()
                .map(m -> m.descriptor().moduleId())
                .toList();
    }

    /**
     * Looks up the inactive entry for the given module id, if there is one.
     */
    public Optional<InactiveModule> inactive(String moduleId) {
        return inactive.stream()
                .filter(m -> m.descriptor().moduleId().equals(moduleId))
                .findFirst();
    }
}
