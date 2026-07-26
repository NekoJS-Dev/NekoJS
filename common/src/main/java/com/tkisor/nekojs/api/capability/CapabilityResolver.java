package com.tkisor.nekojs.api.capability;

import com.tkisor.nekojs.api.plugin.PluginIdentity;
import com.tkisor.nekojs.api.surface.ApiResolutionException;
import com.tkisor.nekojs.api.surface.EnvironmentKey;
import com.tkisor.nekojs.api.surface.EnvironmentScope;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

public final class CapabilityResolver {

    private CapabilityResolver() {
    }

    public static CapabilityResolution resolve(
            EnvironmentKey environmentKey,
            List<CapabilityDefinition> definitions,
            List<CapabilityProviderContribution> providers) {

        Objects.requireNonNull(environmentKey, "environmentKey");
        Objects.requireNonNull(definitions, "definitions");
        Objects.requireNonNull(providers, "providers");

        // Step 1: Validate no duplicate definition names
        validateNoDuplicateDefinitions(definitions);

        // Index definitions by name
        Map<String, CapabilityDefinition> defByName = new LinkedHashMap<>();
        for (CapabilityDefinition def : definitions) {
            defByName.put(def.name(), def);
        }

        // Group providers by capability name
        Map<String, List<CapabilityProviderContribution>> providersByName = new HashMap<>();
        for (CapabilityProviderContribution provider : providers) {
            providersByName.computeIfAbsent(provider.capabilityName(), k -> new ArrayList<>()).add(provider);
        }

        List<ActiveCapability> active = new ArrayList<>();
        List<CapabilityResolution.UnavailableCapability> unavailable = new ArrayList<>();

        // Process each definition
        for (CapabilityDefinition def : definitions) {
            List<CapabilityProviderContribution> candidates = providersByName.getOrDefault(def.name(), List.of());

            // Step 3: Validate services coverage
            for (CapabilityProviderContribution provider : candidates) {
                validateServicesCoverage(def, provider);
            }

            // Step 4: Validate scope containment
            for (CapabilityProviderContribution provider : candidates) {
                validateScopeContainment(def, provider);
            }

            // Step 5: Validate provider policy (CORE_ONLY / ALLOWLIST)
            for (CapabilityProviderContribution provider : candidates) {
                validateProviderPolicy(def, provider);
            }

            // Step 6: Filter eligible providers for this environment
            List<CapabilityProviderContribution> eligible = candidates.stream()
                    .filter(p -> isEligible(environmentKey, p))
                    .toList();

            if (eligible.isEmpty()) {
                // No eligible provider: UNAVAILABLE
                unavailable.add(new CapabilityResolution.UnavailableCapability(
                        def.name(), "UNAVAILABLE"));
            } else if (eligible.size() == 1) {
                // Single eligible provider: activate
                CapabilityProviderContribution provider = eligible.getFirst();
                active.add(new ActiveCapability(def.name(), def.mode(), provider.implementation()));
            } else {
                // Multiple eligible providers: fail-fast
                String providerNames = eligible.stream()
                        .map(p -> p.provider().ownerId() + ":" + p.provider().pluginClassName())
                        .collect(Collectors.joining(", "));
                throw new ApiResolutionException("DUPLICATE_CAPABILITY_PROVIDER",
                        "Capability '" + def.name() + "' has multiple eligible providers: [" + providerNames + "]",
                        Map.of("capability", def.name(), "providers", providerNames));
            }
        }

        return new CapabilityResolution(active, unavailable);
    }

    private static void validateNoDuplicateDefinitions(List<CapabilityDefinition> definitions) {
        Set<String> names = new java.util.HashSet<>();
        for (CapabilityDefinition def : definitions) {
            if (!names.add(def.name())) {
                throw new ApiResolutionException("DUPLICATE_CAPABILITY_DEFINITION",
                        "Duplicate capability definition: " + def.name(),
                        Map.of("capability", def.name()));
            }
        }
    }

    private static void validateServicesCoverage(
            CapabilityDefinition def,
            CapabilityProviderContribution provider) {
        Set<String> required = def.requiredServiceKeys();
        if (required.isEmpty()) return;

        Set<String> provided = provider.services().keySet();
        for (String key : required) {
            if (!provided.contains(key)) {
                throw new ApiResolutionException("MISSING_SERVICE_KEY",
                        "Provider " + provider.provider() + " missing required service key '" + key
                                + "' for capability '" + def.name() + "'",
                        Map.of("capability", def.name(),
                                "provider", provider.provider().ownerId() + ":" + provider.provider().pluginClassName(),
                                "missingKey", key));
            }
        }
    }

