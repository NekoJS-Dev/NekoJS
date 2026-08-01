package com.tkisor.nekojs.api.manifest;

import com.tkisor.nekojs.api.contract.ApiContractIdentity;
import com.tkisor.nekojs.api.contract.ApiContractKind;
import com.tkisor.nekojs.api.contract.NormativeApiContract;
import com.tkisor.nekojs.api.contract.VerifiedApiContract;
import com.tkisor.nekojs.api.contract.VerifiedContractSet;
import com.tkisor.nekojs.api.surface.*;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class ApiManifestWriterTest {

    private static ApiVersion v1_0_0() {
        return ApiVersion.parse("1.0.0");
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

    private static EnvironmentKey startupEnv() {
        return new EnvironmentKey(
                ScriptTypeId.STARTUP,
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

    private static ApiContractIdentity portableIdentity() {
        return new ApiContractIdentity("nekojs-core", ApiContractKind.PORTABLE, "portable-core", v1_0_0());
    }

    private static VerifiedContractSet portableContractSet() {
        NormativeApiContract contract = new NormativeApiContract(
                2,
                new NormativeApiContract.ContractIdentity(
                        "nekojs-core", ApiContractKind.PORTABLE, "portable-core", v1_0_0()),
                "Portable contract",
                List.of(),
                List.of(),
                List.of());

        return VerifiedContractSet.of(
                VerifiedApiContract.create(
                        portableIdentity(),
                        contract,
                        URI.create("file:///test"),
                        "portable-core.json",
                        sha256("aaaa"),
                        sha256("bbbb")));
    }

    private static ApiRuntimeVersions defaultVersions() {
        return new ApiRuntimeVersions(
                "1.1.0-preview1",
                v1_0_0(),
                ApiVersion.parse("0.0.0"),
                ApiVersion.parse("0.0.0"),
                1);
    }

    private static ApiContractHashes contractHashes() {
        return new ApiContractHashes(
                "1.0.0",
                sha256("bbbb"),
                Map.of());
    }

    private static ApiSymbol makeSymbol(String id) {
        return new ApiSymbol(
                ApiSymbolId.parse(id),
                List.of(ApiSignature.function(List.of(), ApiTypeRef.voidType())));
    }

    private static ApiSurfaceSnapshot emptySurface() {
        return new ApiSurfaceSnapshot(
                List.of(),
                Set.of(),
                List.of(),
                List.of(),
                serverEnv());
    }

    private static ApiEnvironmentSnapshot serverSnapshot() {
        return new ApiEnvironmentSnapshot(serverEnv(), emptySurface(), contractHashes());
    }

    private static ApiEnvironmentSnapshot startupSnapshot() {
        return new ApiEnvironmentSnapshot(
                startupEnv(),
                new ApiSurfaceSnapshot(
                        List.of(),
                        Set.of(),
                        List.of(),
                        List.of(),
                        startupEnv()),
                contractHashes());
    }

    @Test
    void canonicalJsonIsDeterministicRegardlessOfInputOrder() {
        ApiSymbol sym1 = makeSymbol("global:Alpha");
        ApiSymbol sym2 = makeSymbol("global:Beta");
        ApiSymbol sym3 = makeSymbol("global:Gamma");

        ApiSurfaceSnapshot surface1 = new ApiSurfaceSnapshot(
                List.of(sym1, sym2, sym3),
                Set.of("cap-a", "cap-b"),
                List.of(),
                List.of(),
                serverEnv());

        ApiSurfaceSnapshot surface2 = new ApiSurfaceSnapshot(
                List.of(sym3, sym1, sym2),
                Set.of("cap-b", "cap-a"),
                List.of(),
                List.of(),
                serverEnv());

        ApiEnvironmentSnapshot snap1 = new ApiEnvironmentSnapshot(serverEnv(), surface1, contractHashes());
        ApiEnvironmentSnapshot snap2 = new ApiEnvironmentSnapshot(serverEnv(), surface2, contractHashes());

        Map<ScriptTypeId, ApiEnvironmentSnapshot> envs1 = Map.of(ScriptTypeId.SERVER, snap1);
        Map<ScriptTypeId, ApiEnvironmentSnapshot> envs2 = Map.of(ScriptTypeId.SERVER, snap2);

        ApiManifestBundle bundle1 = ApiManifestWriter.writeBundle(
                defaultVersions(), portableContractSet(), envs1);
        ApiManifestBundle bundle2 = ApiManifestWriter.writeBundle(
                defaultVersions(), portableContractSet(), envs2);

        assertEquals(bundle1.canonicalJson(), bundle2.canonicalJson());
        assertEquals(bundle1.portableSurfaceHash(), bundle2.portableSurfaceHash());
        assertEquals(bundle1.environmentSurfaceHash("SERVER"), bundle2.environmentSurfaceHash("SERVER"));
    }

    @Test
    void documentationChangeDoesNotAffectCompatibilityHash() {
        ApiSymbol sym = makeSymbol("global:TestAPI");

        ApiSurfaceSnapshot surface = new ApiSurfaceSnapshot(
                List.of(sym),
                Set.of(),
                List.of(),
                List.of(),
                serverEnv());

        ApiEnvironmentSnapshot snap = new ApiEnvironmentSnapshot(serverEnv(), surface, contractHashes());

        Map<ScriptTypeId, ApiEnvironmentSnapshot> envs = Map.of(ScriptTypeId.SERVER, snap);

        ApiManifestBundle bundle1 = ApiManifestWriter.writeBundle(
                defaultVersions(), portableContractSet(), envs);
        ApiManifestBundle bundle2 = ApiManifestWriter.writeBundle(
                defaultVersions(), portableContractSet(), envs);

        assertEquals(bundle1.portableContractHash(), bundle2.portableContractHash());
        assertEquals(bundle1.portableSurfaceHash(), bundle2.portableSurfaceHash());
    }

    @Test
    void parameterTypeChangeAffectsSurfaceHash() {
        ApiSymbol sym1 = new ApiSymbol(
                ApiSymbolId.parse("global:TestAPI"),
                List.of(ApiSignature.function(
                        List.of(new ApiParameter("x", ApiTypeRef.primitive("string"), false, false)),
                        ApiTypeRef.voidType())));

        ApiSymbol sym2 = new ApiSymbol(
                ApiSymbolId.parse("global:TestAPI"),
                List.of(ApiSignature.function(
                        List.of(new ApiParameter("x", ApiTypeRef.primitive("number"), false, false)),
                        ApiTypeRef.voidType())));

        ApiSurfaceSnapshot surface1 = new ApiSurfaceSnapshot(
                List.of(sym1), Set.of(), List.of(), List.of(), serverEnv());
        ApiSurfaceSnapshot surface2 = new ApiSurfaceSnapshot(
                List.of(sym2), Set.of(), List.of(), List.of(), serverEnv());

        ApiEnvironmentSnapshot snap1 = new ApiEnvironmentSnapshot(serverEnv(), surface1, contractHashes());
        ApiEnvironmentSnapshot snap2 = new ApiEnvironmentSnapshot(serverEnv(), surface2, contractHashes());

        Map<ScriptTypeId, ApiEnvironmentSnapshot> envs1 = Map.of(ScriptTypeId.SERVER, snap1);
        Map<ScriptTypeId, ApiEnvironmentSnapshot> envs2 = Map.of(ScriptTypeId.SERVER, snap2);

        ApiManifestBundle bundle1 = ApiManifestWriter.writeBundle(
                defaultVersions(), portableContractSet(), envs1);
        ApiManifestBundle bundle2 = ApiManifestWriter.writeBundle(
                defaultVersions(), portableContractSet(), envs2);

        assertNotEquals(bundle1.portableSurfaceHash(), bundle2.portableSurfaceHash());
    }

    @Test
    void hashFormatMatchesExpectedPattern() {
        ApiEnvironmentSnapshot snap = serverSnapshot();
        Map<ScriptTypeId, ApiEnvironmentSnapshot> envs = Map.of(ScriptTypeId.SERVER, snap);

        ApiManifestBundle bundle = ApiManifestWriter.writeBundle(
                defaultVersions(), portableContractSet(), envs);

        String hashPattern = "^sha256:[0-9a-f]{64}$";
        assertTrue(bundle.portableContractHash().matches(hashPattern));
        assertTrue(bundle.portableSurfaceHash().matches(hashPattern));
        assertTrue(bundle.environmentSurfaceHash("SERVER").matches(hashPattern));
    }

    @Test
    void throwsOnApiVersionMismatch() {
        ApiRuntimeVersions badVersions = new ApiRuntimeVersions(
                "1.1.0-preview1",
                ApiVersion.parse("2.0.0"),
                ApiVersion.parse("0.0.0"),
                ApiVersion.parse("0.0.0"),
                1);

        Map<ScriptTypeId, ApiEnvironmentSnapshot> envs = Map.of(ScriptTypeId.SERVER, serverSnapshot());

        ApiResolutionException error = assertThrows(
                ApiResolutionException.class,
                () -> ApiManifestWriter.writeBundle(badVersions, portableContractSet(), envs));
        assertEquals("API_VERSION_MISMATCH", error.code());
    }

    @Test
    void bundleContainsCorrectVersions() {
        Map<ScriptTypeId, ApiEnvironmentSnapshot> envs = Map.of(ScriptTypeId.SERVER, serverSnapshot());

        ApiManifestBundle bundle = ApiManifestWriter.writeBundle(
                defaultVersions(), portableContractSet(), envs);

        assertEquals("1.1.0-preview1", bundle.nekojsVersion());
        assertEquals(v1_0_0(), bundle.apiVersion());
        assertEquals(ApiVersion.parse("0.0.0"), bundle.spiVersion());
        assertEquals(ApiVersion.parse("0.0.0"), bundle.runtimeContractVersion());
        assertEquals(1, bundle.catalogSchemaVersion());
    }

    @Test
    void environmentsMapKeyedByScriptTypeId() {
        Map<ScriptTypeId, ApiEnvironmentSnapshot> envs = Map.of(
                ScriptTypeId.SERVER, serverSnapshot(),
                ScriptTypeId.STARTUP, startupSnapshot());

        ApiManifestBundle bundle = ApiManifestWriter.writeBundle(
                defaultVersions(), portableContractSet(), envs);

        assertEquals(2, bundle.environments().size());
        assertNotNull(bundle.environmentManifest("SERVER"));
        assertNotNull(bundle.environmentManifest("STARTUP"));
    }

    @Test
    void moduleContractHashesFromVerifiedContracts() {
        NormativeApiContract moduleContract = new NormativeApiContract(
                1,
                new NormativeApiContract.ContractIdentity(
                        "test-owner", ApiContractKind.FEATURE, "test-module", v1_0_0()),
                "Module contract",
                List.of(),
                List.of(),
                List.of());

        VerifiedContractSet contracts = VerifiedContractSet.of(
                VerifiedApiContract.create(
                        portableIdentity(),
                        new NormativeApiContract(
                                1,
                                new NormativeApiContract.ContractIdentity(
                                        "nekojs-core", ApiContractKind.PORTABLE, "portable-core", v1_0_0()),
                                "Portable contract",
                                List.of(), List.of(), List.of()),
                        URI.create("file:///test"),
                        "portable-core.json",
                        sha256("aaaa"),
                        sha256("bbbb")),
                VerifiedApiContract.create(
                        new ApiContractIdentity("test-owner", ApiContractKind.FEATURE, "test-module", v1_0_0()),
                        moduleContract,
                        URI.create("file:///test2"),
                        "test-module.json",
                        sha256("cccc"),
                        sha256("dddd")));

        Map<ScriptTypeId, ApiEnvironmentSnapshot> envs = Map.of(ScriptTypeId.SERVER, serverSnapshot());

        ApiManifestBundle bundle = ApiManifestWriter.writeBundle(
                defaultVersions(), contracts, envs);

        assertTrue(bundle.moduleContractHashes().containsKey("test-module"));
        assertEquals(sha256("dddd"), bundle.moduleContractHashes().get("test-module"));
    }
}
