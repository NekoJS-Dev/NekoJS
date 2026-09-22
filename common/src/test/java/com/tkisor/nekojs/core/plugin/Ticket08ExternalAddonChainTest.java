package com.tkisor.nekojs.core.plugin;

import com.tkisor.nekojs.api.NekoJSPlugin;
import com.tkisor.nekojs.api.ScriptType;
import com.tkisor.nekojs.api.annotation.RegisterNekoJSPlugin;
import com.tkisor.nekojs.api.contract.VerifiedContractSet;
import com.tkisor.nekojs.api.data.BindingRegistry;
import com.tkisor.nekojs.api.plugin.OwnedPlugin;
import com.tkisor.nekojs.api.plugin.PluginIdentity;
import com.tkisor.nekojs.core.NekoCoreContext;
import com.tkisor.nekojs.core.NekoJSBasePluginManager;
import com.tkisor.nekojs.core.NekoSandboxFactory;
import com.tkisor.nekojs.core.ScriptEventBridge;
import com.tkisor.nekojs.core.compiler.ScriptCompilerRegistry;
import com.tkisor.nekojs.core.config.SandboxConfig;
import com.tkisor.nekojs.core.error.DefaultErrorTracker;
import com.tkisor.nekojs.core.fs.ClassFilter;
import com.tkisor.nekojs.core.fs.NekoJSPaths;
import com.tkisor.nekojs.core.module.NekoModulePipelineCache;
import com.tkisor.nekojs.core.villager.VillagerTradeDomainState;
import com.tkisor.nekojs.script.ScriptEnvironmentFactory;
import com.tkisor.nekojs.script.ScriptManager;
import com.tkisor.nekojs.script.prop.ScriptPropertyRegistry;
import com.tkisor.nekojs.testfixture.NekoModuleTestFixtures;
import com.tkisor.nekojs.testfixture.TestPlatformInit;
import graal.graalvm.polyglot.Context;
import graal.graalvm.polyglot.Engine;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Ticket 08: the real external addon chain — jar discovery through the production
 * seam, contribution via the existing Point/Hook model, frozen-product consumption
 * through both an Extension Handle and the script-visible binding, bootstrap-once
 * survival across an ordinary script reload, and generation-token invalidation.
 *
 * <p>All fixture classes live in a dedicated {@link URLClassLoader} world over the
 * test-only addon jar; nothing here instantiates the addon internally.
 */
class Ticket08ExternalAddonChainTest {

    @BeforeAll
    static void initPlatform() {
        TestPlatformInit.ensureInitialized();
    }

    // =====================================================================================
    // Discovery: jar/mods-dir shape and fabric.mod.json shape through the production seam
    // =====================================================================================

    @Test
    void jarDiscoveryRegistersOnlyEligiblePluginsWithJarOwnerIdentity() throws Exception {
        try (ExternalAddonFixture.ManagerEntriesSnapshot ignored = new ExternalAddonFixture.ManagerEntriesSnapshot();
             URLClassLoader loader = ExternalAddonFixture.openJarLoader()) {
            ExternalAddonFixture.assertNotOnEngineClassPath();
            List<Class<?>> annotated = ExternalAddonFixture.scanAnnotatedPluginClasses(loader);
            assertEquals(4, annotated.size(), "fixture jar carries four annotated plugin classes");
            ExternalAddonFixture.registerDiscovered(annotated);

            List<NekoJSPlugin> plugins = NekoJSBasePluginManager.getPlugins();
            List<String> names = plugins.stream().map(p -> p.getClass().getName()).toList();
            // clientOnly (test platform is dedicated-server shaped) and missing requiredMods
            // are filtered by the production rules; priority 1200 sorts the main addon first
            assertEquals(List.of(
                    ExternalAddonFixture.MAIN_PLUGIN,
                    ExternalAddonFixture.SECONDARY_PLUGIN), names,
                    "discovery must filter clientOnly/requiredMods and order by priority");

            // owner identity comes from the protection domain = the real fixture jar
            List<OwnedPlugin> owned = NekoJSBasePluginManager.getOwnedPlugins();
            assertEquals(2, owned.size());
            String codeSource = owned.get(0).identity().codeSource().toString();
            assertTrue(codeSource.contains("nekojs-external-addon-fixture"),
                    "owner code source must point at the discovered jar, got: " + codeSource);

            // a second, clean discovery round over the same jar input is idempotent
            ExternalAddonFixture.registerDiscovered(annotated);
            assertEquals(2, NekoJSBasePluginManager.getPlugins().size(),
                    "duplicate (identity, class) discovery registers exactly once");
        }
    }

