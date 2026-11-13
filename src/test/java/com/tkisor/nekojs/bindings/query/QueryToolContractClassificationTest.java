package com.tkisor.nekojs.bindings.query;

import com.tkisor.nekojs.api.NekoJSPlugin;
import com.tkisor.nekojs.api.ScriptType;
import com.tkisor.nekojs.api.catalog.BindingCatalogEntry;
import com.tkisor.nekojs.api.catalog.LegacySurfaceAdapter;
import com.tkisor.nekojs.api.contract.VerifiedApiContract;
import com.tkisor.nekojs.api.data.BindingRegistry;
import com.tkisor.nekojs.api.surface.ApiSymbol;
import com.tkisor.nekojs.api.surface.ApiSymbolId;
import com.tkisor.nekojs.api.surface.ApiTypeRef;
import com.tkisor.nekojs.core.api.CoreManagedApiBootstrap;
import com.tkisor.nekojs.util.selector.EntitySelectorsJS;
import com.tkisor.nekojs.util.selector.EntitySelectorsPlugin;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Classifies the source contract of the query tools.
 *
 * <p>The two query domains (DataMap / EntitySelectors) classify as <b>query bindings</b>:
 * BindingsPoint ({@code registerBinding}) collects them, and the catalog observation surface
 * ({@code global:} kind plus a placeholder signature) shows them. They do <b>not</b> enter the
 * portable-core managed contract: an existing entry point does not mean managed stable. Neither
 * has an event owner, a lifecycle Point or an event wrapper.
 *
 * <p>Every assertion observes from the outermost caller-facing entry points
 * ({@code CoreManagedApiBootstrap.buildContract}, {@code LegacySurfaceAdapter},
 * {@code BindingRegistry}) and touches no golden.
 */
class QueryToolContractClassificationTest {

    private static final URI TEST_CODE_SOURCE = URI.create("file:///ticket25-query-tools.jar");

    /** Catalog observation: a query binding enters legacySurface with the global kind and a
     * placeholder void signature. */
    @Test
    void queryBindingIsObservedAsLegacyGlobalSymbolWithPlaceholderSignature() {
        BindingCatalogEntry entry =
                BindingCatalogEntry.of("EntitySelectors", ScriptType.SERVER, EntitySelectorsJS.class, false);

        List<ApiSymbol> legacy = LegacySurfaceAdapter.fromBindings(List.of(entry));

        assertEquals(1, legacy.size(), "one binding entry maps to exactly one observation symbol");
        ApiSymbolId id = legacy.get(0).id();
        assertEquals("global", id.kind(), "binding observation kind must be 'global'");
        assertEquals("EntitySelectors", id.qualifiedName());
        assertEquals(1, legacy.get(0).signatures().size(), "placeholder signature only");
        var signature = legacy.get(0).signatures().get(0);
        assertTrue(signature.parameters().isEmpty(), "observation signature must not invent parameters");
        assertEquals(ApiTypeRef.voidType(), signature.returnType(),
                "observation signature is a void placeholder, not a real contract signature");
    }

    /** The managed contract leaves out the query globals (DataMap/EntitySelectors) but includes
     * the Registry alternative query surface. */
    @Test
    void queryToolGlobalsAreNotInManagedContractWhileRegistryAlternativeIs() {
        VerifiedApiContract contract = CoreManagedApiBootstrap.buildContract(TEST_CODE_SOURCE);
        Set<String> symbolIds = contract.contract().symbols().stream()
                .map(symbol -> symbol.id().value())
                .collect(Collectors.toSet());

        // A runtime entry point is not the same as managed stable: listing a binding global does not
        // promote it to managed stable.
        assertFalse(symbolIds.contains("global:DataMap"),
                "DataMap query binding must stay on the observation plane, not the managed contract");
        assertFalse(symbolIds.contains("global:EntitySelectors"),
                "EntitySelectors query binding must stay on the observation plane, not the managed contract");

        // The alternative DataMap query surface, the dataMap query on the Registry facade, is a managed
        // contract symbol.
        assertTrue(symbolIds.contains("global:Registry"),
                "Registry facade global must be in the managed contract");
        assertTrue(symbolIds.contains("member:RegistryView.dataMapIds"),
                "RegistryView.dataMapIds (portable data map query) must be a contract symbol");
        assertTrue(symbolIds.contains("member:RegistryView.dataMapValue"),
                "RegistryView.dataMapValue (portable data map query) must be a contract symbol");
    }

