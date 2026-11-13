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
import com.tkisor.nekojs.bindings.static_access.ScriptEventsJS;
import com.tkisor.nekojs.core.DefaultScriptEventBridge;
import com.tkisor.nekojs.script.ScriptContextRegistry;
import com.tkisor.nekojs.testfixture.TestPlatformInit;
import graal.graalvm.polyglot.Context;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The smallest runnable chain for script events: follow the production sequence (STARTUP
 * declaration, {@code ScriptEvents.post}, SERVER listening and firing) end to end through a real
 * Graal evaluation.
 *
 * <p>The chain matches {@code ScriptManager.doLoadScripts}: bindEvents (built-in groups plus
 * dynamic groups), then script execution, then {@code ScriptEvents.post(registrar)}. The example
 * only uses what has passed the gate (dynamic declaration, listening and post) and does not touch
 * NativeEvents, the legacy/raw path (see {@code NativeEventsLegacyTierCharacterizationTest}).
 */
class ScriptEventsMinimalExampleTest {

    /** Startup side: declares the named event groups. */
    static final String STARTUP_EXAMPLE = """
            // startup_scripts/events.js —— ScriptEvents 动态声明（票 14 示例）
            ScriptEvents.server(event => event.register('MyEvents', 'bossKilled'));
            ScriptEvents.client(event => event.register('HudEvents', 'cooldownEnded'));
            """;

    /** Server side: listens, then fires the event from the script itself. */
    static final String SERVER_EXAMPLE = """
            // server_scripts/boss.js —— 监听动态事件并触发
            MyEvents.bossKilled(payload => {
              console.log('boss killed: ' + payload.boss);
            });
            MyEvents.bossKilled.post({ boss: 'ender_dragon' });
            """;

    /** Captures console.log; the example scripts keep the console.log form and the test redirects it. */
    public static final class LogCapture {
        volatile String lastLine;

        public void record(String line) {
            lastLine = line;
        }
    }

    private StubRuntime runtime;
    private ScriptEventsJS scriptEvents;
    private DefaultScriptEventBridge bridge;
    private Context startupContext;
    private Context serverContext;

    @BeforeAll
    static void initPlatform() {
        TestPlatformInit.ensureInitialized();
    }

    @BeforeEach
    void setUp() {
        clearAllDefinitions();
        runtime = new StubRuntime();
        runtime.eventGroups.put("ScriptEvents", ScriptEvents.GROUP);
        scriptEvents = new ScriptEventsJS();
        scriptEvents.bindRuntime(runtime);
        bridge = new DefaultScriptEventBridge(scriptEvents);
        bridge.setPluginRuntime(runtime);

        startupContext = Context.newBuilder("js").allowAllAccess(true).build();
        ScriptContextRegistry.bind(startupContext, ScriptType.STARTUP);
        serverContext = Context.newBuilder("js").allowAllAccess(true).build();
        ScriptContextRegistry.bind(serverContext, ScriptType.SERVER);
    }

    @AfterEach
    void tearDown() {
        clearAllDefinitions();
        ScriptContextRegistry.unbind(startupContext);
        ScriptContextRegistry.unbind(serverContext);
        startupContext.close();
        serverContext.close();
    }

    private static void clearAllDefinitions() {
        for (ScriptType target : ScriptType.all()) {
            ScriptEventRegistry.clearDefinitions(target);
        }
    }

    @Test
    void minimalExampleRunsTheDeclareListenPostChain() {
        // (1) STARTUP: bind the event groups (including the ScriptEvents registration bus) and run the declaring script
        bridge.bindEvents(startupContext.getBindings("js"), ScriptType.STARTUP);
        startupContext.eval("js", STARTUP_EXAMPLE);

        // (2) Production sequence: once the scripts have run, ScriptEvents.post(registrar) records the registrations
        ScriptEvents.post(scriptEvents);

        // (3) SERVER: bindEvents binds the dynamic groups; redirect console.log, then run the example
        LogCapture capture = new LogCapture();
        serverContext.getBindings("js").putMember("capture", capture);
        serverContext.eval("js", "globalThis.console = { log: (m) => capture.record(m) };");
        bridge.bindEvents(serverContext.getBindings("js"), ScriptType.SERVER);
        serverContext.eval("js", SERVER_EXAMPLE);

        assertEquals("boss killed: ender_dragon", capture.lastLine,
                "the minimal example must deliver listen + post end to end");

        // (4) Each dynamic group lands on its own side: the SERVER-target group is in the server environment,
        // the CLIENT-target group is not
        var serverGroups = ScriptEventRegistry.groupsFor(ScriptType.SERVER);
        var clientGroups = ScriptEventRegistry.groupsFor(ScriptType.CLIENT);
        assertEquals(1, serverGroups.get("MyEvents").size());
        assertEquals(1, clientGroups.get("HudEvents").size());
        assertEquals(false, serverGroups.containsKey("HudEvents"),
                "CLIENT-target dynamic group must not be exposed to the SERVER environment");
    }

    private static final class StubRuntime implements IPluginRuntime {
        final Map<String, EventGroup> eventGroups = new java.util.LinkedHashMap<>();

        @Override public Map<String, Binding> bindings(ScriptType type) { return Map.of(); }
        @Override public Map<String, EventGroup> eventGroups() { return eventGroups; }
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