    @Test
    void fabricModJsonEntrypointDiscoveryRegistersTheMainPlugin() throws Exception {
        try (ExternalAddonFixture.ManagerEntriesSnapshot ignored = new ExternalAddonFixture.ManagerEntriesSnapshot();
             URLClassLoader loader = ExternalAddonFixture.openJarLoader()) {
            List<String> entrypoints = ExternalAddonFixture.fabricNekojsEntrypoints();
            assertEquals(List.of(ExternalAddonFixture.MAIN_PLUGIN), entrypoints,
                    "fabric.mod.json declares the nekojs entrypoint");
            for (String entrypoint : entrypoints) {
                NekoJSBasePluginManager.registerClass(Class.forName(entrypoint, false, loader));
            }
            assertEquals(List.of(ExternalAddonFixture.MAIN_PLUGIN),
                    NekoJSBasePluginManager.getPlugins().stream()
                            .map(p -> p.getClass().getName()).toList());
        }
    }

    @Test
    void fixtureJarCarriesProductionLoaderMetadata() throws Exception {
        String toml = ExternalAddonFixture.neoforgeModsToml();
        assertTrue(toml.contains("modId = \"exampleaddon\""),
                "neoforge.mods.toml must carry the production mod id");
    }

    // =====================================================================================
    // Contribution + frozen product: Handle/result and script binding observe the same data
    // =====================================================================================

    @Test
    void addonContributesThroughHookAndProviderAndFreezesOneProduct() throws Exception {
        try (ExternalAddonFixture.ManagerEntriesSnapshot ignored = new ExternalAddonFixture.ManagerEntriesSnapshot();
             URLClassLoader loader = ExternalAddonFixture.openJarLoader()) {
            ExternalAddonFixture.registerDiscovered(
                    ExternalAddonFixture.scanAnnotatedPluginClasses(loader));
            NekoJSBasePluginManager.registerClass(ChainRecorderPlugin.class);
            NekoJSBasePluginManager.registerClass(ChainContributorPlugin.class);

            NekoPluginRuntime runtime = NekoPluginRuntime.bootstrapOwned(
                    NekoJSBasePluginManager.getOwnedPlugins(),
                    new ScriptPropertyRegistry.Impl(),
                    com.tkisor.nekojs.testfixture.CoreContractPreviews.emptyPortablePreview());

            // custom point product via runtime result container
            Object greetings = runtime.extensionProduct("exampleaddon:greetings", Object.class);
            assertNotNull(greetings);
            assertEquals(List.of("primary", "secondary"),
                    ExternalAddonFixture.call(greetings, "greetings"),
                    "custom point collects every greeter plugin in priority order");
            // the initializer read the FINISHED nekojs:bindings product (declared dependsOnId):
            // SERVER bindings = ExampleAddon (addon hook) + ChainRecorder (hook form)
            // + ChainContributor (explicit Contributor form)
            assertEquals(3, ExternalAddonFixture.call(greetings, "serverBindingCount"),
                    "initializer observed the frozen nekojs:bindings product");

            // Hook projection and explicit Contributor form land in the same frozen map
            Map<String, com.tkisor.nekojs.api.data.Binding> serverBindings = runtime.bindings(ScriptType.SERVER);
            assertTrue(serverBindings.containsKey("ExampleAddon"), "addon hook binding collected");
            assertTrue(serverBindings.containsKey("ChainRecorder"), "test plugin hook binding collected");
            assertTrue(serverBindings.containsKey("ChainContributor"),
                    "explicit BindingsPoint.Contributor form collected identically");

            // Extension Handle returns the same frozen product object
            Object handle = handleOf(loader);
            assertTrue((boolean) ExternalAddonFixture.call(handle, "isFinished"));
            assertSame(greetings, ExternalAddonFixture.call(handle, "result"),
                    "Handle result and runtime product are the same frozen object");

            // script-visible binding holds the same frozen surface instance
            Object surface = ExternalAddonFixture.surfaceOf(loader);
            assertSame(surface, serverBindings.get("ExampleAddon").value(),
                    "binding value and addon surface are the same instance");
            assertEquals("exampleaddon-frozen-product", ExternalAddonFixture.call(surface, "marker"));
            assertEquals("greetings=[primary, secondary], serverBindings=3",
                    ExternalAddonFixture.call(surface, "publishedSummary"));
        }
    }

