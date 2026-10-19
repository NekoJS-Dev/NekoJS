package com.tkisor.nekojs.script;

import com.tkisor.nekojs.api.JSTypeAdapter;
import com.tkisor.nekojs.api.ScriptType;
import com.tkisor.nekojs.api.catalog.ManualDeclarationCatalogEntry;
import com.tkisor.nekojs.api.catalog.TypeDocCatalogEntry;
import com.tkisor.nekojs.api.data.Binding;
import com.tkisor.nekojs.api.event.EventGroup;
import com.tkisor.nekojs.api.event.ScriptBindingSchema;
import com.tkisor.nekojs.api.plugin.IPluginRuntime;
import com.tkisor.nekojs.api.recipe.RecipeLifecycleContext;
import com.tkisor.nekojs.api.recipe.RecipeNamespaceEntry;
import com.tkisor.nekojs.api.surface.ApiRuntimeView;
import com.tkisor.nekojs.api.surface.ApiSymbolId;
import com.tkisor.nekojs.api.surface.EnvironmentKey;
import com.tkisor.nekojs.core.ScriptEventBridge;
import com.tkisor.nekojs.core.compiler.ScriptCompilerRegistry;
import com.tkisor.nekojs.core.config.SandboxConfig;
import com.tkisor.nekojs.core.fs.NekoJSPaths;
import com.tkisor.nekojs.core.module.NekoModulePipelineCache;
import com.tkisor.nekojs.core.state.GenerationGlobals;
import com.tkisor.nekojs.core.state.GlobalStateStores;
import com.tkisor.nekojs.testfixture.NekoModuleTestFixtures;
import com.tkisor.nekojs.testfixture.TestPlatformInit;
import graal.graalvm.polyglot.Context;
import graal.graalvm.polyglot.PolyglotException;
import graal.graalvm.polyglot.Value;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Exercises binding installation with real guest inspection and caller Context scopes. */
class ScriptEnvironmentBindingScopeTest {
    @BeforeAll
    static void initializePlatform() {
        TestPlatformInit.ensureInitialized();
    }

    @Test
    void inspectionCollectsGuestAndBindingNamesWithoutEnteringPluginCallbacks() {
        try (Harness harness = new Harness(null)) {
            harness.install();

            assertKnownGlobals(harness.session.bindingSchemaView());
            assertTrue(harness.observed.containsAll(List.of("events", "bindings", "value", "api", "groups")));
            assertNull(currentMarker());
        }
    }

    @Test
    void inspectionRestoresAnAlreadyEnteredCallerContext() {
        try (Context caller = Context.newBuilder("js").build(); Harness harness = new Harness("caller")) {
            caller.eval("js", "globalThis.scopeMarker = 'caller'");
            caller.enter();
            try {
                harness.install();
                assertKnownGlobals(harness.session.bindingSchemaView());
                assertEquals("caller", currentMarker());
            } finally {
                caller.leave();
            }
            assertNull(currentMarker());
        }
    }

    @Test
    void inspectionBalancesAnAlreadyEnteredTargetContext() {
        try (Harness harness = new Harness("candidate")) {
            harness.context.enter();
            try {
                harness.install();
                assertKnownGlobals(harness.session.bindingSchemaView());
                assertEquals("candidate", currentMarker());
            } finally {
                harness.context.leave();
            }
            assertNull(currentMarker());
        }
    }

    @Test
    void failedGuestInspectionRestoresCallerAndRetainsActiveSchemaBeforeRetry() {
        try (Context caller = Context.newBuilder("js").build(); Harness harness = new Harness("caller")) {
            Object activeToken = new Object();
            ScriptBindingSchema.View active = harness.owner.beginBindingSchemaCandidate(activeToken, ScriptType.SERVER,
                    Map.of("Previous", new ScriptBindingSchema.BindingMembers(Set.of("keep"))),
                    Set.of("Previous"), null);
            harness.owner.commitBindingSchemaCandidate(activeToken);
            harness.context.eval("js", """
                    globalThis.savedNames = Object.getOwnPropertyNames;
                    Object.getOwnPropertyNames = () => { throw new Error('enumeration-failed'); };
                    """);
            caller.eval("js", "globalThis.scopeMarker = 'caller'");
            caller.enter();
            try {
                PolyglotException failure = assertThrows(PolyglotException.class, harness::install);
                assertTrue(failure.getMessage().contains("enumeration-failed"));
                assertEquals("caller", currentMarker());
                assertEquals(active, harness.owner.activeBindingSchemaView(ScriptType.SERVER));
                assertTrue(harness.session.bindingSchemaView().knownGlobals().isEmpty());

                harness.context.eval("js", "Object.getOwnPropertyNames = savedNames");
                harness.install();
                assertKnownGlobals(harness.session.bindingSchemaView());
                assertEquals(active, harness.owner.activeBindingSchemaView(ScriptType.SERVER));
                harness.factory.publishEnvironmentBindings(harness.context, harness.session);
                assertEquals(harness.session.bindingSchemaView(), harness.owner.activeBindingSchemaView(ScriptType.SERVER));
                assertEquals("caller", currentMarker());
            } finally {
                caller.leave();
            }
            assertNull(currentMarker());
        }
    }

