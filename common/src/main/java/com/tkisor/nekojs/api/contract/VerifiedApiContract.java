package com.tkisor.nekojs.api.contract;

import com.tkisor.nekojs.api.surface.ApiVersion;

import java.net.URI;
import java.util.Objects;

public final class VerifiedApiContract {

    private final ApiContractIdentity identity;
    private final NormativeApiContract contract;
    private final URI codeSource;
    private final String resourceName;
    private final String integritySha256;
    private final String compatibilitySha256;

    VerifiedApiContract(ApiContractIdentity identity, NormativeApiContract contract,
                        URI codeSource, String resourceName,
                        String integritySha256, String compatibilitySha256) {
        this.identity = Objects.requireNonNull(identity, "identity");
        this.contract = Objects.requireNonNull(contract, "contract");
        this.codeSource = Objects.requireNonNull(codeSource, "codeSource");
        this.resourceName = Objects.requireNonNull(resourceName, "resourceName");
        this.integritySha256 = Objects.requireNonNull(integritySha256, "integritySha256");
        this.compatibilitySha256 = Objects.requireNonNull(compatibilitySha256, "compatibilitySha256");
    }

    public ApiContractIdentity identity() {
        return identity;
    }

    public NormativeApiContract contract() {
        return contract;
    }

    public URI codeSource() {
        return codeSource;
    }

    public String resourceName() {
        return resourceName;
    }

    public String integritySha256() {
        return integritySha256;
    }

    public String compatibilitySha256() {
        return compatibilitySha256;
    }
}
