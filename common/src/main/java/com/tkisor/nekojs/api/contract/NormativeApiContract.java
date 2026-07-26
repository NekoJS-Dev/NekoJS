package com.tkisor.nekojs.api.contract;

import com.tkisor.nekojs.api.surface.ApiSymbol;
import com.tkisor.nekojs.api.surface.ApiVersion;

import java.util.List;
import java.util.Map;
import java.util.Objects;

public record NormativeApiContract(
        int schemaVersion,
        ContractIdentity identity,
        String docs,
        List<ApiSymbol> symbols,
        List<ContractCapability> capabilities,
        List<ContractModule> modules
) {
    public NormativeApiContract {
        Objects.requireNonNull(identity, "identity");
        symbols = List.copyOf(symbols == null ? List.of() : symbols);
        capabilities = List.copyOf(capabilities == null ? List.of() : capabilities);
        modules = List.copyOf(modules == null ? List.of() : modules);
    }

    public record ContractIdentity(String owner, ApiContractKind kind, String contractId, ApiVersion version) {
        public ContractIdentity {
            Objects.requireNonNull(owner, "owner");
            Objects.requireNonNull(kind, "kind");
            Objects.requireNonNull(contractId, "contractId");
            Objects.requireNonNull(version, "version");
        }
    }

    public record ContractCapability(String id, String contractVersionRange, String docs) {
        public ContractCapability {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(contractVersionRange, "contractVersionRange");
        }
    }

    public record ContractModule(
            String id,
            com.tkisor.nekojs.api.surface.ApiTier tier,
            ApiVersion contractVersion,
            int moduleRevision,
            String docs,
            List<ApiSymbol> symbols,
            List<ContractModuleDependency> dependencies
    ) {
        public ContractModule {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(tier, "tier");
            symbols = List.copyOf(symbols == null ? List.of() : symbols);
            dependencies = List.copyOf(dependencies == null ? List.of() : dependencies);
        }
    }

    public record ContractModuleDependency(
            String moduleId,
            String versionRange,
            com.tkisor.nekojs.api.surface.ApiTier targetTier
    ) {
        public ContractModuleDependency {
            Objects.requireNonNull(moduleId, "moduleId");
        }
    }
}