    private static void validateScopeContainment(
            CapabilityDefinition def,
            CapabilityProviderContribution provider) {
        if (def.environmentScope() == null || provider.environmentScope() == null) return;

        EnvironmentScope defScope = def.environmentScope();
        EnvironmentScope provScope = provider.environmentScope();

        // Provider scope must be equal or narrower than definition scope.
        // Narrower means: provider can require MORE mods, restrict to FEWER loaders, etc.

        // If definition restricts to a script type, provider must also restrict to the same or a subset
        if (defScope.scriptType() != null && provScope.scriptType() != null
                && defScope.scriptType() != provScope.scriptType()) {
            throw new ApiResolutionException("SCOPE_NOT_CONTAINED",
                    "Provider scriptType " + provScope.scriptType()
                            + " is not contained in definition scope " + defScope.scriptType(),
                    Map.of("definitionScope", defScope.toString(), "providerScope", provScope.toString()));
        }

        // If definition restricts to a dist, provider must also restrict to the same or a subset
        if (defScope.dist() != null && provScope.dist() != null
                && defScope.dist() != provScope.dist()) {
            throw new ApiResolutionException("SCOPE_NOT_CONTAINED",
                    "Provider dist " + provScope.dist()
                            + " is not contained in definition scope " + defScope.dist(),
                    Map.of("definitionScope", defScope.toString(), "providerScope", provScope.toString()));
        }

        // Provider requiredMods must be a superset of definition requiredMods
        if (!defScope.requiredMods().isEmpty() && !provScope.requiredMods().containsAll(defScope.requiredMods())) {
            throw new ApiResolutionException("SCOPE_NOT_CONTAINED",
                    "Provider requiredMods " + provScope.requiredMods()
                            + " does not contain all definition requiredMods " + defScope.requiredMods(),
                    Map.of("definitionScope", defScope.toString(), "providerScope", provScope.toString()));
        }

        // If definition restricts allowedLoaderIds, provider must be a subset
        if (!defScope.allowedLoaderIds().isEmpty() && !provScope.allowedLoaderIds().isEmpty()
                && !defScope.allowedLoaderIds().containsAll(provScope.allowedLoaderIds())) {
            throw new ApiResolutionException("SCOPE_NOT_CONTAINED",
                    "Provider allowedLoaderIds " + provScope.allowedLoaderIds()
                            + " is not a subset of definition allowedLoaderIds " + defScope.allowedLoaderIds(),
                    Map.of("definitionScope", defScope.toString(), "providerScope", provScope.toString()));
        }
    }

    private static void validateProviderPolicy(
            CapabilityDefinition def,
            CapabilityProviderContribution provider) {
        PluginIdentity identity = provider.provider();

        switch (def.providerPolicy()) {
            case CORE_ONLY -> {
                // Only core (nekojs) can provide
                if (!"nekojs".equals(identity.ownerId())) {
                    throw new ApiResolutionException("CORE_ONLY_VIOLATION",
                            "Addon '" + identity.ownerId() + "' cannot provide core capability '"
                                    + def.name() + "' (CORE_ONLY policy)",
                            Map.of("capability", def.name(),
                                    "provider", identity.ownerId() + ":" + identity.pluginClassName()));
                }
            }
            case ALLOWLIST -> {
                if (!def.allowedProviderOwners().contains(identity.ownerId())
                        && !"nekojs".equals(identity.ownerId())) {
                    throw new ApiResolutionException("PROVIDER_NOT_ALLOWED",
                            "Provider '" + identity.ownerId() + "' not in allowlist for capability '"
                                    + def.name() + "'",
                            Map.of("capability", def.name(),
                                    "provider", identity.ownerId() + ":" + identity.pluginClassName()));
                }
            }
        }
    }

    private static boolean isEligible(
            EnvironmentKey environmentKey,
            CapabilityProviderContribution provider) {
        if (provider.environmentScope() == null) return true;
        return provider.environmentScope().matches(environmentKey);
    }
}