    /** EntitySelectorsPlugin has no event owner: it does not implement an event Contributor and does not
     * override the event registration hooks. */
    @Test
    void entitySelectorsPluginCarriesNoEventOwnerAndNoLifecyclePoint() {
        Set<String> implemented = List.of(EntitySelectorsPlugin.class.getInterfaces()).stream()
                .map(Class::getName)
                .collect(Collectors.toSet());

        assertEquals(
                Set.of(NekoJSPlugin.class.getName(), "com.tkisor.nekojs.core.plugin.BindingsPoint$Contributor"),
                implemented,
                "EntitySelectorsPlugin must only project the bindings collection path (no event/Point owner)");

        Set<String> declared = List.of(EntitySelectorsPlugin.class.getDeclaredMethods()).stream()
                .map(method -> method.getName() + "/" + method.getParameterCount())
                .collect(Collectors.toSet());
        assertFalse(declared.contains("registerEvents/1"),
                "query binding must not register events");
        assertFalse(declared.contains("registerClientEvents/1"),
                "query binding must not register client events");
    }

    /** A query needs a ServerLevel, so the binding is visible only to the SERVER and TEST script types. */
    @Test
    void entitySelectorsBindingIsVisibleOnlyToServerAndTestScriptTypes() {
        for (ScriptType type : ScriptType.values()) {
            BindingRegistry.BindingRegistryImpl registry = new BindingRegistry.BindingRegistryImpl(type);
            new EntitySelectorsPlugin().registerBinding(registry);

            if (type == ScriptType.SERVER || type == ScriptType.TEST) {
                assertTrue(registry.viewRegistered().containsKey("EntitySelectors"),
                        "EntitySelectors must be registered for " + type);
                assertEquals(EntitySelectorsJS.class, registry.viewRegistered().get("EntitySelectors").valueType(),
                        "binding valueType must be the query facade class");
            } else {
                assertFalse(registry.viewRegistered().containsKey("EntitySelectors"),
                        "EntitySelectors must not be registered for " + type + " (no ServerLevel there)");
            }
        }
    }

    /** Pins the runtime members of the EntitySelectors factory and query entry points; that list feeds the
     * declaration parity check. */
    @Test
    void entitySelectorsFactoryAndQueryMembersExistAtRuntime() throws Exception {
        // Factory side.
        assertNotNull(EntitySelectorsJS.class.getMethod("create", java.util.function.Consumer.class));
        assertNotNull(EntitySelectorsJS.class.getMethod("builder"));
        for (String preset : List.of("allPlayers", "allEntities", "nearestPlayer",
                "nearestEntity", "randomPlayer", "randomEntity")) {
            assertNotNull(EntitySelectorsJS.class.getMethod(preset), "missing factory preset: " + preset);
        }
        // Query side. A bare JVM must not initialize the MC argument classes, so the classes load with
        // initialize=false, as in VanillaRegistryProbe.
        ClassLoader loader = getClass().getClassLoader();
        Class<?> serverLevel = Class.forName("net.minecraft.server.level.ServerLevel", false, loader);
        Class<?> entitySelector =
                Class.forName("net.minecraft.commands.arguments.selector.EntitySelector", false, loader);
        assertNotNull(EntitySelectorsJS.class.getMethod("find", serverLevel, entitySelector));
        assertNotNull(EntitySelectorsJS.class.getMethod("find",
                serverLevel, entitySelector, double.class, double.class, double.class));
    }
}
