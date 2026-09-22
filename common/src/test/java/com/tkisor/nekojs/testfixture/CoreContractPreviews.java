package com.tkisor.nekojs.testfixture;

import com.tkisor.nekojs.NekoJS;
import com.tkisor.nekojs.api.contract.ApiContractIdentity;
import com.tkisor.nekojs.api.contract.ApiContractKind;
import com.tkisor.nekojs.api.contract.NormativeApiContract;
import com.tkisor.nekojs.api.contract.VerifiedApiContract;
import com.tkisor.nekojs.api.contract.VerifiedContractSet;
import com.tkisor.nekojs.api.surface.ApiVersion;

import java.util.List;

/**
 * Minimal {@code nekojs-core} PORTABLE preview contract accepted by
 * {@code NekoPluginRuntime.bootstrapOwned(.., contracts)} in tests: the frozen
 * registry set requires exactly one such contract even when no managed
 * contributions are exercised (same shape as {@code ApiSurfaceBootstrapTest}'s
 * empty preview).
 */
public final class CoreContractPreviews {

    private CoreContractPreviews() {
    }

    public static VerifiedContractSet emptyPortablePreview() throws Exception {
        java.net.URI codeSource = NekoJS.class.getProtectionDomain().getCodeSource().getLocation().toURI();
        ApiContractIdentity identity = new ApiContractIdentity(
                "nekojs-core", ApiContractKind.PORTABLE, "portable-core", ApiVersion.parse("0.0.0"));
        NormativeApiContract contract = new NormativeApiContract(
                2,
                new NormativeApiContract.ContractIdentity(
                        "nekojs-core", ApiContractKind.PORTABLE, "portable-core", ApiVersion.parse("0.0.0")),
                null, List.of(), List.of(), List.of());
        return VerifiedContractSet.of(VerifiedApiContract.create(identity, contract, codeSource,
                "nekojs/api-contract/preview", "sha256:preview", "sha256:preview"));
    }
}
