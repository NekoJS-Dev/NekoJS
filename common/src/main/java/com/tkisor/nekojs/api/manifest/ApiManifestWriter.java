package com.tkisor.nekojs.api.manifest;

import com.tkisor.nekojs.api.contract.VerifiedApiContract;
import com.tkisor.nekojs.api.contract.VerifiedContractSet;
import com.tkisor.nekojs.api.surface.*;

import java.util.*;

public final class ApiManifestWriter {

    private ApiManifestWriter() {}

    public static ApiManifestBundle writeBundle(
            ApiRuntimeVersions versions,
            VerifiedContractSet contracts,
            Map<ScriptTypeId, ApiEnvironmentSnapshot> environments) {

        Objects.requireNonNull(versions, "versions");
        Objects.requireNonNull(contracts, "contracts");
        Objects.requireNonNull(environments, "environments");

        VerifiedApiContract portable = contracts.requirePortable("nekojs");
        if (!versions.apiVersion().equals(portable.identity().version())) {
            throw new ApiResolutionException(
                    "API_VERSION_MISMATCH",
                    "versions.apiVersion (" + versions.apiVersion()
                            + ") does not match portable contract version ("
                            + portable.identity().version() + ")",
                    Map.of(
                            "expected", portable.identity().version().toString(),
                            "actual", versions.apiVersion().toString()));
        }

        String portableContractHash = portable.compatibilitySha256();

        Map<String, String> moduleContractHashes = new LinkedHashMap<>();
        for (VerifiedApiContract c : contracts.all()) {
            if (c.identity().kind() != com.tkisor.nekojs.api.contract.ApiContractKind.PORTABLE) {
                moduleContractHashes.put(c.identity().contractId(), c.compatibilitySha256());
            }
        }

        Map<String, String> envSurfaceHashes = new LinkedHashMap<>();
        String portableSurfaceHash = null;

        for (Map.Entry<ScriptTypeId, ApiEnvironmentSnapshot> entry : environments.entrySet()) {
            ApiSurfaceSnapshot snapshot = entry.getValue().surfaceSnapshot();
            String surfaceJson = CanonicalJson.serializeSurfaceForHash(snapshot);
            String hash = CanonicalJson.sha256hex(surfaceJson);
            envSurfaceHashes.put(entry.getKey().name(), hash);

            if (portableSurfaceHash == null) {
                portableSurfaceHash = hash;
            }
        }

        if (portableSurfaceHash == null) {
            portableSurfaceHash = CanonicalJson.sha256hex("");
        }

        Map<String, ApiEnvironmentManifest> manifests = new LinkedHashMap<>();
        for (Map.Entry<ScriptTypeId, ApiEnvironmentSnapshot> entry : environments.entrySet()) {
            ApiSurfaceSnapshot snapshot = entry.getValue().surfaceSnapshot();
            String envHash = envSurfaceHashes.get(entry.getKey().name());
            manifests.put(
                    entry.getKey().name(),
                    ApiEnvironmentManifest.fromSnapshot(snapshot, portableSurfaceHash, envHash));
        }

        ApiManifestBundle bundle = new ApiManifestBundle(
                versions.catalogSchemaVersion(),
                versions.nekojsVersion(),
                versions.apiVersion(),
                versions.spiVersion(),
                versions.runtimeContractVersion(),
                portableContractHash,
                moduleContractHashes,
                portableSurfaceHash,
                manifests,
                "");

        String canonicalJson = CanonicalJson.serialize(bundle);

        return new ApiManifestBundle(
                versions.catalogSchemaVersion(),
                versions.nekojsVersion(),
                versions.apiVersion(),
                versions.spiVersion(),
                versions.runtimeContractVersion(),
                portableContractHash,
                moduleContractHashes,
                portableSurfaceHash,
                manifests,
                canonicalJson);
    }
}
