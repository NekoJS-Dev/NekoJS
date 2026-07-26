package com.tkisor.nekojs.probe;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.tkisor.nekojs.api.ScriptType;
import com.tkisor.nekojs.api.contract.VerifiedContractSet;
import com.tkisor.nekojs.api.manifest.ApiManifestBundle;
import com.tkisor.nekojs.api.manifest.ApiManifestWriter;
import com.tkisor.nekojs.api.surface.ApiEnvironmentSnapshot;
import com.tkisor.nekojs.api.surface.ApiRuntimeVersions;
import com.tkisor.nekojs.api.surface.ScriptTypeId;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Objects;

/**
 * Generates the canonical {@code api-manifest.json} from managed APIs and runtime versions.
 * Uses {@link ApiManifestWriter#writeBundle} to produce deterministic output.
 *
 * <p>The constructor accepts injectable {@link ApiRuntimeVersions} for testing;
 * production code uses {@link ApiManifestGenerator()} which reads from the classpath.
 */
public final class ApiManifestGenerator {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final ApiRuntimeVersions versions;
    private final VerifiedContractSet contracts;

    /**
     * Production constructor: reads versions from classpath.
     * Requires the {@code api-runtime.properties} resource to be present.
     */
    public ApiManifestGenerator(ApiRuntimeVersions versions, VerifiedContractSet contracts) {
        this.versions = Objects.requireNonNull(versions, "versions");
        this.contracts = Objects.requireNonNull(contracts, "contracts");
    }

    /**
     * Write {@code api-manifest.json} to the given directory.
     *
     * @param outputDir   the target directory (staging dir)
     * @param managedApis map from ScriptType to its managed environment snapshot
     * @return the path to the written file
     */
    public Path write(Path outputDir, Map<ScriptType, ApiEnvironmentSnapshot> managedApis) throws IOException {
        Objects.requireNonNull(outputDir, "outputDir");
        Objects.requireNonNull(managedApis, "managedApis");

        Map<ScriptTypeId, ApiEnvironmentSnapshot> envMap = new java.util.LinkedHashMap<>();
        for (Map.Entry<ScriptType, ApiEnvironmentSnapshot> entry : managedApis.entrySet()) {
            envMap.put(ScriptTypeId.fromScriptType(entry.getKey()), entry.getValue());
        }

        ApiManifestBundle bundle = ApiManifestWriter.writeBundle(versions, contracts, envMap);

        Path manifestFile = outputDir.resolve("api-manifest.json");
        Files.createDirectories(outputDir);
        Files.writeString(manifestFile, bundle.canonicalJson(), StandardCharsets.UTF_8);
        return manifestFile;
    }
}
