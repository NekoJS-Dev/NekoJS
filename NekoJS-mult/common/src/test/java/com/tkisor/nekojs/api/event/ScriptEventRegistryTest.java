package com.tkisor.nekojs.api.event;

import com.tkisor.nekojs.api.JSTypeAdapter;
import com.tkisor.nekojs.api.ScriptType;
import com.tkisor.nekojs.api.catalog.ManualDeclarationCatalogEntry;
import com.tkisor.nekojs.api.catalog.TypeDocCatalogEntry;
import com.tkisor.nekojs.api.data.Binding;
import com.tkisor.nekojs.api.plugin.IPluginRuntime;
import com.tkisor.nekojs.api.recipe.RecipeLifecycleContext;
import com.tkisor.nekojs.api.recipe.RecipeNamespaceEntry;
import com.tkisor.nekojs.api.surface.ApiRuntimeView;
import com.tkisor.nekojs.api.surface.ApiSymbolId;
import com.tkisor.nekojs.api.surface.EnvironmentKey;
import com.tkisor.nekojs.core.DefaultScriptEventBridge;
import com.tkisor.nekojs.testfixture.TestPlatformInit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Follow-up regression tests for BUG-B3:
 *
 * <ul>
 *   <li>{@link ScriptEventRegistry#register} must be idempotent when the same key is
 *       re-registered with the same {@code sourceScriptId} (ScriptEvents re-fires after a
 *       full STARTUP reload, and a targeted STARTUP file reload degrades to one), and must
 *       still reject the same key from a different source.</li>
 *   <li>{@link DefaultScriptEventBridge#clearListeners(ScriptType, String)} for STARTUP must
 *       clear definitions of ALL target types by script id, because ScriptEvents.post always
 *       registers definitions with targetType SERVER or CLIENT.</li>
 *   <li>{@link DefaultScriptEventBridge#clearListeners(ScriptType)} for STARTUP must clear
 *       definitions of ALL target types (full STARTUP reload), not just STARTUP ones.</li>
 * </ul>
 */
class ScriptEventRegistryTest {

    private static final String SCRIPT_ID = "startup_scripts/events.js";
    private static final String OTHER_SCRIPT_ID = "startup_scripts/other.js";

    @BeforeEach
    void initAndClear() {
        TestPlatformInit.ensureInitialized();
        clearAllDefinitions();
    }

    @org.junit.jupiter.api.AfterEach
    void clearAfter() {
        clearAllDefinitions();
    }

    private static void clearAllDefinitions() {
        for (ScriptType target : ScriptType.all()) {
            ScriptEventRegistry.clearDefinitions(target);
        }
    }

    @Test
    void sameSourceReregistrationReplacesDefinitionWithoutThrowing() {
        StubPluginRuntime runtime = new StubPluginRuntime();
        AtomicBoolean oldUnregistered = new AtomicBoolean(false);
        ScriptEventDefinition first = new ScriptEventDefinition(
                "grp", "server_evt", ScriptType.SERVER, String.class.getName(), SCRIPT_ID,
                EventBusJS.of(String.class), () -> oldUnregistered.set(true));

        ScriptEventRegistry.register(runtime, first);

        ScriptEventDefinition replacement = new ScriptEventDefinition(
                "grp", "server_evt", ScriptType.SERVER, String.class.getName(), SCRIPT_ID,
                EventBusJS.of(String.class), () -> {});
        assertDoesNotThrow(() -> ScriptEventRegistry.register(runtime, replacement),
                "same-key same-source re-registration must be a replacement, not an error");
        assertTrue(oldUnregistered.get(),
                "replacement must unregister the previous same-source definition");
    }

    @Test
    void differentSourceDuplicateStillThrows() {
        StubPluginRuntime runtime = new StubPluginRuntime();
        ScriptEventDefinition first = new ScriptEventDefinition(
                "grp", "server_evt", ScriptType.SERVER, String.class.getName(), SCRIPT_ID,
                EventBusJS.of(String.class), () -> {});
        ScriptEventRegistry.register(runtime, first);

        ScriptEventDefinition otherSource = new ScriptEventDefinition(
                "grp", "server_evt", ScriptType.SERVER, String.class.getName(), OTHER_SCRIPT_ID,
                EventBusJS.of(String.class), () -> {});
        assertThrows(IllegalArgumentException.class, () -> ScriptEventRegistry.register(runtime, otherSource),
                "same key from a different source id must still be rejected");
    }

    @Test
    void startupClearByScriptIdClearsDefinitionsForAllTargetTypes() {
        StubPluginRuntime runtime = new StubPluginRuntime();
        AtomicBoolean serverUnregistered = new AtomicBoolean(false);
        AtomicBoolean clientUnregistered = new AtomicBoolean(false);

        ScriptEventRegistry.register(runtime, new ScriptEventDefinition(
                "grp", "server_evt", ScriptType.SERVER, String.class.getName(), SCRIPT_ID,
                EventBusJS.of(String.class), () -> serverUnregistered.set(true)));
        ScriptEventRegistry.register(runtime, new ScriptEventDefinition(
                "grp", "client_evt", ScriptType.CLIENT, String.class.getName(), SCRIPT_ID,
                EventBusJS.of(String.class), () -> clientUnregistered.set(true)));

        DefaultScriptEventBridge bridge = new DefaultScriptEventBridge(
                (targetType, groupName, eventName, eventClass, priority, receiveCancelled) -> {});
        bridge.setPluginRuntime(runtime);
        bridge.clearListeners(ScriptType.STARTUP, SCRIPT_ID);

        assertTrue(serverUnregistered.get(),
                "STARTUP per-script clear must clear SERVER-target definitions with the same script id");
        assertTrue(clientUnregistered.get(),
                "STARTUP per-script clear must clear CLIENT-target definitions with the same script id");
    }

    @Test
    void startupClearByTypeClearsDefinitionsForAllTargetTypes() {
        StubPluginRuntime runtime = new StubPluginRuntime();
        AtomicBoolean serverUnregistered = new AtomicBoolean(false);
        AtomicBoolean clientUnregistered = new AtomicBoolean(false);

        ScriptEventRegistry.register(runtime, new ScriptEventDefinition(
                "grp", "server_evt", ScriptType.SERVER, String.class.getName(), SCRIPT_ID,
                EventBusJS.of(String.class), () -> serverUnregistered.set(true)));
        ScriptEventRegistry.register(runtime, new ScriptEventDefinition(
                "grp", "client_evt", ScriptType.CLIENT, String.class.getName(), SCRIPT_ID,
                EventBusJS.of(String.class), () -> clientUnregistered.set(true)));

        DefaultScriptEventBridge bridge = new DefaultScriptEventBridge(
                (targetType, groupName, eventName, eventClass, priority, receiveCancelled) -> {});
        bridge.setPluginRuntime(runtime);
        bridge.clearListeners(ScriptType.STARTUP);

        assertTrue(serverUnregistered.get(),
                "full STARTUP clear must clear SERVER-target definitions");
        assertTrue(clientUnregistered.get(),
                "full STARTUP clear must clear CLIENT-target definitions");
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
}
