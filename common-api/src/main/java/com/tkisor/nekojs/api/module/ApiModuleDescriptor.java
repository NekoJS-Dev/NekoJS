package com.tkisor.nekojs.api.module;

import com.tkisor.nekojs.api.contract.ApiContractIdentity;
import com.tkisor.nekojs.api.surface.ApiTier;
import com.tkisor.nekojs.api.surface.ApiVersion;

import java.util.List;
import java.util.Objects;

public record ApiModuleDescriptor(
        String moduleId,
        ApiTier tier,
        ApiVersion contractVersion,
        int moduleRevision,
        ApiContractIdentity contractIdentity,
        List<ApiModuleDependency> dependencies
) {
    public ApiModuleDescriptor {
        Objects.requireNonNull(moduleId, "moduleId");
        Objects.requireNonNull(tier, "tier");
        dependencies = List.copyOf(dependencies == null ? List.of() : dependencies);

        // Tier discrimination
        switch (tier) {
            case FEATURE, PLATFORM, ADDON -> {
                if (contractVersion == null) {
                    throw new IllegalArgumentException(
                            tier + " module must have contractVersion");
                }
            }
            case VERSION, UNSAFE_NATIVE -> {
                if (moduleRevision < 1) {
                    throw new IllegalArgumentException(
                            tier + " module must have positive moduleRevision");
                }
            }
            default -> {}
        }
    }
}
