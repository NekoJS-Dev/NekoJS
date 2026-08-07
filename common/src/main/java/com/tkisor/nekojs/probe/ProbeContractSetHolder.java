package com.tkisor.nekojs.probe;

import com.tkisor.nekojs.api.contract.VerifiedContractSet;
import com.tkisor.nekojs.core.api.CoreManagedApiBootstrap;

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
        try {
            var codeSource = ProbeContractSetHolder.class.getProtectionDomain().getCodeSource().getLocation().toURI();
            return VerifiedContractSet.of(CoreManagedApiBootstrap.buildContract(codeSource));
        } catch (java.net.URISyntaxException e) {
            throw new IllegalStateException("Failed to resolve probe code source", e);
        }
    }
}