    /** The explicit Contributor form is collected by the same built-in point as the hook. */
    public static final class ChainContributorPlugin implements BindingsPoint.Contributor {
        @Override
        public void registerBinding(BindingRegistry registry) {
            registry.register(ScriptType.SERVER, "ChainContributor", "contributor-form");
        }
    }

    /** Hook-form twin of {@link ChainContributorPlugin} plus the script-side recorder binding. */
    @RegisterNekoJSPlugin(priority = 500)
    public static final class ChainRecorderPlugin implements NekoJSPlugin {
        public static final ChainRecorder RECORDER = new ChainRecorder();

        @Override
        public void registerBinding(BindingRegistry registry) {
            registry.register(ScriptType.SERVER, "ChainRecorder", RECORDER);
        }
    }

    /** Java-side recorder bound into scripts to observe generation behavior. */
    public static final class ChainRecorder {
        public final List<Object> remembered = new CopyOnWriteArrayList<>();
        public final List<String> notes = new CopyOnWriteArrayList<>();

        public void remember(Object surface) {
            remembered.add(surface);
        }

        public void note(String value) {
            notes.add(value);
        }
    }

    // =====================================================================================
    // Handle read boundaries and accumulator sealing
    // =====================================================================================

    @Test
    void handleRejectsReadsBeforeFinishAndSkippedPointsStayUnpublished() {
        AtomicReference<NekoPluginExtensionHandle<Object>> captured = new AtomicReference<>();
        NekoPluginExtensionProvider provider = registry -> {
            // enabled=false → the point is skipped → its handle never finishes
            captured.set(registry.register(NekoPluginExtensionPoint
                    .<NekoJSPlugin, Object, Object>builder("t08:skipped", NekoJSPlugin.class)
                    .merge(MergePolicy.append())
                    .enabledWhen(context -> false)
                    .initializer(context -> new Object())
                    .collector((plugin, acc) -> { })
                    .finish(acc -> acc)
                    .build()));
        };
        NekoPluginBootstrap.bootstrap(List.of(provider), new ScriptPropertyRegistry.Impl());
        NekoPluginExtensionHandle<Object> handle = captured.get();
        assertFalse(handle.isFinished());
        IllegalStateException ex = assertThrows(IllegalStateException.class, handle::result);
        assertTrue(ex.getMessage().contains("t08:skipped"), "error names the point: " + ex.getMessage());
    }

