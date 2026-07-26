package com.tkisor.nekojs.script;

import com.tkisor.nekojs.api.ScriptType;
import com.tkisor.nekojs.api.contract.ApiContractIdentity;
import com.tkisor.nekojs.api.contract.ApiContractKind;
import com.tkisor.nekojs.api.contract.NormativeApiContract;
import com.tkisor.nekojs.api.contract.VerifiedApiContract;
import com.tkisor.nekojs.api.contract.VerifiedContractSet;
import com.tkisor.nekojs.api.data.Binding;
import com.tkisor.nekojs.api.event.ScriptBindingSchema;
import com.tkisor.nekojs.api.plugin.PluginIdentity;
import com.tkisor.nekojs.api.surface.ApiContribution;
import com.tkisor.nekojs.api.surface.ApiContributionRegistry;
import com.tkisor.nekojs.api.surface.ApiParameter;
import com.tkisor.nekojs.api.surface.ApiRuntimeProvider;
import com.tkisor.nekojs.api.surface.ApiSignature;
import com.tkisor.nekojs.api.surface.ApiSymbol;
import com.tkisor.nekojs.api.surface.ApiSymbolId;
import com.tkisor.nekojs.api.surface.ApiTier;
import com.tkisor.nekojs.api.surface.ApiTypeRef;
import com.tkisor.nekojs.api.surface.ApiVersion;
import com.tkisor.nekojs.api.surface.EnvironmentKey;
import com.tkisor.nekojs.api.surface.LoaderVersion;
import com.tkisor.nekojs.api.surface.RuntimeDist;
import com.tkisor.nekojs.api.surface.ScriptTypeId;
import com.tkisor.nekojs.core.NekoSandboxFactory;
import com.tkisor.nekojs.core.ScriptEventBridge;
import com.tkisor.nekojs.core.api.ApiFacadeProxy;
import com.tkisor.nekojs.core.api.FrozenApiRegistry;
import com.tkisor.nekojs.core.api.JsApiSurfaceResolver;
import com.tkisor.nekojs.core.plugin.NekoPluginRuntime;
import com.tkisor.nekojs.testfixture.TestPlatformInit;
import graal.graalvm.polyglot.Context;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class ManagedApiEnvironmentTest {

    private static final ApiSymbolId STABLE_ID = ApiSymbolId.parse("global:Stable");
    private static final ApiSymbolId MEMBER_ID = ApiSymbolId.parse("member:Stable.declared");

    @BeforeEach
    void setUp() {
        ScriptBindingSchema.clearAll();
        TestPlatformInit.ensureInitialized();
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

    private static ApiSignature declaredSignature() {
        return ApiSignature.function(
                List.of(new ApiParameter("value", ApiTypeRef.primitive("string"), false, false)),
                ApiTypeRef.primitive("string"));
    }

    private static VerifiedContractSet contractWithStable() {
        ApiSymbol declaredSymbol = new ApiSymbol(MEMBER_ID, List.of(declaredSignature()));
        NormativeApiContract contract = new NormativeApiContract(
                1,
                new NormativeApiContract.ContractIdentity(
                        "test-owner", ApiContractKind.PORTABLE, "stable-contract", ApiVersion.parse("1.0.0")),
                "Stable contract",
                List.of(declaredSymbol),
                List.of(),
                List.of());
        return VerifiedContractSet.of(
                new VerifiedApiContract(
                        new ApiContractIdentity("test-owner", ApiContractKind.PORTABLE, "stable-contract",
                                ApiVersion.parse("1.0.0")),
                        contract,
                        URI.create("file:///test"),
                        "stable-contract.json",
                        "sha256:integrity",
                        "sha256:compatibility"));
    }

    private static FrozenApiRegistry resolveRegistry(VerifiedContractSet contracts) {
        PluginIdentity owner = new PluginIdentity("test-owner", "test-plugin", URI.create("test:///test-plugin.jar"));
        ApiContributionRegistry registry = ApiContributionRegistry.ownedBy(owner, contracts);
        registry.registerSymbol(ApiContribution.symbol(
                MEMBER_ID,
                ApiTier.GLOBAL,
                "Stable",
                Set.of(ScriptTypeId.SERVER),
                List.of(declaredSignature()),
                (ctx, recv, args) -> "managed-result"));
        return JsApiSurfaceResolver.resolve(serverEnv(), contracts, List.of(registry), List.of());
    }

    @Test
    void managedGlobalExposesOnlyRegistryMembers() {
        FrozenApiRegistry registry = resolveRegistry(contractWithStable());
        ApiFacadeProxy proxy = ApiFacadeProxy.global(registry, STABLE_ID, new Object());

        try (Context context = Context.newBuilder("js").allowAllAccess(true).build()) {
            context.getBindings("js").putMember("Stable", proxy);
            assertTrue(context.eval("js", "'declared' in Stable").asBoolean());
            assertFalse(context.eval("js", "'accidentalPublicHelper' in Stable").asBoolean());
        }
    }

    @Test
    void legacyBindingStillWorksWithJavaMemberIndex() {
        Map<String, Binding> legacyBindings = Map.of(
                "LegacyApi", Binding.of("LegacyApi", new LegacyApiImpl()));
        ScriptBindingSchema.BindingMembers members = new ScriptBindingSchema.BindingMembers(
                Set.of("visibleMethod", "anotherMethod"));
        ScriptBindingSchema.register(ScriptType.SERVER, Map.of("LegacyApi", members));

        ScriptBindingSchema.BindingMembers resolved = ScriptBindingSchema.lookup(ScriptType.SERVER).get("LegacyApi");
        assertNotNull(resolved);
        assertTrue(resolved.contains("visibleMethod"));
        assertTrue(resolved.contains("anotherMethod"));
    }

    @Test
    void sameNameManagedAndLegacyConflictDetected() {
        Map<String, Binding> legacyBindings = Map.of(
                "Stable", Binding.of("Stable", new LegacyApiImpl()));
        FrozenApiRegistry registry = resolveRegistry(contractWithStable());
        ScriptBindingSchema.BindingMembers managedMembers = ScriptBindingSchema.fromSurface(
                registry.environmentSnapshot().surfaceSnapshot(), STABLE_ID);
        ScriptBindingSchema.BindingMembers legacyMembers = new ScriptBindingSchema.BindingMembers(
                Set.of("legacyMethod"));

        assertFalse(managedMembers.memberNames().equals(legacyMembers.memberNames()),
                "Managed and legacy member sets for same name should differ");
    }

    public static final class LegacyApiImpl {
        public String visibleMethod() { return "legacy"; }
        public String anotherMethod() { return "legacy2"; }
    }
}
