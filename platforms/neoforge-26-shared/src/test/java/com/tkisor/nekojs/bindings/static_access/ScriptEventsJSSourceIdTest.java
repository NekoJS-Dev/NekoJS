package com.tkisor.nekojs.bindings.static_access;

import com.tkisor.nekojs.api.JSTypeAdapter;
import com.tkisor.nekojs.api.ScriptType;
import com.tkisor.nekojs.api.catalog.ManualDeclarationCatalogEntry;
import com.tkisor.nekojs.api.catalog.TypeDocCatalogEntry;
import com.tkisor.nekojs.api.data.Binding;
import com.tkisor.nekojs.api.event.EventBusJS;
import com.tkisor.nekojs.api.event.EventGroup;
import com.tkisor.nekojs.api.event.ScriptEventDefinition;
import com.tkisor.nekojs.api.event.ScriptEventRegistry;
import com.tkisor.nekojs.api.plugin.IPluginRuntime;
import com.tkisor.nekojs.api.recipe.RecipeLifecycleContext;
import com.tkisor.nekojs.api.recipe.RecipeNamespaceEntry;
import com.tkisor.nekojs.api.surface.ApiRuntimeView;
import com.tkisor.nekojs.api.surface.ApiSymbolId;
import com.tkisor.nekojs.api.surface.EnvironmentKey;
import com.tkisor.nekojs.platform.IModInfo;
import com.tkisor.nekojs.platform.IPlatform;
import com.tkisor.nekojs.platform.Platform;
import com.tkisor.nekojs.script.ScriptContextRegistry;
import graal.graalvm.polyglot.Context;
import graal.graalvm.polyglot.Value;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * B3 regression test: {@code ScriptEventsJS.registerNative} must resolve the actual
 * current script id from the Graal context so per-script STARTUP reload
 * ({@code ScriptEventRegistry.clearDefinitions(STARTUP, scriptId)}) can clear the
 * definitions registered by a single script. The old constant
 * {@code "nekojs:startup/script_events"} never matches the reload id.
 */
class ScriptEventsJSSourceIdTest {
    private static final String SCRIPT_ID = "startup_scripts/events.js";
    private static final String FALLBACK = "nekojs:startup/script_events";

    @BeforeAll
    static void initPlatformStub() {
        try {
            Platform.init(new StubPlatform());
        } catch (IllegalStateException alreadyInitialized) {
            // Same JVM may already have a platform stub from another test.
        }
    }

    @AfterEach
    void clearAllDefinitions() {
        for (ScriptType target : ScriptType.all()) {
            ScriptEventRegistry.clearDefinitions(target);
        }
    }

    @Test
    void resolvesSourceScriptIdFromBoundGraalContext() {
        Context ctx = Context.newBuilder("js").build();
        try {
            ScriptContextRegistry.bind(ctx, ScriptType.STARTUP);
            ScriptContextRegistry.switchCurrentScriptId(ctx, SCRIPT_ID);
            Value value = ctx.eval("js", "'java.lang.String'");

            assertEquals(SCRIPT_ID, ScriptEventsJS.resolveSourceScriptId(value));
        } finally {
            ScriptContextRegistry.unbind(ctx);
            ctx.close();
        }
    }

    @Test
    void fallsBackToStartupConstantForPlainJavaValues() {
        assertEquals(FALLBACK, ScriptEventsJS.resolveSourceScriptId("java.lang.String"));
        assertEquals(FALLBACK, ScriptEventsJS.resolveSourceScriptId(String.class));
    }

    @Test
    void definitionRegisteredWithResolvedScriptIdIsClearedByPerScriptReload() {
        Context ctx = Context.newBuilder("js").build();
        try {
            ScriptContextRegistry.bind(ctx, ScriptType.STARTUP);
            ScriptContextRegistry.switchCurrentScriptId(ctx, SCRIPT_ID);
            String resolved = ScriptEventsJS.resolveSourceScriptId(ctx.eval("js", "'java.lang.String'"));
            assertEquals(SCRIPT_ID, resolved);

            StubPluginRuntime runtime = new StubPluginRuntime();

            // ScriptEvents.post registers with real target types (SERVER/CLIENT), so the
            // per-script clear must match those target types with the resolved script id.
            AtomicBoolean firstUnregistered = new AtomicBoolean(false);
            ScriptEventDefinition first = new ScriptEventDefinition(
                    "grp", "server_evt", ScriptType.SERVER, String.class.getName(), resolved,
                    EventBusJS.of(String.class), () -> firstUnregistered.set(true));
            ScriptEventRegistry.register(runtime, first);

            ScriptEventRegistry.clearDefinitions(ScriptType.SERVER, SCRIPT_ID);
            assertTrue(firstUnregistered.get(),
                    "definition with the resolved script id and real SERVER target must be cleared by per-script clear");

            // A full STARTUP reload re-runs all startup entries and re-fires ScriptEvents;
            // re-registering the same definition with the same source id must replace the old
            // definition instead of throwing.
            AtomicBoolean recreatedUnregistered = new AtomicBoolean(false);
            ScriptEventDefinition recreated = new ScriptEventDefinition(
                    "grp", "server_evt", ScriptType.SERVER, String.class.getName(), resolved,
                    EventBusJS.of(String.class), () -> recreatedUnregistered.set(true));
            ScriptEventRegistry.register(runtime, recreated);

            ScriptEventDefinition replacement = new ScriptEventDefinition(
                    "grp", "server_evt", ScriptType.SERVER, String.class.getName(), resolved,
                    EventBusJS.of(String.class), () -> {});
            ScriptEventRegistry.register(runtime, replacement);
            assertTrue(recreatedUnregistered.get(),
                    "same-source re-registration must replace the previous definition");

            // The same key with a different source id is still a hard duplicate.
            ScriptEventDefinition otherSource = new ScriptEventDefinition(
                    "grp", "server_evt", ScriptType.SERVER, String.class.getName(), FALLBACK,
                    EventBusJS.of(String.class), () -> {});
            assertThrows(IllegalArgumentException.class, () -> ScriptEventRegistry.register(runtime, otherSource),
                    "different source id for the same key must still throw");
        } finally {
            ScriptContextRegistry.unbind(ctx);
            ctx.close();
        }
    }

    private static final class StubPluginRuntime implements IPluginRuntime {
        @Override public Map<String, Binding> bindings(ScriptType type) { return Map.of(); }
        @Override public Map<String, EventGroup> eventGroups() { return Map.of(); }
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
        @Override public ApiRuntimeView apiRuntime(EnvironmentKey environment) { return null; }
        @Override public Object managedApiImplementation(ApiSymbolId globalId) { return null; }
    }

    private static final class StubPlatform implements IPlatform {
        @Override public boolean isClient() { return false; }
        @Override public boolean isDevelopment() { return true; }
        @Override public String getMcVersion() { return "test"; }
        @Override public Path getGameDir() { return Path.of(System.getProperty("java.io.tmpdir"), "nekojs-smoke-test"); }
        @Override public Map<String, IModInfo> getMods() { return Map.of(); }
        @Override public IModInfo getInfo(String modID) { return null; }
        @Override public String getLoaderId() { return "test"; }
        @Override public String getLoaderVersion() { return "0"; }
    }
}
