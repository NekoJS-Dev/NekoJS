package com.tkisor.nekojs.api.event;

import com.tkisor.nekojs.api.JSTypeAdapter;
import com.tkisor.nekojs.api.ScriptType;
import com.tkisor.nekojs.api.catalog.EventCatalogEntry;
import com.tkisor.nekojs.api.catalog.NekoScriptCatalog;
import com.tkisor.nekojs.api.catalog.TypeDocCatalogEntry;
import com.tkisor.nekojs.api.catalog.ManualDeclarationCatalogEntry;
import com.tkisor.nekojs.api.data.Binding;
import com.tkisor.nekojs.api.plugin.IPluginRuntime;
import com.tkisor.nekojs.api.recipe.RecipeLifecycleContext;
import com.tkisor.nekojs.api.recipe.RecipeNamespaceEntry;
import com.tkisor.nekojs.api.surface.ApiRuntimeView;
import com.tkisor.nekojs.api.surface.ApiSymbolId;
import com.tkisor.nekojs.api.surface.EnvironmentKey;
import com.tkisor.nekojs.bindings.static_access.ScriptEventsJS;
import com.tkisor.nekojs.core.DefaultScriptEventBridge;
import com.tkisor.nekojs.probe.backend.python.ApiTypeRefPyRenderer;
import com.tkisor.nekojs.probe.backend.python.PythonEventRenderer;
import com.tkisor.nekojs.probe.backend.typescript.AdapterAliasGenerator;
import com.tkisor.nekojs.probe.backend.typescript.EventDeclarationGenerator;
import com.tkisor.nekojs.probe.types.TypeAliasRegistry;
import com.tkisor.nekojs.testfixture.TestPlatformInit;
import graal.graalvm.polyglot.Context;
import graal.graalvm.polyglot.Value;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Diagnosable failures of dynamic ScriptEvents declarations, plus declaration parity.
 *
 * <p>Failures that must be diagnosable:
 * <ul>
 *   <li>registration conflicts: the group name clashes with a built-in event group, with a
 *       built-in binding, or repeats the same key from another source. All throw
 *       {@link IllegalArgumentException} with a specific message;</li>
 *   <li>invalid identifiers: a group or name that is not a JS identifier is rejected outright;</li>
 *   <li>unknown group or member: script-side access to a built-in or a dynamic group reports
 *       "No such ... bus", and a side where the group does not apply reports
 *       "not available in SIDE";</li>
 *   <li>side filtering: a SERVER-target dynamic group is not bound in a CLIENT environment, so
 *       scripts there cannot see it.</li>
 * </ul>
 *
 * <p>Parity: dynamic events go through the <strong>same</strong> catalog derivation as built-in
 * events ({@link NekoScriptCatalog#events}) and produce an {@link EventCatalogEntry}. Those
 * entries then drive TS ({@link EventDeclarationGenerator}) and Python
 * ({@link PythonEventRenderer}) declaration generation, so both backends consume one entry list
 * and produce the same member shape: an any payload and a post that fires exactly once.
 */
class ScriptEventsDeclarationDiagnosticsTest {

    private ScriptEventsJS scriptEvents;
    private StubRuntime runtime;
    private Context context;

    @BeforeAll
    static void initPlatform() {
        TestPlatformInit.ensureInitialized();
    }

    @BeforeEach
    void setUp() {
        clearAllDefinitions();
        runtime = new StubRuntime();
        scriptEvents = new ScriptEventsJS();
        scriptEvents.bindRuntime(runtime);
        context = Context.newBuilder("js").allowAllAccess(true).build();
        ScriptContextRegistryBridge.bind(context);
    }

    @AfterEach
    void tearDown() {
        clearAllDefinitions();
        ScriptContextRegistryBridge.unbind(context);
        context.close();
    }

    private static void clearAllDefinitions() {
        for (ScriptType target : ScriptType.all()) {
            ScriptEventRegistry.clearDefinitions(target);
        }
    }

    // ---- Registration conflicts and invalid input: diagnosable failures ----

    @Test
    void groupNameConflictingWithBuiltInEventGroupIsRejected() {
        runtime.eventGroups.put("ServerEvents", EventGroup.of("ServerEvents"));
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> scriptEvents.register(ScriptType.SERVER, "ServerEvents", "anything", "s.js"));
        assertTrue(ex.getMessage().contains("conflicts with built-in event group"),
                "diagnostic must name the conflicting built-in group: " + ex.getMessage());
    }

    @Test
    void groupNameConflictingWithBuiltInBindingIsRejected() {
        runtime.bindings.put("NativeEvents", Binding.of("NativeEvents", new Object()));
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> scriptEvents.register(ScriptType.SERVER, "NativeEvents", "anything", "s.js"));
        assertTrue(ex.getMessage().contains("conflicts with built-in binding"),
                "diagnostic must name the conflicting built-in binding: " + ex.getMessage());
    }

    @Test
    void crossSourceDuplicateKeyIsRejectedWithRegistrationKey() {
        scriptEvents.register(ScriptType.SERVER, "MyEvents", "bossKilled", "scripts/a.js");
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> scriptEvents.register(ScriptType.SERVER, "MyEvents", "bossKilled", "scripts/b.js"));
        assertTrue(ex.getMessage().contains("MyEvents.bossKilled") && ex.getMessage().contains("SERVER"),
                "diagnostic must carry the full registration key: " + ex.getMessage());
    }

    @Test
    void nonIdentifierNamesAreRejected() {
        IllegalArgumentException group = assertThrows(IllegalArgumentException.class,
                () -> scriptEvents.register(ScriptType.SERVER, "Not A Group", "evt", "s.js"));
        assertTrue(group.getMessage().contains("valid JS identifier"), group.getMessage());
        IllegalArgumentException name = assertThrows(IllegalArgumentException.class,
                () -> scriptEvents.register(ScriptType.SERVER, "MyEvents", "not-a-name", "s.js"));
        assertTrue(name.getMessage().contains("valid JS identifier"), name.getMessage());
    }

    @Test
    void reloadCleanupMakesReregistrationPossibleAfterDiagnosableConflict() {
        scriptEvents.register(ScriptType.SERVER, "MyEvents", "bossKilled", "scripts/a.js");

        // The production sequence (ScriptManager.doLoadScripts): a STARTUP reload clears all
        // definitions first, then replays ScriptEvents.post. A repeat registration that was not
        // cleared, including one from the same source, is rejected at the registration entry with a
        // diagnosable error; it never silently stacks a second definition.
        IllegalArgumentException conflict = assertThrows(IllegalArgumentException.class,
                () -> scriptEvents.register(ScriptType.SERVER, "MyEvents", "bossKilled", "scripts/a.js"));
        assertTrue(conflict.getMessage().contains("MyEvents.bossKilled"),
                "diagnostic must carry the full registration key: " + conflict.getMessage());

        DefaultScriptEventBridge bridge = new DefaultScriptEventBridge(scriptEvents);
        bridge.setPluginRuntime(runtime);
        bridge.clearListeners(ScriptType.STARTUP);
        assertEquals(0, NekoScriptCatalog.events(runtime).stream()
                .filter(e -> e.group().equals("MyEvents")).count(),
                "full STARTUP sweep must remove the dynamic definitions");

        // Re-registering the same key after the cleanup succeeds, and the catalog still holds exactly one entry
        scriptEvents.register(ScriptType.SERVER, "MyEvents", "bossKilled", "scripts/a.js");
        assertEquals(1, NekoScriptCatalog.events(runtime).stream()
                .filter(e -> e.group().equals("MyEvents")).count(),
                "post-cleanup re-registration yields exactly one catalog entry");
    }

    // ---- Unknown group, unknown member, wrong side: diagnosable from the script side ----

    @Test
    void unknownDynamicGroupMemberIsDiagnosed() {
        scriptEvents.register(ScriptType.SERVER, "MyEvents", "bossKilled", "scripts/a.js");
        ScriptEventGroupJS group = new ScriptEventGroupJS("MyEvents",
                ScriptEventRegistry.groupsFor(ScriptType.SERVER).get("MyEvents"));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> group.getMember("nope"));
        assertEquals("No such script event bus: MyEvents.nope", ex.getMessage());
        assertFalse(group.hasMember("nope"));
        assertTrue(group.hasMember("bossKilled"));
    }

    @Test
    void builtInGroupUnknownMemberAndWrongSideAreDiagnosed() {
        EventGroup builtIn = EventGroup.of("BuiltInEvents");
        builtIn.server("tick", String.class);
        builtIn.client("render", String.class);
        EventGroupJS serverView = new EventGroupJS(builtIn, ScriptType.SERVER);

        IllegalArgumentException unknown = assertThrows(IllegalArgumentException.class,
                () -> serverView.getMember("nope"));
        assertEquals("No such event bus: BuiltInEvents.nope", unknown.getMessage());

        IllegalArgumentException wrongSide = assertThrows(IllegalArgumentException.class,
                () -> serverView.getMember("render"));
        assertEquals("Event 'BuiltInEvents.render' not available in SERVER", wrongSide.getMessage());
    }

    @Test
    void serverTargetDynamicGroupIsBoundOnlyOnServerSide() {
        scriptEvents.register(ScriptType.SERVER, "MyEvents", "bossKilled", "scripts/a.js");
        Recorder recorder = new Recorder();
        context.getBindings("js").putMember("recorder", recorder);
        DefaultScriptEventBridge bridge = new DefaultScriptEventBridge(scriptEvents);
        bridge.setPluginRuntime(runtime);

        Value serverBindings = context.getBindings("js");
        bridge.bindEvents(serverBindings, ScriptType.SERVER);
        assertTrue(serverBindings.hasMember("MyEvents"),
                "SERVER-target dynamic group is visible to server scripts");
        // External behavior of listening and firing (ScriptEventBusJS: calling registers a listener,
        // post fires it; the JS object passed to post becomes a PolyglotMap through as(Object.class)
        // and is read back by property in JS)
        Value bus = serverBindings.getMember("MyEvents").getMember("bossKilled");
        bus.execute(context.eval("js",
                "(payload) => recorder.ledger(payload ? payload.boss : null)"));
        bus.getMember("post").execute(context.eval("js", "({ boss: 'ender_dragon' })"));
        assertEquals("ender_dragon", recorder.lastBoss,
                "declared dynamic event delivers listen + post end to end");

        try (Context clientContext = Context.newBuilder("js").allowAllAccess(true).build()) {
            ScriptContextRegistryBridge.bind(clientContext);
            Value clientBindings = clientContext.getBindings("js");
            bridge.bindEvents(clientBindings, ScriptType.CLIENT);
            assertFalse(clientBindings.hasMember("MyEvents"),
                    "SERVER-target dynamic group must not be bound in the CLIENT environment");
            ScriptContextRegistryBridge.unbind(clientContext);
        }
    }

    // ---- TS and Python declaration parity: one shared catalog derivation path ----

    @Test
    void runtimeMembersAndDeclarationsDeriveFromTheSameCatalogEntries() {
        scriptEvents.register(ScriptType.SERVER, "MyEvents", "bossKilled", "scripts/a.js");
        List<EventCatalogEntry> entries = NekoScriptCatalog.events(runtime);
        assertEquals(List.of("MyEvents.bossKilled"), entries.stream()
                .map(e -> e.group() + "." + e.name()).toList());
        assertTrue(entries.getFirst().scriptDefined());

        // The same entry list drives the TS declarations
        TypeAliasRegistry aliases = new TypeAliasRegistry();
        String ts = new EventDeclarationGenerator(aliases, new AdapterAliasGenerator(aliases))
                .generate(entries, ScriptType.SERVER);
        // The same entry list drives the Python declarations
        String py = new PythonEventRenderer(new ApiTypeRefPyRenderer(Set.of()), Set.of())
                .render(ScriptType.SERVER, entries, null);

        // TS: an any payload, and post exactly once
        assertEquals(1, countOccurrences(ts, "function bossKilled(handler: ((payload: any) => void)): void;"),
                "TS declares the script event listener exactly once");
        assertEquals(1, countOccurrences(ts, "function post(payload?: any): void;"),
                "TS declares the script event post member exactly once");
        assertFalse(ts.contains("$Object"), "no java.lang.Object leak as payload");

        // Python: an Any payload and post exactly once, derived from the same catalog entry as TS
        assertEquals(1, countOccurrences(py,
                "def __call__(self, handler: Callable[[Any], None]) -> None: ..."),
                "Python declares the script event listener exactly once");
        assertEquals(1, countOccurrences(py,
                "def post(self, payload: Any = ...) -> None: ..."),
                "Python declares the script event post member exactly once (parity with TS/runtime)");
        assertTrue(py.contains("bossKilled"), "Python keeps the runtime member name");
    }

    private static int countOccurrences(String haystack, String needle) {
        int count = 0;
        int index = 0;
        while ((index = haystack.indexOf(needle, index)) != -1) {
            count++;
            index += needle.length();
        }
        return count;
    }

    // ---- Script-side ledger (records listener callbacks) ----

    public static final class Recorder {
        public volatile String lastBoss;

        public void ledger(String boss) {
            lastBoss = boss;
        }
    }

    // ---- Test infrastructure ----

    /** Narrow wrapper over ScriptContextRegistry, so the test class does not import the internal registry. */
    private static final class ScriptContextRegistryBridge {
        static void bind(Context ctx) {
            com.tkisor.nekojs.script.ScriptContextRegistry.bind(ctx, ScriptType.SERVER);
        }

        static void unbind(Context ctx) {
            com.tkisor.nekojs.script.ScriptContextRegistry.unbind(ctx);
        }
    }

    private static final class StubRuntime implements IPluginRuntime {
        final Map<String, Binding> bindings = new java.util.HashMap<>();
        final Map<String, EventGroup> eventGroups = new java.util.LinkedHashMap<>();

        @Override
        public Map<String, Binding> bindings(ScriptType type) {
            return bindings;
        }

        @Override
        public Map<String, EventGroup> eventGroups() {
            return eventGroups;
        }

        @Override
        public List<JSTypeAdapter<?>> adapters() {
            return List.of();
        }

        @Override
        public List<TypeDocCatalogEntry> typeDocs() {
            return List.of();
        }

        @Override
        public List<ManualDeclarationCatalogEntry> manualDeclarations() {
            return List.of();
        }

        @Override
        public Map<String, String> nodeModules() {
            return Map.of();
        }

        @Override
        public Map<String, RecipeNamespaceEntry> recipeNamespaces() {
            return Map.of();
        }

        @Override
        public void beforeRecipeLoading(RecipeLifecycleContext context) {}

        @Override
        public void afterRecipes(RecipeLifecycleContext context) {}

        @Override
        public void fireInit() {}

        @Override
        public void fireInitStartup() {}

        @Override
        public void fireAfterInit() {}

        @Override
        public void fireBeforeScriptsLoaded(ScriptType type) {}

        @Override
        public void fireAfterScriptsLoaded(ScriptType type) {}

        @Override
        public ApiRuntimeView apiRuntime(EnvironmentKey environment) {
            return null;
        }

        @Override
        public Object managedApiImplementation(ApiSymbolId globalId) {
            return null;
        }
    }
}
