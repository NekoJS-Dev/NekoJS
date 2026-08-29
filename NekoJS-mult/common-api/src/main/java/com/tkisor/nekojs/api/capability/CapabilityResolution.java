package com.tkisor.nekojs.api.capability;

import java.util.List;
import java.util.Map;
import java.util.Objects;

public record CapabilityResolution(
        List<ActiveCapability> active,
        List<UnavailableCapability> unavailable
) {
    public CapabilityResolution {
        active = List.copyOf(active == null ? List.of() : active);
        unavailable = List.copyOf(unavailable == null ? List.of() : unavailable);
    }

    public record UnavailableCapability(String name, String reason) {
        public UnavailableCapability {
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(reason, "reason");
        }
    }
}
