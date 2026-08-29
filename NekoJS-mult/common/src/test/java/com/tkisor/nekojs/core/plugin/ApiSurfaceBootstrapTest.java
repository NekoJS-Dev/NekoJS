package com.tkisor.nekojs.core.plugin;

import com.tkisor.nekojs.NekoJS;
import com.tkisor.nekojs.api.NekoJSPlugin;
import com.tkisor.nekojs.api.contract.ApiContractIdentity;
import com.tkisor.nekojs.api.contract.ApiContractKind;
import com.tkisor.nekojs.api.contract.NormativeApiContract;
import com.tkisor.nekojs.api.contract.VerifiedApiContract;
import com.tkisor.nekojs.api.contract.VerifiedContractSet;
import com.tkisor.nekojs.api.plugin.OwnedPlugin;
import com.tkisor.nekojs.api.plugin.PluginIdentity;
import com.tkisor.nekojs.api.surface.*;
import com.tkisor.nekojs.core.api.FrozenApiRegistrySet;
import com.tkisor.nekojs.core.api.CoreManagedApiBootstrap;
import com.tkisor.nekojs.probe.ProbeBackendRegistry;
import com.tkisor.nekojs.script.prop.ScriptPropertyRegistry;
import com.tkisor.nekojs.testfixture.TestPlatformInit;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.net.URI;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class ApiSurfaceBootstrapTest {

    private static final URI TEST_CODE_SOURCE = URI.create("file:///test-mod.jar");

    @BeforeAll
    static void initPlatform() {
        TestPlatformInit.ensureInitialized();
    }

    @BeforeEach
    void resetStaticState() throws Exception {
        resetProbeBackendRegistry();
        resetNekoPluginRuntime();
    }

    private static void resetProbeBackendRegistry() throws Exception {
        Field inst = ProbeBackendRegistry.class.getDeclaredField("INSTANCE");
        inst.setAccessible(true);
        inst.set(null, null);
    }

    private static void resetNekoPluginRuntime() throws Exception {
        Field current = com.tkisor.nekojs.core.plugin.NekoPluginRuntime.class.getDeclaredField("current");
        current.setAccessible(true);
        current.set(null, null);

        Field runtime = com.tkisor.nekojs.api.plugin.NekoRuntimeAccess.class.getDeclaredField("runtime");
        runtime.setAccessible(true);
        runtime.set(null, null);
    }

    @Test
    void managedPluginOwnerComesFromIdentity() {
        String owner = "test-mod";
        ApiSymbolId symbolId = new ApiSymbolId("global", "global:testSymbol");
        ApiSignature sig = ApiSignature.function(List.of(), ApiTypeRef.voidType());

        VerifiedApiContract contract = createContractWithSymbol(owner, "test-contract", symbolId, sig);
        VerifiedContractSet contracts = VerifiedContractSet.of(contract, nekojsCorePortableContract());

        PluginIdentity identity = new PluginIdentity(owner, TestManagedPlugin.class.getName(), TEST_CODE_SOURCE);

        TestManagedPlugin plugin = new TestManagedPlugin((registry) -> {
            assertEquals(owner, registry.owner().ownerId(),
                    "Owner in registry must come from PluginIdentity, not caller argument");
            registry.registerSymbol(ApiContribution.symbol(
                    symbolId, ApiTier.GLOBAL, "testSymbol",
                    Set.of(ScriptTypeId.STARTUP), List.of(sig),
                    (ctx, recv, args) -> null));
        });

        OwnedPlugin owned = new OwnedPlugin(identity, plugin);
        ScriptPropertyRegistry scriptProps = new ScriptPropertyRegistry.Impl();

        NekoPluginRuntime runtime = NekoPluginRuntime.bootstrapOwned(
                List.of(owned), scriptProps, contracts);

        assertNotNull(runtime);
    }

    @Test
    void legacyPluginWithoutContractNotInManagedCollection() throws Exception {
        ScriptPropertyRegistry scriptProps = new ScriptPropertyRegistry.Impl();

        VerifiedContractSet emptyContracts = VerifiedContractSet.of(
                emptyPortablePreview(NekoJS.class.getProtectionDomain().getCodeSource().getLocation().toURI()));

        TestLegacyPlugin legacyPlugin = new TestLegacyPlugin();
        OwnedPlugin legacyOwned = new OwnedPlugin(
                new PluginIdentity("legacy:" + TestLegacyPlugin.class.getName(),
                        TestLegacyPlugin.class.getName(), TEST_CODE_SOURCE),
                legacyPlugin);

        NekoPluginRuntime runtime = NekoPluginRuntime.bootstrapOwned(
                List.of(legacyOwned), scriptProps, emptyContracts);

        assertNotNull(runtime);
        ApiRuntimeProvider provider = runtime.apiRuntimeProvider();
        assertNotNull(provider, "Runtime should have an API runtime provider (even if no managed contributions)");
        assertTrue(provider instanceof FrozenApiRegistrySet);
        FrozenApiRegistrySet frozenSet = (FrozenApiRegistrySet) provider;
        assertTrue(frozenSet.contracts().all().size() <= 1,
                "Legacy plugin should not contribute additional contracts");
    }

    @Test
    void ownerMismatchSkipsManagedRegistration() {
        String owner = "test-mod";
        String wrongOwner = "other-mod";
        ApiSymbolId symbolId = new ApiSymbolId("global", "global:testSymbol");
        ApiSignature sig = ApiSignature.function(List.of(), ApiTypeRef.voidType());

        VerifiedApiContract contract = createContractWithSymbol(owner, "test-contract", symbolId, sig);
        VerifiedContractSet contracts = VerifiedContractSet.of(contract, nekojsCorePortableContract());

        PluginIdentity wrongIdentity = new PluginIdentity(wrongOwner,
                TestManagedPlugin.class.getName(), TEST_CODE_SOURCE);

        boolean[] called = {false};
        TestManagedPlugin plugin = new TestManagedPlugin((registry) -> {
            called[0] = true;
        });

        OwnedPlugin owned = new OwnedPlugin(wrongIdentity, plugin);
        ScriptPropertyRegistry scriptProps = new ScriptPropertyRegistry.Impl();

        NekoPluginRuntime runtime = NekoPluginRuntime.bootstrapOwned(
                List.of(owned), scriptProps, contracts);

        assertNotNull(runtime);
        assertFalse(called[0],
                "Plugin with owner mismatch should not have registerApiSurface called");
    }

    @Test
    void contributionNoContractFailsFast() {
        String owner = "test-mod";
        ApiSymbolId contractSymbol = new ApiSymbolId("global", "global:contractSymbol");
        ApiSymbolId unknownSymbol = new ApiSymbolId("global", "global:unknownSymbol");
        ApiSignature sig = ApiSignature.function(List.of(), ApiTypeRef.voidType());

        VerifiedApiContract contract = createContractWithSymbol(owner, "test-contract", contractSymbol, sig);
        VerifiedContractSet contracts = VerifiedContractSet.of(contract, nekojsCorePortableContract());

        PluginIdentity identity = new PluginIdentity(owner, TestManagedPlugin.class.getName(), TEST_CODE_SOURCE);

        TestManagedPlugin plugin = new TestManagedPlugin((registry) -> {
            registry.registerSymbol(ApiContribution.symbol(
                    unknownSymbol, ApiTier.GLOBAL, "unknown",
                    Set.of(ScriptTypeId.STARTUP), List.of(sig),
                    (ctx, recv, args) -> null));
        });

        OwnedPlugin owned = new OwnedPlugin(identity, plugin);
        ScriptPropertyRegistry scriptProps = new ScriptPropertyRegistry.Impl();

        assertThrows(ApiResolutionException.class, () ->
                NekoPluginRuntime.bootstrapOwned(List.of(owned), scriptProps, contracts),
                "Contribution with no matching contract must fail-fast");
    }

    @Test
    void bootstrapLegacyStillWorks() {
        ScriptPropertyRegistry scriptProps = new ScriptPropertyRegistry.Impl();
        TestLegacyPlugin legacyPlugin = new TestLegacyPlugin();

        NekoPluginRuntime runtime = NekoPluginBootstrap.bootstrap(
                List.of(legacyPlugin), scriptProps);

        assertNotNull(runtime, "Legacy bootstrap must continue to work");
    }

    @Test
    void productionBootstrapPublishesPortableCoreInEveryScriptEnvironment() {
        NekoPluginRuntime runtime = NekoPluginRuntime.bootstrapOwned(
                List.of(), new ScriptPropertyRegistry.Impl());

        assertEquals(ApiVersion.parse("0.12.0"),
                ((FrozenApiRegistrySet) runtime.apiRuntimeProvider())
                        .contracts().requirePortable("nekojs-core").identity().version());
        assertNotNull(runtime.managedApiImplementation(CoreManagedApiBootstrap.ID_GLOBAL));
        assertNotNull(runtime.managedApiImplementation(CoreManagedApiBootstrap.PLATFORM_GLOBAL));
        assertNotNull(runtime.managedApiImplementation(CoreManagedApiBootstrap.TEXT_GLOBAL));
        assertNotNull(runtime.managedApiImplementation(CoreManagedApiBootstrap.JSON_IO_GLOBAL));
        assertNotNull(runtime.managedApiImplementation(CoreManagedApiBootstrap.NBT_GLOBAL));

        for (com.tkisor.nekojs.api.ScriptType type : com.tkisor.nekojs.api.ScriptType.values()) {
            ApiRuntimeView view = runtime.apiRuntime(EnvironmentKeyFactory.current(type));
            assertNotNull(view, type.name());
            assertTrue(view.findSymbol(CoreManagedApiBootstrap.ID_GLOBAL).isPresent(), type.name());
            assertTrue(view.findSymbol(CoreManagedApiBootstrap.PLATFORM_GLOBAL).isPresent(), type.name());
            assertTrue(view.findSymbol(CoreManagedApiBootstrap.TEXT_GLOBAL).isPresent(), type.name());
            assertTrue(view.findSymbol(CoreManagedApiBootstrap.JSON_IO_GLOBAL).isPresent(), type.name());
            assertTrue(view.findSymbol(CoreManagedApiBootstrap.NBT_GLOBAL).isPresent(), type.name());
        }
    }

    private VerifiedApiContract createContractWithSymbol(
            String owner, String contractId, ApiSymbolId symbolId, ApiSignature sig) {
        ApiContractIdentity identity = new ApiContractIdentity(
                owner, ApiContractKind.PORTABLE, contractId, ApiVersion.parse("1.0.0"));

        ApiSymbol symbol = new ApiSymbol(symbolId, List.of(sig));

        NormativeApiContract contract = new NormativeApiContract(
                1,
                new NormativeApiContract.ContractIdentity(
                        owner, ApiContractKind.PORTABLE, contractId, ApiVersion.parse("1.0.0")),
                null,
                List.of(symbol),
                List.of(),
                List.of());

        return VerifiedApiContract.create(identity, contract, TEST_CODE_SOURCE,
                "test-contract.json", "sha256:test", "sha256:test");
    }

    /** 合成一个空 portable-core 预览契约（替代已删除的 ApiContractReader.emptyVerifiedCorePreview）。 */
    private static VerifiedApiContract emptyPortablePreview(URI codeSource) {
        ApiContractIdentity identity = new ApiContractIdentity(
                "nekojs-core", ApiContractKind.PORTABLE, "portable-core", ApiVersion.parse("0.0.0"));
        NormativeApiContract contract = new NormativeApiContract(
                2,
                new NormativeApiContract.ContractIdentity(
                        "nekojs-core", ApiContractKind.PORTABLE, "portable-core", ApiVersion.parse("0.0.0")),
                null, List.of(), List.of(), List.of());
        return VerifiedApiContract.create(identity, contract, codeSource,
                "nekojs/api-contract/preview", "sha256:preview", "sha256:preview");
    }

    private static VerifiedApiContract nekojsCorePortableContract() {
        ApiContractIdentity identity = new ApiContractIdentity(
                "nekojs-core", ApiContractKind.PORTABLE, "portable-core", ApiVersion.parse("1.0.0"));

        NormativeApiContract contract = new NormativeApiContract(
                1,
                new NormativeApiContract.ContractIdentity(
                        "nekojs-core", ApiContractKind.PORTABLE, "portable-core", ApiVersion.parse("1.0.0")),
                "Core portable contract",
                List.of(),
                List.of(),
                List.of());

        return VerifiedApiContract.create(identity, contract, TEST_CODE_SOURCE,
                "nekojs-core.json", "sha256:core", "sha256:core");
    }

    private static class TestManagedPlugin implements NekoJSPlugin {
        private final java.util.function.Consumer<ApiContributionRegistry> registrar;

        TestManagedPlugin(java.util.function.Consumer<ApiContributionRegistry> registrar) {
            this.registrar = registrar;
        }

        @Override
        public void registerApiSurface(ApiContributionRegistry registry) {
            registrar.accept(registry);
        }
    }

    private static class TestLegacyPlugin implements NekoJSPlugin {
    }
}
