package com.tkisor.nekojs.api.surface;

import com.tkisor.nekojs.api.capability.CapabilityImplementationMode;
import com.tkisor.nekojs.api.capability.CapabilityProviderContribution;
import com.tkisor.nekojs.api.contract.ApiContractIdentity;
import com.tkisor.nekojs.api.contract.VerifiedApiContract;
import com.tkisor.nekojs.api.contract.VerifiedContractSet;
import com.tkisor.nekojs.api.plugin.PluginIdentity;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class ApiContributionRegistry {

    private final PluginIdentity owner;
    private final VerifiedContractSet ownerContracts;
    private final List<ApiContribution> symbolContributions = new ArrayList<>();
    private final List<CapabilityProviderContribution> capabilityProviders = new ArrayList<>();

    private ApiContributionRegistry(PluginIdentity owner, VerifiedContractSet ownerContracts) {
        this.owner = Objects.requireNonNull(owner, "owner");
        this.ownerContracts = Objects.requireNonNull(ownerContracts, "ownerContracts");
    }

    public static ApiContributionRegistry ownedBy(PluginIdentity identity, VerifiedContractSet ownerContracts) {
        validateOwnership(identity, ownerContracts);
        return new ApiContributionRegistry(identity, ownerContracts);
    }

    private static void validateOwnership(PluginIdentity identity, VerifiedContractSet contracts) {
        for (VerifiedApiContract contract : contracts.all()) {
            ApiContractIdentity contractIdentity = contract.identity();
            if (!contractIdentity.owner().equals(identity.ownerId())) {
                throw new ApiResolutionException("OWNER_MISMATCH",
                        "Contract owner '" + contractIdentity.owner()
                                + "' does not match identity owner '" + identity.ownerId() + "'",
                        Map.of("contractOwner", contractIdentity.owner(),
                                "identityOwner", identity.ownerId()));
            }
        }
    }

    public void registerSymbol(ApiContribution contribution) {
        Objects.requireNonNull(contribution, "contribution");
        validateContributionMatchesContract(contribution);
        symbolContributions.add(contribution);
    }

    public void registerCapabilityProvider(
            String capabilityId,
            ApiVersion contractVersion,
            CapabilityImplementationMode mode,
            EnvironmentScope scope,
            Object implementation,
            Map<String, Object> services) {
        Objects.requireNonNull(capabilityId, "capabilityId");
        Objects.requireNonNull(contractVersion, "contractVersion");
        Objects.requireNonNull(mode, "mode");
        Objects.requireNonNull(implementation, "implementation");
        Objects.requireNonNull(services, "services");

        CapabilityProviderContribution provider = new CapabilityProviderContribution(
                owner,
                capabilityId,
                100,
                implementation,
                scope,
                services);
        capabilityProviders.add(provider);
    }

    private void validateContributionMatchesContract(ApiContribution contribution) {
        ApiSymbolId symbolId = contribution.symbolId();
        ApiTier tier = contribution.tier();

        boolean found = false;
        for (VerifiedApiContract contract : ownerContracts.all()) {
            if (matchesContract(contract, symbolId, tier)) {
                found = true;
                break;
            }
        }

        if (!found) {
            throw new ApiResolutionException("CONTRIBUTION_NO_CONTRACT",
                    "No matching contract found for contribution " + symbolId + " at tier " + tier,
                    Map.of("symbolId", symbolId.value(), "tier", tier.name()));
        }
    }

    private boolean matchesContract(VerifiedApiContract contract, ApiSymbolId symbolId, ApiTier tier) {
        if (contract.identity().kind() == com.tkisor.nekojs.api.contract.ApiContractKind.PORTABLE) {
            return contract.contract().symbols().stream()
                    .anyMatch(s -> s.id().equals(symbolId));
        }

        return contract.contract().modules().stream()
                .anyMatch(m -> m.tier() == tier && m.symbols().stream()
                        .anyMatch(s -> s.id().equals(symbolId)));
    }

    public PluginIdentity owner() {
        return owner;
    }

    public VerifiedContractSet ownerContracts() {
        return ownerContracts;
    }

    public List<ApiContribution> symbolContributions() {
        return List.copyOf(symbolContributions);
    }

    public List<CapabilityProviderContribution> capabilityProviders() {
        return List.copyOf(capabilityProviders);
    }
}
