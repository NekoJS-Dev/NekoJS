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
import com.tkisor.nekojs.core.ScriptFilePolicy;
import com.tkisor.nekojs.core.api.ApiModuleInstaller;
import com.tkisor.nekojs.core.compiler.ScriptCompilerRegistry;
import com.tkisor.nekojs.core.config.SandboxConfig;
import com.tkisor.nekojs.core.error.DefaultErrorTracker;
import com.tkisor.nekojs.core.fs.NekoJSFileSystem;
import com.tkisor.nekojs.core.fs.NekoJSPaths;
import com.tkisor.nekojs.core.fs.SandboxPolicy;
import com.tkisor.nekojs.core.module.NekoModulePipelineCache;
import com.tkisor.nekojs.core.module.NekoModuleResolver;
import com.tkisor.nekojs.core.node.NekoNodeModuleInstaller;
import com.tkisor.nekojs.core.node.NekoNodeRuntime;
import com.tkisor.nekojs.testfixture.NekoModuleTestFixtures;
import com.tkisor.nekojs.script.ScriptContextRegistry;
import com.tkisor.nekojs.testfixture.TestPlatformInit;
import graal.graalvm.polyglot.Context;
import graal.graalvm.polyglot.io.IOAccess;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Runs native registrar import, dynamic declaration, listening, and post through real guest contexts. */
class ScriptEventsMinimalExampleTest {

    /** Startup side: declares the named event groups. */
    static final String STARTUP_EXAMPLE = """
            import { ScriptEvents } from "nekojs:event/versioned";
            ScriptEvents.server(event => event.register('MyEvents', 'bossKilled'));
            ScriptEvents.client(event => event.register('HudEvents', 'cooldownEnded'));
            """;

    /** Server side: listens, then fires the event from the script itself. */
    static final String SERVER_EXAMPLE = """
            // Script-defined dynamic groups remain global subscription namespaces.
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
    private NekoNodeRuntime startupRuntime;
    private NekoModulePipelineCache cache;
    private Path scriptDir;

    @BeforeAll
    static void initPlatform() {
        TestPlatformInit.ensureInitialized();
    }

    @BeforeEach
    void setUp() throws Exception {
        clearAllDefinitions();
        runtime = new StubRuntime();
        runtime.events.scopedTo("nekojs").register(ScriptEvents.GROUP);
        scriptEvents = new ScriptEventsJS();
        scriptEvents.bindRuntime(runtime);
        bridge = new DefaultScriptEventBridge(scriptEvents);
        bridge.setPluginRuntime(runtime);

        NekoJSPaths paths = NekoJSPaths.get();
        SandboxConfig config = SandboxConfig.defaultConfig();
        ScriptCompilerRegistry compilers = ScriptCompilerRegistry.createRuntimeRegistry();
        cache = NekoModuleTestFixtures.newCache(paths, compilers, config);
        Files.createDirectories(paths.startupScripts());
        scriptDir = Files.createTempDirectory(paths.startupScripts(), "script-events-example-");
        IOAccess io = IOAccess.newBuilder().fileSystem(new NekoJSFileSystem(paths.root(),
                new SandboxPolicy(config, paths), paths, cache)).build();
        startupContext = Context.newBuilder("js").allowAllAccess(true).allowIO(io).build();
        startupRuntime = NekoNodeModuleInstaller.install(startupContext, ScriptType.STARTUP,
                new NekoModuleResolver(paths.gameDir(), paths.root(), paths.nodeModules(), new ScriptFilePolicy(compilers)),
                paths, new DefaultErrorTracker(paths, config), config, cache);
        ApiModuleInstaller.install(startupContext, runtime, ScriptType.STARTUP);
        ScriptContextRegistry.bind(startupContext, ScriptType.STARTUP);
        serverContext = Context.newBuilder("js").allowAllAccess(true).build();
        ScriptContextRegistry.bind(serverContext, ScriptType.SERVER);
    }

    @AfterEach
    void tearDown() throws Exception {
        try {
            if (bridge != null) bridge.clearListeners(ScriptType.STARTUP);
            clearAllDefinitions();
            ScriptContextRegistry.unbind(startupContext);
            ScriptContextRegistry.unbind(serverContext);
        } finally {
            try {
                if (startupRuntime != null) startupRuntime.close();
            } finally {
                try {
                    if (startupContext != null) startupContext.close();
                } finally {
                    try {
                        if (serverContext != null) serverContext.close();
                    } finally {
                        if (cache != null) cache.close();
                        if (scriptDir != null) {
                            try (var files = Files.walk(scriptDir)) {
                                for (Path path : files.sorted(java.util.Comparator.reverseOrder()).toList()) Files.delete(path);
                            }
                        }
                    }
                }
            }
        }
    }

    private static void clearAllDefinitions() {
        for (ScriptType target : ScriptType.all()) {
            ScriptEventRegistry.clearDefinitions(target);
        }
    }

    @Test
    void minimalExampleRunsTheDeclareListenPostChain() throws Exception {
        // STARTUP imports the native registrar bus instead of installing a global alias.
        bridge.bindEvents(startupContext.getBindings("js"), ScriptType.STARTUP);
        Path entry = Files.writeString(scriptDir.resolve("events.mjs"), STARTUP_EXAMPLE);
        startupRuntime.moduleLoaderHost().loadEntry(entry.toString());
        assertEquals(false, startupContext.getBindings("js").hasMember("ScriptEvents"));

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
        final EventGroupRegistry.Impl events = new EventGroupRegistry.Impl();

        @Override public Map<String, Binding> bindings(ScriptType type) { return Map.of(); }
        @Override public Map<String, EventGroup> eventGroups() { return events.view(); }
        @Override public List<ModuleEventGroup> moduleEventGroups() { return events.moduleGroups(); }
        @Override public List<EventGroup> lifecycleEventGroups() { return events.sources(); }
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