    @Test
    void sealedAccumulatorRejectsPostFinishWritesAndLateBindingWritesDoNotLeak() throws Exception {
        try (ExternalAddonFixture.ManagerEntriesSnapshot ignored = new ExternalAddonFixture.ManagerEntriesSnapshot();
             URLClassLoader loader = ExternalAddonFixture.openJarLoader()) {
            ExternalAddonFixture.registerDiscovered(
                    ExternalAddonFixture.scanAnnotatedPluginClasses(loader));
            NekoJSBasePluginManager.registerClass(HookCapturePlugin.class);

            NekoPluginRuntime runtime = NekoPluginRuntime.bootstrapOwned(
                    NekoJSBasePluginManager.getOwnedPlugins(),
                    new ScriptPropertyRegistry.Impl(),
                    com.tkisor.nekojs.testfixture.CoreContractPreviews.emptyPortablePreview());

            // registry-graph freeze: the sealed accumulator rejects further collection
            Object accumulator = accumulatorOf(loader);
            assertEquals(2, ((List<?>) accumulator).size(), "two greeter plugins were collected");
            IllegalStateException sealed = assertThrows(IllegalStateException.class,
                    () -> ExternalAddonFixture.call(accumulator, "add", "late"));
            assertTrue(sealed.getMessage().contains("sealed"),
                    "post-finish collection must be rejected: " + sealed.getMessage());
            // published result keeps the frozen snapshot: registry freeze, accumulator seal and
            // result publication are three distinct boundaries
            assertEquals(List.of("primary", "secondary"),
                    ExternalAddonFixture.call(
                            runtime.extensionProduct("exampleaddon:greetings", Object.class), "greetings"));

            // bindings accumulator is not Sealable: a late write may mutate the accumulator
            // but must not change the already-published frozen product
            Map<String, com.tkisor.nekojs.api.data.Binding> frozen = runtime.bindings(ScriptType.SERVER);
            int before = frozen.size();
            HookCapturePlugin.SERVER_REGISTRY.get().register("LateBinding", "late-value");
            assertEquals(before, runtime.bindings(ScriptType.SERVER).size(),
                    "late accumulator write does not affect the published frozen bindings");
            assertFalse(runtime.bindings(ScriptType.SERVER).containsKey("LateBinding"));
        }
    }

    /** Hook-form plugin that captures the SERVER BindingRegistry handed to its collector. */
    public static final class HookCapturePlugin implements NekoJSPlugin {
        static final AtomicReference<BindingRegistry> SERVER_REGISTRY = new AtomicReference<>();

        @Override
        public void registerBinding(BindingRegistry registry) {
            if (registry.scriptType() == ScriptType.SERVER) {
                SERVER_REGISTRY.set(registry);
            }
        }
    }

    // =====================================================================================
    // Reload survival: bootstrap/freeze exactly once, Handle stays readable, tokens go stale
    // =====================================================================================

