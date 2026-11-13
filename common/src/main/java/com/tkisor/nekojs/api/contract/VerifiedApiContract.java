package com.tkisor.nekojs.api.contract;

import com.tkisor.nekojs.api.surface.ApiVersion;

import java.net.URI;
import java.util.Objects;

/**
 * A verified API contract: the contract content bound to its source and to its integrity and
 * compatibility hashes.
 *
 * <p>Immutable; build one through
 * {@link #create(ApiContractIdentity, NormativeApiContract, URI, String, String, String)}. The
 * integrity hash checks that the contract resource was not tampered with, and the compatibility
 * hash is used when comparing contract versions.
 */
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

    /** Creates a verified contract. No argument may be {@code null}. */
    public static VerifiedApiContract create(ApiContractIdentity identity, NormativeApiContract contract,
                                             URI codeSource, String resourceName,
                                             String integritySha256, String compatibilitySha256) {
        return new VerifiedApiContract(identity, contract, codeSource, resourceName, integritySha256, compatibilitySha256);
    }

    /** Returns the contract identity. */
    public ApiContractIdentity identity() {
        return identity;
    }

    /** Returns the contract content. */
    public NormativeApiContract contract() {
        return contract;
    }

    /** Returns the URI the contract resource came from. */
    public URI codeSource() {
        return codeSource;
    }

    /** Returns the contract resource name. */
    public String resourceName() {
        return resourceName;
    }

    /** Returns the SHA-256 integrity hash of the contract resource. */
    public String integritySha256() {
        return integritySha256;
    }

    /** Returns the SHA-256 compatibility hash of the contract. */
    public String compatibilitySha256() {
        return compatibilitySha256;
    }
}
