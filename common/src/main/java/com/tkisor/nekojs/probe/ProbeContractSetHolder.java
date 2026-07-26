package com.tkisor.nekojs.probe;

import com.tkisor.nekojs.api.contract.ApiContractIdentity;
import com.tkisor.nekojs.api.contract.ApiContractKind;
import com.tkisor.nekojs.api.contract.ApiContractReader;
import com.tkisor.nekojs.api.contract.NormativeApiContract;
import com.tkisor.nekojs.api.contract.VerifiedApiContract;
import com.tkisor.nekojs.api.contract.VerifiedContractSet;
import com.tkisor.nekojs.api.surface.ApiRuntimeVersions;
import com.tkisor.nekojs.api.surface.ApiVersion;
import com.tkisor.nekojs.core.api.ApiRuntimeVersionReader;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
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
                "nekojs", ApiContractKind.PORTABLE, "nekojs-portable", apiVersion);

        NormativeApiContract contract = new NormativeApiContract(
                versions.catalogSchemaVersion(),
                new NormativeApiContract.ContractIdentity(
                        "nekojs", ApiContractKind.PORTABLE, "nekojs-portable", apiVersion),
                "Portable core contract",
                List.of(),
                List.of(),
                List.of());

        String hash = sha256Hex(contract.toString().getBytes(StandardCharsets.UTF_8));

        VerifiedApiContract verified = new VerifiedApiContract(
                identity,
                contract,
                URI.create("file:///probe"),
                "nekojs/api-contract/portable",
                hash,
                hash);

        return VerifiedContractSet.of(verified);
    }

    private static String sha256Hex(byte[] data) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(data);
            StringBuilder sb = new StringBuilder("sha256:");
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }
}
