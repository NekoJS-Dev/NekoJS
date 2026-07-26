package com.tkisor.nekojs.api.manifest;

import com.tkisor.nekojs.api.surface.ApiVersion;
import com.tkisor.nekojs.api.surface.ScriptTypeId;

import java.util.Map;
import java.util.Objects;

public record ApiManifestBundle(
        int catalogSchemaVersion,
        String nekojsVersion,
        ApiVersion apiVersion,
        ApiVersion spiVersion,
        ApiVersion runtimeContractVersion,
        String portableContractHash,
        Map<String, String> moduleContractHashes,
        String portableSurfaceHash,
        Map<String, ApiEnvironmentManifest> environments,
        String canonicalJson
) {
    public ApiManifestBundle {
        Objects.requireNonNull(nekojsVersion, "nekojsVersion");
        Objects.requireNonNull(apiVersion, "apiVersion");
        Objects.requireNonNull(spiVersion, "spiVersion");
        Objects.requireNonNull(runtimeContractVersion, "runtimeContractVersion");
        Objects.requireNonNull(portableContractHash, "portableContractHash");
        moduleContractHashes = Map.copyOf(moduleContractHashes == null ? Map.of() : moduleContractHashes);
        Objects.requireNonNull(portableSurfaceHash, "portableSurfaceHash");
        environments = Map.copyOf(environments == null ? Map.of() : environments);
        Objects.requireNonNull(canonicalJson, "canonicalJson");
        if (catalogSchemaVersion < 1) {
            throw new IllegalArgumentException("catalogSchemaVersion must be positive");
        }
    }

    public ApiEnvironmentManifest environmentManifest(String scriptTypeId) {
        return environments.get(scriptTypeId);
    }

    public String environmentSurfaceHash(String scriptTypeId) {
        ApiEnvironmentManifest manifest = environments.get(scriptTypeId);
        return manifest == null ? null : manifest.environmentSurfaceHash();
    }
}
