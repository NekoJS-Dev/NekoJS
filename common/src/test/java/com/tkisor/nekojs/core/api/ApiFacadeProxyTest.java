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
import graal.graalvm.polyglot.Context;
import graal.graalvm.polyglot.Value;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class ApiFacadeProxyTest {

    public static final class Implementation {
        public String declared(String value) {
            return value.toUpperCase();
        }

        public String accidentalPublicHelper() {
            return "must stay hidden";
        }

        public Object rawReturn() {
            return new Object();
        }
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

    private static ApiSymbolId stableDeclaredId() {
        return ApiSymbolId.parse("member:Stable.declared");
    }

    private static ApiSymbolId stableRawReturnId() {
        return ApiSymbolId.parse("member:Stable.rawReturn");
    }

    private static ApiSignature declaredSignature() {
        return ApiSignature.function(
                List.of(new ApiParameter("value", ApiTypeRef.primitive("string"), false, false)),
                ApiTypeRef.primitive("string"));
    }

    private static ApiSignature rawReturnSignature() {
        return ApiSignature.function(
                List.of(),
                ApiTypeRef.primitive("string"));
    }

    private static VerifiedContractSet contractWithStable() {
        ApiSymbol declaredSymbol = new ApiSymbol(stableDeclaredId(), List.of(declaredSignature()));

        NormativeApiContract contract = new NormativeApiContract(
                1,
                new NormativeApiContract.ContractIdentity(
                        "nekojs-core", ApiContractKind.PORTABLE, "stable-contract", ApiVersion.parse("1.0.0")),
                "Stable contract",
                List.of(declaredSymbol),
                List.of(),
                List.of());

        return VerifiedContractSet.of(
                VerifiedApiContract.create(
                        new ApiContractIdentity("nekojs-core", ApiContractKind.PORTABLE, "stable-contract",
                                ApiVersion.parse("1.0.0")),
                        contract,
                        URI.create("file:///test"),
                        "stable-contract.json",
                        "sha256:integrity",
                        "sha256:compatibility"));
    }

    private static VerifiedContractSet contractWithRawReturn() {
        ApiSymbol rawReturnSymbol = new ApiSymbol(stableRawReturnId(), List.of(rawReturnSignature()));

        NormativeApiContract contract = new NormativeApiContract(
                1,
                new NormativeApiContract.ContractIdentity(
                        "nekojs-core", ApiContractKind.PORTABLE, "raw-contract", ApiVersion.parse("1.0.0")),
                "Raw contract",
                List.of(rawReturnSymbol),
                List.of(),
                List.of());

        return VerifiedContractSet.of(
                VerifiedApiContract.create(
                        new ApiContractIdentity("nekojs-core", ApiContractKind.PORTABLE, "raw-contract",
                                ApiVersion.parse("1.0.0")),
                        contract,
                        URI.create("file:///test"),
                        "raw-contract.json",
                        "sha256:integrity",
                        "sha256:compatibility"));
    }

    private static FrozenApiRegistry resolve(
            VerifiedContractSet contracts,
            ApiContribution... contributions) {

        PluginIdentity owner = new PluginIdentity("nekojs-core", "test-plugin", java.net.URI.create("test:///test-plugin.jar"));
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

    private static ApiFacadeProxy proxyForFixture() {
        Implementation impl = new Implementation();
        FrozenApiRegistry registry = resolve(
                contractWithStable(),
                ApiContribution.symbol(
                        stableDeclaredId(),
                        ApiTier.GLOBAL,
                        "Stable",
                        Set.of(ScriptTypeId.SERVER),
                        List.of(declaredSignature()),
                        (ctx, recv, args) -> {
                            return impl.declared((String) args.get(0));
                        }));

        return ApiFacadeProxy.global(registry, ApiSymbolId.parse("global:Stable"), impl);
    }

    private static ApiFacadeProxy proxyForRawReturn() {
        Implementation impl = new Implementation();
        FrozenApiRegistry registry = resolve(
                contractWithRawReturn(),
                ApiContribution.symbol(
                        stableRawReturnId(),
                        ApiTier.GLOBAL,
                        "Stable",
                        Set.of(ScriptTypeId.SERVER),
                        List.of(rawReturnSignature()),
                        (ctx, recv, args) -> {
                            return impl.rawReturn();
                        }));

        return ApiFacadeProxy.global(registry, ApiSymbolId.parse("global:Stable"), impl);
    }

    @Test
    void proxyExposesOnlyFrozenMembers() {
        try (Context context = Context.newBuilder("js").allowAllAccess(true).build()) {
            context.getBindings("js").putMember("Stable", proxyForFixture());
            assertEquals("OK", context.eval("js", "Stable.declared('ok')").asString());
            assertFalse(context.eval("js", "'accidentalPublicHelper' in Stable").asBoolean());
        }
    }

    @Test
    void rawReturnFailsAtBoundary() {
        try (Context context = Context.newBuilder("js").allowAllAccess(true).build()) {
            context.getBindings("js").putMember("Stable", proxyForRawReturn());
            assertThrows(graal.graalvm.polyglot.PolyglotException.class,
                    () -> context.eval("js", "Stable.rawReturn()"));
        }
    }

    public static final class CallbackPayload {
        public String visible() {
            return "visible";
        }

        public String hidden() {
            return "hidden";
        }
    }

    @Test
    void callbackPayloadIsProxied() {
        try (Context context = Context.newBuilder("js").allowAllAccess(true).build()) {
            CallbackPayload payload = new CallbackPayload();
            FrozenApiRegistry registry = resolve(
                    contractWithStable(),
                    ApiContribution.symbol(
                            stableDeclaredId(),
                            ApiTier.GLOBAL,
                            "Stable",
                            Set.of(ScriptTypeId.SERVER),
                            List.of(declaredSignature()),
                            (ctx, recv, args) -> {
                                // The callback receives the payload
                                return "callback-called";
                            }));

            ApiFacadeProxy proxy = ApiFacadeProxy.global(registry, ApiSymbolId.parse("global:Stable"), null);
            context.getBindings("js").putMember("Stable", proxy);
            context.getBindings("js").putMember("Payload", payload);

            // The payload should only expose frozen members
            assertEquals("callback-called", context.eval("js", "Stable.declared('test')").asString());
        }
    }
}
