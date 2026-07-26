package com.tkisor.nekojs.core.api;

import com.tkisor.nekojs.api.contract.ApiContractIdentity;
import com.tkisor.nekojs.api.contract.ApiContractKind;
import com.tkisor.nekojs.api.contract.NormativeApiContract;
import com.tkisor.nekojs.api.contract.VerifiedApiContract;
import com.tkisor.nekojs.api.contract.VerifiedContractSet;
import com.tkisor.nekojs.api.plugin.PluginIdentity;
import com.tkisor.nekojs.api.surface.ApiContribution;
import com.tkisor.nekojs.api.surface.ApiContributionRegistry;
import com.tkisor.nekojs.api.surface.ApiParameter;
import com.tkisor.nekojs.api.surface.ApiResolutionException;
import com.tkisor.nekojs.api.surface.ApiSignature;
import com.tkisor.nekojs.api.surface.ApiSymbol;
import com.tkisor.nekojs.api.surface.ApiSymbolId;
import com.tkisor.nekojs.api.surface.ApiTier;
import com.tkisor.nekojs.api.surface.ApiTypeRef;
import com.tkisor.nekojs.api.surface.ApiVersion;
import com.tkisor.nekojs.api.surface.EnvironmentKey;
import com.tkisor.nekojs.api.surface.LegacyGlobalReservation;
import com.tkisor.nekojs.api.surface.LoaderVersion;
import com.tkisor.nekojs.api.surface.RuntimeDist;
import com.tkisor.nekojs.api.surface.ScriptTypeId;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class JsApiSurfaceResolverTest {

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

    private static EnvironmentKey versionEnv() {
        return new EnvironmentKey(
                ScriptTypeId.SERVER,
                RuntimeDist.DEDICATED_SERVER,
                "neoforge",
                "21.1.0",
                LoaderVersion.parse("21.1.0"),
                "1.21.1",
                Map.of());
    }

    private static ApiSymbolId finderFindId() {
        return ApiSymbolId.parse("member:type:Finder.find");
    }

    private static ApiSignature stringSignature() {
        return ApiSignature.function(
                List.of(new ApiParameter("query", ApiTypeRef.primitive("string"), false, false)),
                ApiTypeRef.primitive("string"));
    }

    private static ApiSignature numberSignature() {
        return ApiSignature.function(
                List.of(new ApiParameter("id", ApiTypeRef.primitive("number"), false, false)),
                ApiTypeRef.primitive("string"));
    }

    private static VerifiedContractSet contractWithFindOverloads() {
        ApiSymbolId finderFindId = finderFindId();
        ApiSymbol symbol = new ApiSymbol(finderFindId, List.of(stringSignature(), numberSignature()));

        NormativeApiContract contract = new NormativeApiContract(
                1,
                new NormativeApiContract.ContractIdentity(
                        "test-owner", ApiContractKind.PORTABLE, "test-contract", ApiVersion.parse("1.0.0")),
                "Test contract",
                List.of(symbol),
                List.of(),
                List.of());

        return VerifiedContractSet.of(
                new VerifiedApiContract(
                        new ApiContractIdentity("test-owner", ApiContractKind.PORTABLE, "test-contract",
                                ApiVersion.parse("1.0.0")),
                        contract,
                        URI.create("file:///test"),
                        "test-contract.json",
                        "sha256:integrity",
                        "sha256:compatibility"));
    }

    private static VerifiedContractSet platformContract() {
        ApiSymbolId symbolId = ApiSymbolId.parse("global:PlatformAPI");
        ApiSymbol symbol = new ApiSymbol(symbolId, List.of(
                ApiSignature.function(List.of(), ApiTypeRef.voidType())));

        NormativeApiContract contract = new NormativeApiContract(
                1,
                new NormativeApiContract.ContractIdentity(
                        "test-owner", ApiContractKind.PORTABLE, "platform-contract", ApiVersion.parse("1.0.0")),
                "Platform contract",
                List.of(symbol),
                List.of(),
                List.of());

        return VerifiedContractSet.of(
                new VerifiedApiContract(
                        new ApiContractIdentity("test-owner", ApiContractKind.PORTABLE, "platform-contract",
                                ApiVersion.parse("1.0.0")),
                        contract,
                        URI.create("file:///test"),
                        "platform-contract.json",
                        "sha256:integrity",
                        "sha256:compatibility"));
    }

    private static ApiContribution contribution(String symbolId, ApiSignature signature) {
        return ApiContribution.symbol(
                ApiSymbolId.parse(symbolId),
                ApiTier.FEATURE,
                "testName",
                Set.of(ScriptTypeId.SERVER),
                List.of(signature),
                (ctx, recv, args) -> null);
    }

    private static ApiContribution nativeReturnContribution(ApiTier tier) {
        return ApiContribution.withNativeReturn(
                ApiSymbolId.parse("global:PlatformAPI"),
                tier,
                "PlatformAPI",
                Set.of(ScriptTypeId.SERVER),
                List.of(ApiSignature.function(List.of(), ApiTypeRef.voidType())),
                (ctx, recv, args) -> null);
    }

    private static FrozenApiRegistry resolve(
            VerifiedContractSet contracts,
            ApiContribution... contributions) {

        PluginIdentity owner = new PluginIdentity("test-owner", "test-plugin");
        ApiContributionRegistry registry = ApiContributionRegistry.ownedBy(owner, contracts);
        for (ApiContribution contrib : contributions) {
            registry.registerSymbol(contrib);
        }

        return JsApiSurfaceResolver.resolve(
                serverEnv(),
                contracts,
                List.of(registry),
                List.of());
    }

    @Test
    void keepsDistinctOverloadsUnderOneSymbol() {
        FrozenApiRegistry registry = resolve(
                contractWithFindOverloads(),
                contribution("member:type:Finder.find", stringSignature()),
                contribution("member:type:Finder.find", numberSignature()));

        ApiSymbol symbol = registry.require(finderFindId());
        assertEquals(2, symbol.signatures().size());
    }

    @Test
    void rejectsRawReturnOutsideVersionTier() {
        ApiResolutionException error = assertThrows(ApiResolutionException.class,
                () -> resolve(platformContract(), nativeReturnContribution(ApiTier.PLATFORM)));
        assertEquals("NATIVE_TYPE_LEAK", error.code());
    }

    @Test
    void rejectsDuplicateCallKey() {
        ApiResolutionException error = assertThrows(ApiResolutionException.class,
                () -> resolve(
                        contractWithFindOverloads(),
                        contribution("member:type:Finder.find", stringSignature()),
                        contribution("member:type:Finder.find", stringSignature())));
        assertEquals("DUPLICATE_CALL_KEY", error.code());
    }

    @Test
    void rejectsContributionWithoutMatchingContract() {
        ApiResolutionException error = assertThrows(ApiResolutionException.class,
                () -> resolve(
                        contractWithFindOverloads(),
                        contribution("member:type:Unknown.method", stringSignature())));
        assertEquals("CONTRIBUTION_NO_CONTRACT", error.code());
    }

    @Test
    void rejectsLegacyNameCollision() {
        ApiSymbolId finderId = ApiSymbolId.parse("global:Finder");
        ApiSymbol symbol = new ApiSymbol(finderId, List.of(
                ApiSignature.function(List.of(), ApiTypeRef.voidType())));

        NormativeApiContract contract = new NormativeApiContract(
                1,
                new NormativeApiContract.ContractIdentity(
                        "test-owner", ApiContractKind.PORTABLE, "finder-contract", ApiVersion.parse("1.0.0")),
                "Finder contract",
                List.of(symbol),
                List.of(),
                List.of());

        VerifiedContractSet contracts = VerifiedContractSet.of(
                new VerifiedApiContract(
                        new ApiContractIdentity("test-owner", ApiContractKind.PORTABLE, "finder-contract",
                                ApiVersion.parse("1.0.0")),
                        contract,
                        URI.create("file:///test"),
                        "finder-contract.json",
                        "sha256:integrity",
                        "sha256:compatibility"));

        LegacyGlobalReservation reservation = new LegacyGlobalReservation(
                "Finder", ApiSymbolId.parse("global:LegacyFinder"));

        PluginIdentity owner = new PluginIdentity("test-owner", "test-plugin");
        ApiContributionRegistry registry = ApiContributionRegistry.ownedBy(owner, contracts);
        registry.registerSymbol(ApiContribution.symbol(
                finderId,
                ApiTier.GLOBAL,
                "Finder",
                Set.of(ScriptTypeId.SERVER),
                List.of(ApiSignature.function(List.of(), ApiTypeRef.voidType())),
                (ctx, recv, args) -> null));

        ApiResolutionException error = assertThrows(ApiResolutionException.class,
                () -> JsApiSurfaceResolver.resolve(
                        serverEnv(),
                        contracts,
                        List.of(registry),
                        List.of(reservation)));
        assertEquals("LEGACY_NAME_COLLISION", error.code());
    }

    @Test
    void allowsVersionTierNativeReturn() {
        ApiSymbolId symbolId = ApiSymbolId.parse("global:VersionAPI");
        ApiSymbol symbol = new ApiSymbol(symbolId, List.of(
                ApiSignature.function(List.of(), ApiTypeRef.voidType())));

        NormativeApiContract contract = new NormativeApiContract(
                1,
                new NormativeApiContract.ContractIdentity(
                        "test-owner", ApiContractKind.PORTABLE, "version-contract", ApiVersion.parse("1.0.0")),
                "Version contract",
                List.of(symbol),
                List.of(),
                List.of());

        VerifiedContractSet contracts = VerifiedContractSet.of(
                new VerifiedApiContract(
                        new ApiContractIdentity("test-owner", ApiContractKind.PORTABLE, "version-contract",
                                ApiVersion.parse("1.0.0")),
                        contract,
                        URI.create("file:///test"),
                        "version-contract.json",
                        "sha256:integrity",
                        "sha256:compatibility"));

        FrozenApiRegistry registry = resolve(contracts,
                ApiContribution.withNativeReturn(
                        symbolId,
                        ApiTier.VERSION,
                        "VersionAPI",
                        Set.of(ScriptTypeId.SERVER),
                        List.of(ApiSignature.function(List.of(), ApiTypeRef.voidType())),
                        (ctx, recv, args) -> null));

        assertNotNull(registry);
        assertTrue(registry.find(symbolId).isPresent());
    }

    @Test
    void resolvesGlobalsForMatchingScriptType() {
        ApiSymbolId symbolId = ApiSymbolId.parse("global:TestGlobal");
        ApiSymbol symbol = new ApiSymbol(symbolId, List.of(
                ApiSignature.function(List.of(), ApiTypeRef.voidType())));

        NormativeApiContract contract = new NormativeApiContract(
                1,
                new NormativeApiContract.ContractIdentity(
                        "test-owner", ApiContractKind.PORTABLE, "global-contract", ApiVersion.parse("1.0.0")),
                "Global contract",
                List.of(symbol),
                List.of(),
                List.of());

        VerifiedContractSet contracts = VerifiedContractSet.of(
                new VerifiedApiContract(
                        new ApiContractIdentity("test-owner", ApiContractKind.PORTABLE, "global-contract",
                                ApiVersion.parse("1.0.0")),
                        contract,
                        URI.create("file:///test"),
                        "global-contract.json",
                        "sha256:integrity",
                        "sha256:compatibility"));

        FrozenApiRegistry registry = resolve(contracts,
                ApiContribution.symbol(
                        symbolId,
                        ApiTier.GLOBAL,
                        "TestGlobal",
                        Set.of(ScriptTypeId.SERVER),
                        List.of(ApiSignature.function(List.of(), ApiTypeRef.voidType())),
                        (ctx, recv, args) -> null));

        Map<String, ApiSymbol> globals = registry.globalsByScriptTypeId(ScriptTypeId.SERVER);
        assertEquals(1, globals.size());
        assertTrue(globals.containsKey("TestGlobal"));
    }

    @Test
    void environmentSnapshotContainsContractHashes() {
        FrozenApiRegistry registry = resolve(contractWithFindOverloads(),
                contribution("member:type:Finder.find", stringSignature()));

        assertNotNull(registry.environmentSnapshot());
        assertNotNull(registry.environmentSnapshot().contractHashes());
        assertEquals("1.0.0", registry.environmentSnapshot().contractHashes().portableApiVersion());
        assertEquals("sha256:compatibility",
                registry.environmentSnapshot().contractHashes().portableContractHash());
    }

    @Test
    void emptyContributionsReturnsEmptyRegistry() {
        FrozenApiRegistry registry = resolve(contractWithFindOverloads());

        assertNotNull(registry);
        assertTrue(registry.find(finderFindId()).isPresent());
    }
}
