package com.tkisor.nekojs.api.capability;

import com.tkisor.nekojs.api.plugin.PluginIdentity;
import com.tkisor.nekojs.api.surface.ApiResolutionException;
import com.tkisor.nekojs.api.surface.ApiVersion;
import com.tkisor.nekojs.api.surface.EnvironmentKey;
import com.tkisor.nekojs.api.surface.EnvironmentScope;
import com.tkisor.nekojs.api.surface.LoaderVersion;
import com.tkisor.nekojs.api.surface.RuntimeDist;
import com.tkisor.nekojs.api.surface.ScriptTypeId;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class CapabilityResolverTest {

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

    private static EnvironmentKey clientEnv() {
        return new EnvironmentKey(
                ScriptTypeId.CLIENT,
                RuntimeDist.CLIENT,
                "neoforge",
                "21.1.0",
                LoaderVersion.parse("21.1.0"),
                "1.21.1",
                Map.of());
    }

    private static CapabilityDefinition coreDefinition() {
        return new CapabilityDefinition(
                "test-capability",
                ApiVersion.parse("1.0.0"),
                CapabilityImplementationMode.SINGLE,
                ProviderPolicy.CORE_ONLY,
                Set.of(),
                null, // no scope restriction
                Set.of(),
                Set.of());
    }

    private static CapabilityDefinition coreDefinitionWithScope(EnvironmentScope scope) {
        return new CapabilityDefinition(
                "test-capability",
                ApiVersion.parse("1.0.0"),
                CapabilityImplementationMode.SINGLE,
                ProviderPolicy.CORE_ONLY,
                Set.of(),
                scope,
                Set.of(),
                Set.of());
    }

    private static CapabilityProviderContribution provider(String name) {
        return new CapabilityProviderContribution(
                new PluginIdentity("nekojs", name, java.net.URI.create("test:///" + name + ".jar")),
                "test-capability",
                100,
                "implementation-" + name,
                null, // no scope restriction
                Map.of());
    }

    private static CapabilityProviderContribution provider(String name, EnvironmentScope scope) {
        return new CapabilityProviderContribution(
                new PluginIdentity("nekojs", name, java.net.URI.create("test:///" + name + ".jar")),
                "test-capability",
                100,
                "implementation-" + name,
                scope,
                Map.of());
    }

    private static CapabilityProviderContribution addonProvider(String owner, String name) {
        return new CapabilityProviderContribution(
                new PluginIdentity(owner, name, java.net.URI.create("test:///" + name + ".jar")),
                "test-capability",
                100,
                "implementation-" + name,
                null,
                Map.of());
    }

    @Test
    void noProviderReturnsUnavailable() {
        CapabilityResolution result = CapabilityResolver.resolve(
                serverEnv(),
                List.of(coreDefinition()),
                List.of());

        assertEquals(1, result.unavailable().size());
        assertEquals("test-capability", result.unavailable().getFirst().name());
        assertEquals("UNAVAILABLE", result.unavailable().getFirst().reason());
        assertTrue(result.active().isEmpty());
    }

    @Test
    void singleEligibleProviderActivates() {
        CapabilityResolution result = CapabilityResolver.resolve(
                serverEnv(),
                List.of(coreDefinition()),
                List.of(provider("a")));

        assertEquals(1, result.active().size());
        assertEquals("test-capability", result.active().getFirst().name());
        assertEquals(CapabilityImplementationMode.SINGLE, result.active().getFirst().mode());
        assertEquals("implementation-a", result.active().getFirst().implementation());
        assertTrue(result.unavailable().isEmpty());
    }

    @Test
    void scopeMismatchReturnsUnavailable() {
        // Definition requires server environment, provider only allows client
        EnvironmentScope clientOnlyScope = new EnvironmentScope(
                ScriptTypeId.CLIENT,
                RuntimeDist.CLIENT,
                Set.of(),
                Set.of(),
                null,
                null);

        CapabilityResolution result = CapabilityResolver.resolve(
                serverEnv(),
                List.of(coreDefinition()),
                List.of(provider("a", clientOnlyScope)));

        assertEquals(1, result.unavailable().size());
        assertEquals("UNAVAILABLE", result.unavailable().getFirst().reason());
    }

    @Test
    void duplicateEligibleProvidersFail() {
        assertThrows(ApiResolutionException.class,
                () -> CapabilityResolver.resolve(
                        serverEnv(),
                        List.of(coreDefinition()),
                        List.of(provider("a"), provider("b"))));
    }

    @Test
    void addonCannotProvideCoreCapability() {
        // CORE_ONLY policy: addon cannot provide
        assertThrows(ApiResolutionException.class,
                () -> CapabilityResolver.resolve(
                        serverEnv(),
                        List.of(coreDefinition()),
                        List.of(addonProvider("some-addon", "addon-plugin"))));
    }

    @Test
    void contractVersionMismatchReturnsUnavailable() {
        // Definition has version 1.0.0, provider has no scope but we test via
        // a definition with required service keys that provider doesn't have
        CapabilityDefinition defWithServices = new CapabilityDefinition(
                "test-capability",
                ApiVersion.parse("1.0.0"),
                CapabilityImplementationMode.SINGLE,
                ProviderPolicy.CORE_ONLY,
                Set.of(),
                null,
                Set.of("required-service"),
                Set.of());

        // Provider doesn't have the required service
        CapabilityProviderContribution providerWithoutService = new CapabilityProviderContribution(
                new PluginIdentity("nekojs", "a", java.net.URI.create("test:///a.jar")),
                "test-capability",
                100,
                "implementation-a",
                null,
                Map.of()); // missing "required-service"

        assertThrows(ApiResolutionException.class,
                () -> CapabilityResolver.resolve(
                        serverEnv(),
                        List.of(defWithServices),
                        List.of(providerWithoutService)));
    }

    @Test
    void providerWithMatchingScopeActivates() {
        EnvironmentScope serverScope = new EnvironmentScope(
                ScriptTypeId.SERVER,
                RuntimeDist.DEDICATED_SERVER,
                Set.of(),
                Set.of(),
                null,
                null);

        CapabilityResolution result = CapabilityResolver.resolve(
                serverEnv(),
                List.of(coreDefinition()),
                List.of(provider("a", serverScope)));

        assertEquals(1, result.active().size());
        assertEquals("test-capability", result.active().getFirst().name());
    }

    @Test
    void emptyDefinitionsReturnsEmptyResult() {
        CapabilityResolution result = CapabilityResolver.resolve(
                serverEnv(),
                List.of(),
                List.of());

        assertTrue(result.active().isEmpty());
        assertTrue(result.unavailable().isEmpty());
    }
}