    private static void assertKnownGlobals(ScriptBindingSchema.View view) {
        assertTrue(view.knownGlobals().containsAll(Set.of(
                "HiddenGuest", "BridgeOnly", "PluginValue", "global", "shared", "Object", "this", "arguments", "super")));
        assertFalse(view.knownGlobals().contains("Previous"));
    }

    private static String currentMarker() {
        Context current;
        try {
            current = Context.getCurrent();
        } catch (IllegalStateException absent) {
            return null;
        }
        return current.eval("js", "globalThis.scopeMarker").asString();
    }

    private static final class Harness implements AutoCloseable {
        final Context context = Context.newBuilder("js").allowAllAccess(true).build();
        final GlobalStateStores stores = new GlobalStateStores();
        final GenerationGlobals globals = stores.newGeneration(ScriptType.SERVER, true);
        final NekoModulePipelineCache owner = NekoModuleTestFixtures.newCache(
                NekoJSPaths.get(), ScriptCompilerRegistry.createRuntimeRegistry(), SandboxConfig.defaultConfig());
        final NekoModulePipelineCache session = owner.openSession();
        final List<String> observed = new ArrayList<>();
        final ScriptEnvironmentFactory factory;

        Harness(String expectedMarker) {
            Runnable observe = () -> assertEquals(expectedMarker, currentMarker());
            ScriptEventBridge bridge = new ScriptEventBridge() {
                @Override public void bindEvents(Value bindings, ScriptType type) {
                    observe.run();
                    observed.add("events");
                    bindings.putMember("BridgeOnly", "bridge");
                }
                @Override public void clearListeners(ScriptType type) {}
            };
            factory = new ScriptEnvironmentFactory(bridge, new ProbeRuntime(observe, observed), null, stores);
            context.eval("js", """
                    globalThis.scopeMarker = 'candidate';
                    Object.defineProperty(globalThis, 'HiddenGuest', { value: 1, enumerable: false });
                    """);
        }

        void install() {
            factory.installEnvironmentBindings(context, ScriptType.SERVER, globals, session);
        }

        @Override public void close() {
            globals.close();
            context.close();
            session.close();
            owner.close();
            stores.closeAll();
        }
    }

    private record ProbeRuntime(Runnable observe, List<String> observed) implements IPluginRuntime {
        @Override public Map<String, Binding> bindings(ScriptType type) {
            observe.run();
            observed.add("bindings");
            return Map.of("PluginValue", new Binding() {
                @Override public String name() { return "PluginValue"; }
                @Override public Object value() {
                    observe.run();
                    observed.add("value");
                    return "plugin";
                }
            });
        }
        @Override public Map<String, EventGroup> eventGroups() {
            observe.run();
            observed.add("groups");
            return Map.of();
        }
        @Override public ApiRuntimeView apiRuntime(EnvironmentKey environment) {
            observe.run();
            observed.add("api");
            return null;
        }
        @Override public List<JSTypeAdapter<?>> adapters() { return List.of(); }
        @Override public List<TypeDocCatalogEntry> typeDocs() { return List.of(); }
        @Override public List<ManualDeclarationCatalogEntry> manualDeclarations() { return List.of(); }
        @Override public Map<String, String> nodeModules() { return Map.of(); }
        @Override public Map<String, RecipeNamespaceEntry> recipeNamespaces() { return Map.of(); }
        @Override public void beforeRecipeLoading(RecipeLifecycleContext context) {}
        @Override public void afterRecipes(RecipeLifecycleContext context) {}
        @Override public void fireInit() {}
        @Override public void fireInitStartup() {}
        @Override public void fireAfterInit() {}
        @Override public void fireBeforeScriptsLoaded(ScriptType type) {}
        @Override public void fireAfterScriptsLoaded(ScriptType type) {}
        @Override public Object managedApiImplementation(ApiSymbolId globalId) { return null; }
    }
}