    @Test
    void ordinaryReloadKeepsPluginRuntimeFrozenAndInvalidatesGenerationTokens() throws Exception {
        NekoJSPaths paths = NekoJSPaths.get();
        Path serverDir = paths.serverScripts();
        List<Path> restore = isolateServerScriptsDir(serverDir);
        Engine engine = Engine.newBuilder().build();
        ScriptManager manager = null;
        try (ExternalAddonFixture.ManagerEntriesSnapshot ignored = new ExternalAddonFixture.ManagerEntriesSnapshot();
             URLClassLoader loader = ExternalAddonFixture.openJarLoader()) {
            Files.createDirectories(serverDir);
            Path script = serverDir.resolve("t08_addon_chain.js");
            Files.deleteIfExists(script);
            Files.writeString(script, """
                    ChainRecorder.remember(ExampleAddon);
                    ChainRecorder.note(ExampleAddon.marker() + '/init=' + ExampleAddon.initCalls());
                    """);

            ExternalAddonFixture.registerDiscovered(
                    ExternalAddonFixture.scanAnnotatedPluginClasses(loader));
            NekoJSBasePluginManager.registerClass(ChainRecorderPlugin.class);
            ScriptPropertyRegistry properties = scriptPropertiesWithFileRules();
            NekoPluginRuntime runtime = NekoPluginRuntime.bootstrapOwned(
                    NekoJSBasePluginManager.getOwnedPlugins(),
                    properties,
                    com.tkisor.nekojs.testfixture.CoreContractPreviews.emptyPortablePreview());
            // NekoRuntimeAssembly.assemble fires plugin init() right after bootstrapOwned;
            // this harness assembles the manager directly, so the same lifecycle step runs here
            runtime.fireInit();

            // full engine assembly around the real plugin runtime (same shape as the
            // ScriptReloadRegressionTest harness, but with the real bootstrap result)
            ScriptCompilerRegistry compilers = ScriptCompilerRegistry.createRuntimeRegistry();
            SandboxConfig config = new SandboxConfig(false, false, false, false, true, true,
                    false, true, 30, 100_000L, 0);
            DefaultErrorTracker tracker = new DefaultErrorTracker(paths, config);
            NekoCoreContext core = new NekoCoreContext(engine, config, new ClassFilter(config), tracker);
            NekoModulePipelineCache cache = NekoModuleTestFixtures.newCache(paths, compilers, config);
            NekoSandboxFactory sandboxFactory = new NekoSandboxFactory(core, paths, compilers, runtime, cache);
            ScriptEnvironmentFactory environmentFactory = new ScriptEnvironmentFactory(
                    ScriptEventBridge.EMPTY, runtime, sandboxFactory,
                    new com.tkisor.nekojs.core.state.GlobalStateStores());
            manager = new ScriptManager(ScriptType.SERVER, ScriptEventBridge.EMPTY, runtime,
                    properties, tracker, paths, config, environmentFactory,
                    List.of(), cache);

            manager.discoverScripts();
            manager.loadScripts();

            long generation1 = manager.generationId();
            Object surface = ExternalAddonFixture.surfaceOf(loader);
            Object frozenGreetings = runtime.extensionProduct("exampleaddon:greetings", Object.class);
            assertEquals(1, ExternalAddonFixture.call(surface, "initCalls"),
                    "plugin bootstrap ran exactly once at assembly");
            assertEquals(List.of("exampleaddon-frozen-product/init=1"),
                    ChainRecorderPlugin.RECORDER.notes);

            Context generation1Context = currentContext(manager);
            VillagerTradeDomainState domainState = new VillagerTradeDomainState("t08-test", true);

            // ---- ordinary reload: script generation switches, plugin runtime does not ----
            manager.reloadScripts();
            long generation2 = manager.generationId();
            assertTrue(generation2 > generation1, "script generation advanced");

            assertEquals(1, ExternalAddonFixture.call(surface, "initCalls"),
                    "ordinary reload must not re-bootstrap the plugin runtime");
            assertEquals(1, ExternalAddonFixture.call(surface, "registrationCalls"),
                    "extension registry freeze happened once");
            assertEquals(0, ExternalAddonFixture.call(surface, "closeCalls"),
                    "session cleanup must not close process-level plugin products");
            assertSame(frozenGreetings,
                    ExternalAddonFixture.call(handleOf(loader), "result"),
                    "Handle stays readable with the same frozen product");
            assertSame(surface, runtime.bindings(ScriptType.SERVER).get("ExampleAddon").value(),
                    "Point result and Handle identity stay valid");
            assertEquals(2, ChainRecorderPlugin.RECORDER.notes.size(),
                    "the new generation executed the script exactly once more");
            assertEquals("exampleaddon-frozen-product/init=1",
                    ChainRecorderPlugin.RECORDER.notes.get(1),
                    "the new generation reads the same frozen binding value");
            assertSame(surface, ChainRecorderPlugin.RECORDER.remembered.get(0),
                    "generation 1 saw the frozen surface");
            assertSame(surface, ChainRecorderPlugin.RECORDER.remembered.get(1),
                    "generation 2 saw the same frozen surface instance");

            // ---- old generation session objects cannot operate the new session ----
            Context generation2Context = currentContext(manager);
            assertEquals(-1L, ScriptManager.activeGenerationOf(generation1Context),
                    "old generation Context is no longer active");
            assertEquals(generation2, ScriptManager.activeGenerationOf(generation2Context));
            Object staleQuery = domainState.query(generation1Context);
            assertEquals("STALE", String.valueOf(ExternalAddonFixture.call(staleQuery, "getStatus")),
                    "old generation token must not silently read the new session");
            assertTrue(String.valueOf(ExternalAddonFixture.call(staleQuery, "getStatusReason"))
                            .contains("generation-not-active"),
                    "failure must name the stale generation, got: "
                            + ExternalAddonFixture.call(staleQuery, "getStatusReason"));
            Files.deleteIfExists(script);
        } finally {
            if (manager != null) {
                manager.close();
            }
            engine.close();
            restoreForeignScripts(serverDir, restore);
        }
    }

