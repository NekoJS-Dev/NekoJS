package com.tkisor.nekojs.probe;

import com.tkisor.nekojs.api.ScriptType;
import com.tkisor.nekojs.api.contract.ApiContractIdentity;
import com.tkisor.nekojs.api.contract.ApiContractKind;
import com.tkisor.nekojs.api.contract.NormativeApiContract;
import com.tkisor.nekojs.api.contract.VerifiedApiContract;
import com.tkisor.nekojs.api.contract.VerifiedContractSet;
import com.tkisor.nekojs.api.surface.*;
import com.tkisor.nekojs.testfixture.TestPlatformInit;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class ApiManifestGeneratorTest {

    @BeforeAll
    static void initPlatform() {
        TestPlatformInit.ensureInitialized();
    }

    @TempDir
    Path tempDir;

    private static ApiVersion v0_0_0() {
        return ApiVersion.parse("0.0.0");
    }

    private static EnvironmentKey serverEnv() {
        return new EnvironmentKey(
                ScriptTypeId.SERVER,
                RuntimeDist.DEDICATED_SERVER,
                "neoforge",
                "21.1.0",
                LoaderVersion.parse("21.1.0"),
                "1.21.1",
                Map.of());
    }

    private static String sha256(String prefix) {
        return "sha256:" + prefix + "0".repeat(64 - prefix.length());
    }

    private static ApiContractHashes contractHashes() {
        return new ApiContractHashes("0.0.0", sha256("bbbb"), Map.of());
    }

    private static VerifiedContractSet portableContractSet() {
        ApiContractIdentity identity = new ApiContractIdentity(
                "nekojs", ApiContractKind.PORTABLE, "nekojs-portable", v0_0_0());

        NormativeApiContract contract = new NormativeApiContract(
                1,
                new NormativeApiContract.ContractIdentity(
                        "nekojs", ApiContractKind.PORTABLE, "nekojs-portable", v0_0_0()),
                "Portable contract",
                List.of(),
                List.of(),
                List.of());

        String hash = sha256("bbbb");
        return VerifiedContractSet.of(
                VerifiedApiContract.create(identity, contract, URI.create("file:///test"),
                        "nekojs-portable.json", hash, hash));
    }

    private static ApiRuntimeVersions defaultVersions() {
        return new ApiRuntimeVersions(
                "1.1.0-preview1",
                v0_0_0(),
                ApiVersion.parse("0.0.0"),
                ApiVersion.parse("0.0.0"),
                1);
    }

    private static ApiSurfaceSnapshot emptySurface() {
        return new ApiSurfaceSnapshot(
                List.of(),
                Set.of(),
                List.of(),
                List.of(),
                serverEnv());
    }

    @Test
    void writesManifestToStagingDirectory() throws Exception {
        ApiEnvironmentSnapshot env = new ApiEnvironmentSnapshot(serverEnv(), emptySurface(), contractHashes());
        Map<ScriptType, ApiEnvironmentSnapshot> managedApis = Map.of(ScriptType.SERVER, env);

        ApiManifestGenerator generator = new ApiManifestGenerator(defaultVersions(), portableContractSet());
        Path manifestPath = generator.write(tempDir, managedApis);

        assertTrue(Files.exists(manifestPath), "Manifest file should exist");
        assertTrue(manifestPath.getFileName().toString().equals("api-manifest.json"));
    }

    @Test
    void manifestIsUtf8() throws Exception {
        ApiEnvironmentSnapshot env = new ApiEnvironmentSnapshot(serverEnv(), emptySurface(), contractHashes());
        Map<ScriptType, ApiEnvironmentSnapshot> managedApis = Map.of(ScriptType.SERVER, env);

        ApiManifestGenerator generator = new ApiManifestGenerator(defaultVersions(), portableContractSet());
        Path manifestPath = generator.write(tempDir, managedApis);

        byte[] bytes = Files.readAllBytes(manifestPath);
        String content = new String(bytes, StandardCharsets.UTF_8);
        assertFalse(content.isEmpty(), "Manifest should not be empty");
        assertTrue(content.contains("nekojsVersion"), "Manifest should contain version info");
    }

    @Test
    void repeatedGenerationIsByteIdentical() throws Exception {
        ApiSymbol sym = new ApiSymbol(
                ApiSymbolId.parse("global:TestAPI"),
                List.of(ApiSignature.function(List.of(), ApiTypeRef.voidType())));
        ApiSurfaceSnapshot surface = new ApiSurfaceSnapshot(
                List.of(sym), Set.of(), List.of(), List.of(), serverEnv());
        ApiEnvironmentSnapshot env = new ApiEnvironmentSnapshot(serverEnv(), surface, contractHashes());
        Map<ScriptType, ApiEnvironmentSnapshot> managedApis = Map.of(ScriptType.SERVER, env);

        ApiManifestGenerator generator = new ApiManifestGenerator(defaultVersions(), portableContractSet());

        Path dir1 = tempDir.resolve("run1");
        Path dir2 = tempDir.resolve("run2");
        Files.createDirectories(dir1);
        Files.createDirectories(dir2);

        Path path1 = generator.write(dir1, managedApis);
        Path path2 = generator.write(dir2, managedApis);

        byte[] bytes1 = Files.readAllBytes(path1);
        byte[] bytes2 = Files.readAllBytes(path2);

        assertArrayEquals(bytes1, bytes2, "Repeated generation should produce byte-identical output");
    }

    @Test
    void manifestContainsEnvironmentsMap() throws Exception {
        ApiEnvironmentSnapshot env = new ApiEnvironmentSnapshot(serverEnv(), emptySurface(), contractHashes());
        Map<ScriptType, ApiEnvironmentSnapshot> managedApis = Map.of(ScriptType.SERVER, env);

        ApiManifestGenerator generator = new ApiManifestGenerator(defaultVersions(), portableContractSet());
        Path manifestPath = generator.write(tempDir, managedApis);

        String content = Files.readString(manifestPath, StandardCharsets.UTF_8);
        assertTrue(content.contains("SERVER"), "Manifest should contain SERVER environment");
    }
}
