package com.tkisor.nekojs.probe;

import com.tkisor.nekojs.api.contract.ApiContractIdentity;
import com.tkisor.nekojs.api.contract.ApiContractKind;
import com.tkisor.nekojs.api.contract.ApiContractReader;
import com.tkisor.nekojs.api.contract.VerifiedContractSet;
import com.tkisor.nekojs.api.surface.ApiRuntimeVersions;
import com.tkisor.nekojs.api.surface.ApiVersion;
import com.tkisor.nekojs.core.api.ApiRuntimeVersionReader;
import com.tkisor.nekojs.core.api.CoreManagedApiBootstrap;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

/**
 * Holds the {@link VerifiedContractSet} used by the probe manifest generator.
 * In production, the contract set is created from the classpath api-runtime properties.
 * For testing, a pre-built contract set can be injected via {@link #init(VerifiedContractSet)}.
 */
public final class ProbeContractSetHolder {

    private static volatile VerifiedContractSet contractSet;

    private ProbeContractSetHolder() {}

    /**
     * Initialize with an explicit contract set (for testing).
     */
    public static void init(VerifiedContractSet contracts) {
        Objects.requireNonNull(contracts, "contracts");
        if (contractSet != null) {
            throw new IllegalStateException("ProbeContractSetHolder already initialized");
        }
        contractSet = contracts;
    }

    /**
     * Get the contract set, creating it from classpath if not yet initialized.
     */
    public static VerifiedContractSet contractSet() {
        if (contractSet == null) {
            synchronized (ProbeContractSetHolder.class) {
                if (contractSet == null) {
                    contractSet = createDefault();
                }
            }
        }
        return contractSet;
    }

    /**
     * Reset for testing.
     */
    static void reset() {
        contractSet = null;
    }

    private static VerifiedContractSet createDefault() {
        ApiRuntimeVersions versions = ApiRuntimeVersionReader.read();
        ApiVersion apiVersion = versions.apiVersion();
        ApiContractIdentity identity = new ApiContractIdentity(
                "nekojs-core", ApiContractKind.PORTABLE, "portable-core", apiVersion);
        var stream = ProbeContractSetHolder.class.getResourceAsStream(CoreManagedApiBootstrap.RESOURCE);
        if (stream == null) {
            throw new IllegalStateException("Core managed API contract not found: " + CoreManagedApiBootstrap.RESOURCE);
        }
        try {
            var codeSource = ProbeContractSetHolder.class.getProtectionDomain().getCodeSource().getLocation().toURI();
            try (var reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
                return VerifiedContractSet.of(ApiContractReader.readVerified(
                        reader, codeSource, CoreManagedApiBootstrap.RESOURCE, identity, null));
            }
        } catch (java.net.URISyntaxException | java.io.IOException e) {
            throw new IllegalStateException("Failed to load core managed API contract", e);
        }
    }
}
