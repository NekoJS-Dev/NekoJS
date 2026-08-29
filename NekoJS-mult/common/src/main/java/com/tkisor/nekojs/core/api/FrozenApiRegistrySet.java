package com.tkisor.nekojs.core.api;

import com.tkisor.nekojs.api.contract.VerifiedContractSet;
import com.tkisor.nekojs.api.surface.ApiContributionRegistry;
import com.tkisor.nekojs.api.surface.ApiResolutionException;
import com.tkisor.nekojs.api.surface.ApiRuntimeProvider;
import com.tkisor.nekojs.api.surface.ApiRuntimeView;
import com.tkisor.nekojs.api.surface.EnvironmentKey;
import com.tkisor.nekojs.api.surface.LegacyGlobalReservation;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

public final class FrozenApiRegistrySet implements ApiRuntimeProvider {

    private final VerifiedContractSet contracts;
    private final List<ApiContributionRegistry> contributionRegistries;
    private final List<LegacyGlobalReservation> legacyReservations;
    private final List<EnvironmentKey> environmentKeys;
    private final Map<EnvironmentKey, FrozenApiRegistry> cache = new ConcurrentHashMap<>();

    public FrozenApiRegistrySet(
            VerifiedContractSet contracts,
            List<ApiContributionRegistry> contributionRegistries,
            List<LegacyGlobalReservation> legacyReservations,
            List<EnvironmentKey> environmentKeys) {
        this.contracts = Objects.requireNonNull(contracts, "contracts");
        this.contributionRegistries = List.copyOf(
                contributionRegistries == null ? List.of() : contributionRegistries);
        this.legacyReservations = List.copyOf(
                legacyReservations == null ? List.of() : legacyReservations);
        this.environmentKeys = List.copyOf(
                environmentKeys == null ? List.of() : environmentKeys);
    }

    @Override
    public ApiRuntimeView view(EnvironmentKey key) {
        Objects.requireNonNull(key, "key");
        return registry(key);
    }

    public FrozenApiRegistry registry(EnvironmentKey key) {
        Objects.requireNonNull(key, "key");
        return cache.computeIfAbsent(key, k -> {
            if (!environmentKeys.contains(k)) {
                throw new ApiResolutionException("UNSUPPORTED_ENVIRONMENT",
                        "No environment key found matching " + k,
                        Map.of("environmentKey", k.toString()));
            }
            return JsApiSurfaceResolver.resolve(k, contracts, contributionRegistries, legacyReservations);
        });
    }

    public List<EnvironmentKey> environmentKeys() {
        return environmentKeys;
    }

    public VerifiedContractSet contracts() {
        return contracts;
    }
}