    // =====================================================================================
    // Addon-level failure outputs: failures name the addon that caused them
    // =====================================================================================

    @Test
    void duplicateExtensionPointIdNamesBothAddons() throws Exception {
        String first = "owner://t08/first";
        String second = "owner://t08/second";
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> NekoPluginRuntime.bootstrapOwned(List.of(
                        owned(first, provider(registry -> registerPoint(registry, "t08:dup"))),
                        owned(second, provider(registry -> registerPoint(registry, "t08:dup")))),
                        new ScriptPropertyRegistry.Impl(),
                        com.tkisor.nekojs.testfixture.CoreContractPreviews.emptyPortablePreview()));
        assertTrue(ex.getMessage().contains("t08:dup"), "names the point: " + ex.getMessage());
        assertTrue(ex.getMessage().contains(first), "names the first registrant: " + ex.getMessage());
        assertTrue(ex.getMessage().contains(second), "names the duplicate registrant: " + ex.getMessage());
    }

    @Test
    void lateRegistrationAfterFreezeNamesTheAddon() throws Exception {
        AtomicReference<NekoPluginExtensionRegistry> captured = new AtomicReference<>();
        NekoPluginRuntime.bootstrapOwned(
                List.of(owned("owner://t08/late", provider(captured::set))),
                new ScriptPropertyRegistry.Impl(),
                com.tkisor.nekojs.testfixture.CoreContractPreviews.emptyPortablePreview());
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> registerPoint(captured.get(), "t08:after-freeze"));
        assertTrue(ex.getMessage().contains("t08:after-freeze"), "names the point: " + ex.getMessage());
        assertTrue(ex.getMessage().contains("owner://t08/late"),
                "names the addon whose registration window closed: " + ex.getMessage());
    }

    @Test
    void unknownDependencyNamesTheDependentAddon() throws Exception {
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> NekoPluginRuntime.bootstrapOwned(
                        List.of(owned("owner://t08/typo", provider(registry -> registry.register(
                                NekoPluginExtensionPoint
                                        .<NekoJSPlugin, List<String>, List<String>>builder(
                                                "t08:typo-point", NekoJSPlugin.class)
                                        .merge(MergePolicy.append())
                                        .dependsOnId("t08:does-not-exist")
                                        .initializer(context -> new ArrayList<String>())
                                        .collector((plugin, acc) -> { })
                                        .finish(List::copyOf)
                                        .build())))),
                        new ScriptPropertyRegistry.Impl(),
                        com.tkisor.nekojs.testfixture.CoreContractPreviews.emptyPortablePreview()));
        assertTrue(ex.getMessage().contains("t08:does-not-exist"), "names the missing id: " + ex.getMessage());
        assertTrue(ex.getMessage().contains("owner://t08/typo"),
                "names the dependent addon: " + ex.getMessage());
    }

    @Test
    void dependencyCycleNamesBothAddons() throws Exception {
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> NekoPluginRuntime.bootstrapOwned(List.of(
                        owned("owner://t08/a", provider(registry -> registerPoint(registry, "t08:cycle-a", "t08:cycle-b"))),
                        owned("owner://t08/b", provider(registry -> registerPoint(registry, "t08:cycle-b", "t08:cycle-a")))),
                        new ScriptPropertyRegistry.Impl(),
                        com.tkisor.nekojs.testfixture.CoreContractPreviews.emptyPortablePreview()));
        assertTrue(ex.getMessage().contains("cycle"), "reports the cycle: " + ex.getMessage());
        assertTrue(ex.getMessage().contains("owner://t08/a") && ex.getMessage().contains("owner://t08/b"),
                "names both addons on the cycle: " + ex.getMessage());
    }

    // ---- helpers -----------------------------------------------------------------------

    /** File-header properties the ScriptManager's load-order sorter expects to be registered. */
    private static ScriptPropertyRegistry scriptPropertiesWithFileRules() {
        var impl = new ScriptPropertyRegistry.Impl();
        impl.register(com.tkisor.nekojs.script.prop.ScriptProperty.AFTER);
        impl.register(com.tkisor.nekojs.script.prop.ScriptProperty.MODLOADED);
        impl.register(com.tkisor.nekojs.script.prop.ScriptProperty.DISABLE);
        impl.register(com.tkisor.nekojs.script.prop.ScriptProperty.PRIORITY);
        impl.freeze();
        return impl;
    }

    private static NekoPluginExtensionProvider provider(
            java.util.function.Consumer<NekoPluginExtensionRegistry> body) {
        return body::accept;
    }

    private static void registerPoint(NekoPluginExtensionRegistry registry, String id, String... dependsOn) {
        registry.register(NekoPluginExtensionPoint
                .<NekoJSPlugin, List<String>, List<String>>builder(id, NekoJSPlugin.class)
                .merge(MergePolicy.append())
                .dependsOnId(dependsOn)
                .initializer(context -> new ArrayList<String>())
                .collector((plugin, acc) -> { })
                .finish(List::copyOf)
                .build());
    }

    private static OwnedPlugin owned(String ownerId, NekoJSPlugin plugin) {
        return new OwnedPlugin(new PluginIdentity(ownerId, plugin.getClass().getName(),
                java.net.URI.create(ownerId)), plugin);
    }

    private static Object handleOf(URLClassLoader loader) throws Exception {
        Object reference = loader.loadClass(ExternalAddonFixture.MAIN_PLUGIN)
                .getField("GREETINGS_HANDLE").get(null);
        return ((AtomicReference<?>) reference).get();
    }

    private static Object accumulatorOf(URLClassLoader loader) throws Exception {
        Object reference = loader.loadClass(ExternalAddonFixture.MAIN_PLUGIN)
                .getField("LAST_ACCUMULATOR").get(null);
        return ((AtomicReference<?>) reference).get();
    }

    private static Context currentContext(ScriptManager manager) throws Exception {
        Field field = ScriptManager.class.getDeclaredField("runtime");
        field.setAccessible(true);
        Object environment = field.get(manager);
        Method contextAccessor = environment.getClass().getDeclaredMethod("context");
        contextAccessor.setAccessible(true);
        return (Context) contextAccessor.invoke(environment);
    }

    /** Removes foreign script files so the reload run directory is clean; returns what to restore. */
    private static List<Path> isolateServerScriptsDir(Path serverDir) throws Exception {
        List<Path> moved = new ArrayList<>();
        if (Files.isDirectory(serverDir)) {
            try (var stream = Files.list(serverDir)) {
                for (Path file : stream.filter(Files::isRegularFile).toList()) {
                    Path parking = file.resolveSibling(file.getFileName() + ".t08-parked");
                    // stale parking leftovers of an aborted run would break the move
                    Files.deleteIfExists(parking);
                    Files.move(file, parking);
                    moved.add(parking);
                }
            }
        }
        return moved;
    }

    private static void restoreForeignScripts(Path serverDir, List<Path> parked) throws Exception {
        // remove this test's own script first so restoring foreign files cannot collide
        Files.deleteIfExists(serverDir.resolve("t08_addon_chain.js"));
        for (Path parking : parked) {
            if (!Files.exists(parking)) {
                continue; // already restored (aborted earlier run) — nothing to do
            }
            Files.move(parking, parking.resolveSibling(
                    parking.getFileName().toString().replace(".t08-parked", "")));
        }
    }
}
