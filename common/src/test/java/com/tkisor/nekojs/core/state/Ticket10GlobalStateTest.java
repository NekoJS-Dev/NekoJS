package com.tkisor.nekojs.core.state;

import com.tkisor.nekojs.api.ScriptType;
import com.tkisor.nekojs.api.data.Binding;
import com.tkisor.nekojs.api.JSTypeAdapter;
import com.tkisor.nekojs.api.plugin.IPluginRuntime;
import com.tkisor.nekojs.core.ScriptEventBridge;
import com.tkisor.nekojs.core.NekoCoreContext;
import com.tkisor.nekojs.core.NekoSandboxFactory;
import com.tkisor.nekojs.core.compiler.ScriptCompilerRegistry;
import com.tkisor.nekojs.core.config.SandboxConfig;
import com.tkisor.nekojs.core.error.DefaultErrorTracker;
import com.tkisor.nekojs.core.fs.ClassFilter;
import com.tkisor.nekojs.core.fs.NekoJSPaths;
import com.tkisor.nekojs.core.lifecycle.NekoReloadException;
import com.tkisor.nekojs.core.lifecycle.NekoRuntimeRoot;
import com.tkisor.nekojs.core.module.NekoModulePipelineCache;
import com.tkisor.nekojs.core.lifecycle.ReloadPhase;
import com.tkisor.nekojs.script.ScriptEnvironmentFactory;
import com.tkisor.nekojs.script.ScriptManager;
import com.tkisor.nekojs.script.ScriptTypeEnv;
import com.tkisor.nekojs.script.prop.ScriptPropertyRegistry;
import com.tkisor.nekojs.script.prop.ScriptProperty;
import com.tkisor.nekojs.testfixture.TestPlatformInit;
import graal.graalvm.polyglot.Context;
import graal.graalvm.polyglot.Engine;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers the global and shared candidate write set: the backing store per ScriptType owned by
 * the root, the explicit shared entry, candidate write sets (read-your-writes, discard on
 * failure, conflict detection, joint commit), state retention across the root lifetime, guest
 * Value expiry, the split between global and globalThis, and the old cross-type migration.
 *
 * <p>Assertions go through public APIs: the observable results of script eval and reload, the
 * root-owned {@link GlobalStateStores} (the Java-side "other writer" and the read-back point),
 * and what scripts read back through the Marker binding. The tests never touch private Map or
 * lock fields.
 */
class Ticket10GlobalStateTest {

    @BeforeAll
    static void initPlatform() {
        TestPlatformInit.ensureInitialized();
    }

    @BeforeEach
    void cleanScriptDirs() throws Exception {
        for (ScriptType type : ScriptType.values()) {
            Path dir = ScriptTypeEnv.scriptsDir(type);
            if (dir == null) continue;
            Files.createDirectories(dir);
            try (var stream = Files.list(dir)) {
                for (Path path : stream.toList()) {
                    Files.deleteIfExists(path);
                }
            }
        }
    }

    @AfterEach
    void cleanScriptDirsAfter() throws Exception {
        for (ScriptType type : ScriptType.values()) {
            Path dir = ScriptTypeEnv.scriptsDir(type);
            if (dir == null) continue;
            Files.createDirectories(dir);
            try (var stream = Files.list(dir)) {
                for (Path path : stream.toList()) {
                    Files.deleteIfExists(path);
                }
            }
        }
    }


    // ==================== Test bindings (the smallest public API surface) ====================

    public static final class Marker {
        public final List<String> hits = new ArrayList<>();

        public void hit(String tag) {
            hits.add(tag);
        }

        public int countOf(String prefix) {
            return (int) hits.stream().filter(h -> h.startsWith(prefix)).count();
        }

        public Optional<String> first(String prefix) {
            return hits.stream().filter(h -> h.startsWith(prefix)).findFirst();
        }
    }

    /**
     * Reads and writes committed state from the script side and registers candidate plans. The
     * "other writer" commits directly through the public API of the root-owned
     * GlobalStateStores, without going through any generation view.
     */
    public static final class StateTool {
        private final GlobalStateStores stores;
        private final Marker marker;

        StateTool(GlobalStateStores stores, Marker marker) {
            this.stores = stores;
            this.marker = marker;
        }

        private GlobalStore store(String scope) {
            return switch (scope) {
                case "shared" -> stores.sharedStore();
                case "startup" -> stores.storeFor(ScriptType.STARTUP);
                case "server" -> stores.storeFor(ScriptType.SERVER);
                case "client" -> stores.storeFor(ScriptType.CLIENT);
                case "test" -> stores.storeFor(ScriptType.TEST);
                default -> throw new IllegalArgumentException("unknown scope: " + scope);
            };
        }

        /** Reads <b>committed</b> state, bypassing the current generation's write set, so a test can
         * observe the value actually published before commit. */
        public String read(String scope, String key) {
            Object value = store(scope).get(key);
            return value == null ? null : String.valueOf(value);
        }

        /** The "other writer" writes the committed store directly from Java. The store version and
         * epoch bump with the write. */
        public void write(String scope, String key, String value) {
            store(scope).put(key, value);
        }

        /**
         * Registers a joint plan while the candidate runs. mode is one of fail-preflight,
         * publish-throw, pass or other-writer-preflight. Goes through the public
         * ScriptManager.registerCandidatePlan.
         */
        public boolean plan(String domain, String mode) {
            Context current = Context.getCurrent();
            return ScriptManager.registerCandidatePlan(current, new CandidateStatePlan() {
                @Override public String domain() { return domain; }
                @Override public void preflight() {
                    if ("fail-preflight".equals(mode)) {
                        throw new IllegalStateException("plan preflight boom: " + domain);
                    }
                    if ("other-writer-preflight".equals(mode)) {
                        // Simulates another writer slipping in between STATE_PLAN and commit: the write-set check has
                        // already run, so only the re-check under the lock at the commit point can catch this
                        // committed write.
                        stores.sharedStore().put("backstopKey", "other-writer");
                    }
                }
                @Override public void publish() {
                    if ("publish-throw".equals(mode)) {
                        throw new IllegalStateException("plan publish boom: " + domain);
                    }
                    marker.hit("plan-published:" + domain);
                }
            });
        }
    }

    private static final class StubPluginRuntime implements IPluginRuntime {
        private final Marker marker;
        private final StateTool state;

        StubPluginRuntime(Marker marker, StateTool state) {
            this.marker = marker;
            this.state = state;
        }

        @Override
        public Map<String, Binding> bindings(ScriptType type) {
            return Map.of(
                    "Marker", Binding.of("Marker", marker),
                    "State", Binding.of("State", state));
        }

        @Override public Map<String, com.tkisor.nekojs.api.event.EventGroup> eventGroups() { return Map.of(); }
        @Override public List<JSTypeAdapter<?>> adapters() { return List.of(); }
        @Override public List<com.tkisor.nekojs.api.catalog.TypeDocCatalogEntry> typeDocs() { return List.of(); }
        @Override public List<com.tkisor.nekojs.api.catalog.ManualDeclarationCatalogEntry> manualDeclarations() { return List.of(); }
        @Override public Map<String, String> nodeModules() { return Map.of(); }
        @Override public Map<String, com.tkisor.nekojs.api.recipe.RecipeNamespaceEntry> recipeNamespaces() { return Map.of(); }
        @Override public void beforeRecipeLoading(com.tkisor.nekojs.api.recipe.RecipeLifecycleContext context) {}
        @Override public void afterRecipes(com.tkisor.nekojs.api.recipe.RecipeLifecycleContext context) {}
        @Override public void fireInit() {}
        @Override public void fireInitStartup() {}
        @Override public void fireAfterInit() {}
        @Override public void fireBeforeScriptsLoaded(ScriptType type) {}
        @Override public void fireAfterScriptsLoaded(ScriptType type) {}
        @Override public com.tkisor.nekojs.api.surface.ApiRuntimeView apiRuntime(com.tkisor.nekojs.api.surface.EnvironmentKey environment) { return null; }
        @Override public Object managedApiImplementation(com.tkisor.nekojs.api.surface.ApiSymbolId globalId) { return null; }
    }

    /** Wires up managers for several script types: one root state domain plus a real Graal
     * environment. No event groups are involved, so the tests focus on state semantics. */
    static final class Harness implements AutoCloseable {
        final Engine engine = Engine.newBuilder().build();
        final GlobalStateStores stores = new GlobalStateStores();
        final Marker marker = new Marker();
        final StateTool state = new StateTool(stores, marker);
        final StubPluginRuntime pluginRuntime = new StubPluginRuntime(marker, state);
        final Map<ScriptType, ScriptManager> managers = new EnumMap<>(ScriptType.class);

        Harness() {
            NekoJSPaths paths = NekoJSPaths.get();
            SandboxConfig config = testConfig();
            DefaultErrorTracker tracker = new DefaultErrorTracker(paths, config);
            NekoCoreContext core = new NekoCoreContext(engine, config, new ClassFilter(config), tracker);
            ScriptCompilerRegistry compilers = ScriptCompilerRegistry.createRuntimeRegistry();
            NekoModulePipelineCache cache = com.tkisor.nekojs.testfixture.NekoModuleTestFixtures
                    .newCache(paths, compilers, config);
            NekoSandboxFactory sandboxFactory = new NekoSandboxFactory(
                    core, paths, compilers, pluginRuntime, cache);
            ScriptEnvironmentFactory environmentFactory = new ScriptEnvironmentFactory(
                    ScriptEventBridge.EMPTY, pluginRuntime, sandboxFactory, stores);
            for (ScriptType type : ScriptType.values()) {
                managers.put(type, new ScriptManager(type, ScriptEventBridge.EMPTY, pluginRuntime,
                        newPropertyRegistry(), tracker, paths, config, environmentFactory, List.of(), cache));
            }
        }

        ScriptManager managerOf(ScriptType type) {
            return managers.get(type);
        }

        void writeScript(ScriptType type, String fileName, String source) throws Exception {
            Path dir = ScriptTypeEnv.scriptsDir(type);
            Files.createDirectories(dir);
            Files.writeString(dir.resolve(fileName), source);
        }

        void load(ScriptType type) {
            managerOf(type).discoverScripts();
            managerOf(type).loadScripts();
        }

        void reload(ScriptType type) {
            managerOf(type).reloadScripts();
        }

        void runTests() {
            managerOf(ScriptType.TEST).runTestScripts();
        }

        @Override
        public void close() {
            for (ScriptManager manager : managers.values()) {
                try {
                    manager.close();
                } catch (Throwable ignored) {
                }
            }
            engine.close();
        }
    }

    private static SandboxConfig testConfig() {
        // 5 s of wall clock plus a 100k statement limit. Failure injection works by exhausting the
        // statement limit.
        return new SandboxConfig(false, false, false, false, true, true, false, true);
    }

    private static ScriptPropertyRegistry newPropertyRegistry() {
        var impl = new ScriptPropertyRegistry.Impl();
        impl.register(ScriptProperty.AFTER);
        impl.register(ScriptProperty.MODLOADED);
        impl.register(ScriptProperty.DISABLE);
        impl.register(ScriptProperty.PRIORITY);
        impl.freeze();
        return impl;
    }

    // ==================== Same type shares state; the four types stay isolated ====================

    @Test
    void sameTypeFilesShareGlobalAndAllFourTypesReadBackIndependentValues() throws Exception {
        try (Harness harness = new Harness()) {
            harness.writeScript(ScriptType.STARTUP, "entry.js",
                    "global.same = 'startup'");
            harness.writeScript(ScriptType.SERVER, "a.js",
                    "global.count = (global.count || 0) + 1; global.same = 'server'");
            harness.writeScript(ScriptType.SERVER, "b.js",
                    "global.count = (global.count || 0) + 10; Marker.hit('b-sees-a:' + global.same)");
            harness.writeScript(ScriptType.CLIENT, "entry.js",
                    "global.same = 'client'; Marker.hit('client-sees-server:' + (typeof global.count))");
            harness.writeScript(ScriptType.TEST, "entry.js",
                    "global.same = 'test'");

            harness.load(ScriptType.STARTUP);
            harness.load(ScriptType.SERVER);
            harness.load(ScriptType.CLIENT);
            harness.runTests();

            // Files of the same type share state: b.js reads what a.js wrote (count accumulates and same
            // is already set).
            assertEquals("b-sees-a:server", harness.marker.first("b-sees-a").orElseThrow(),
                    "second file of the same type must see the first file's global writes");
            assertEquals("client-sees-server:undefined", harness.marker.first("client-sees-server").orElseThrow(),
                    "CLIENT must not observe SERVER's private global (per-type backing store)");

            // Each of the four types reads back its own value for the same key
            assertEquals("startup", harness.stores.storeFor(ScriptType.STARTUP).get("same"));
            assertEquals("server", harness.stores.storeFor(ScriptType.SERVER).get("same"));
            assertEquals("client", harness.stores.storeFor(ScriptType.CLIENT).get("same"));
            assertEquals("test", harness.stores.storeFor(ScriptType.TEST).get("same"));
            assertEquals(11, harness.stores.storeFor(ScriptType.SERVER).get("count"),
                    "same-type multi-file accumulation");
        }
    }

    // ==================== Read-your-writes, publish on success, discard on failure ====================

    @Test
    void candidateReadsYourWritesAndSuccessfulCommitPublishes() throws Exception {
        try (Harness harness = new Harness()) {
            harness.writeScript(ScriptType.SERVER, "entry.js",
                    "global.keep = 'v1'; global.a = 1; global.b = 2");
            harness.load(ScriptType.SERVER);

            // Inside the candidate: read-your-writes, plus the committed values of the old active
            // generation, so state carries across reloads
            harness.writeScript(ScriptType.SERVER, "entry.js", """
                    global.ryw = 'cand'
                    Marker.hit('ryw:' + global.ryw)
                    Marker.hit('sees-committed:' + global.keep)
                    Marker.hit('sees-committed-a:' + global.a)
                    """);
            // The candidate's writes are not visible before commit. Reload does the whole thing inline, so
            // a test observes the committed store just before commit instead.
            harness.reload(ScriptType.SERVER);

            assertEquals("ryw:cand", harness.marker.first("ryw").orElseThrow(),
                    "candidate must read back its own top-level writes (read-your-writes)");
            assertEquals("sees-committed:v1", harness.marker.first("sees-committed").orElseThrow(),
                    "candidate must see the previous generation's committed values");
            assertEquals("cand", harness.stores.storeFor(ScriptType.SERVER).get("ryw"),
                    "successful commit publishes the candidate write set");

            // A candidate's writes never enter the committed store. Mid-flight state cannot be observed
            // once reload returns, so the next failing candidate shows it: on failure none of set,
            // delete or clear is published
            harness.writeScript(ScriptType.SERVER, "entry.js", """
                    global.a = 99
                    delete global.b
                    global.clear()
                    global.c = 3
                    Marker.hit('cand-sees-clear:' + (typeof global.a) + ':' + (typeof global.keep))
                    throw Error('candidate rejected after state mutations')
                    """);
            NekoReloadException failure = assertThrows(NekoReloadException.class,
                    () -> harness.reload(ScriptType.SERVER),
                    "a candidate rejected after state mutations must fail the reload");
            assertEquals(ReloadPhase.EXECUTION, failure.report().phase(), failure.report().describe());

            // Discard on failure: no set, delete or clear pollutes the old active state
            assertEquals(1, harness.stores.storeFor(ScriptType.SERVER).get("a"),
                    "failed candidate's set must be discarded");
            assertEquals(2, harness.stores.storeFor(ScriptType.SERVER).get("b"),
                    "failed candidate's delete must be discarded");
            assertEquals(4, harness.stores.storeFor(ScriptType.SERVER).size(),
                    "failed candidate's clear must be discarded; old keys intact");
            assertNull(harness.stores.storeFor(ScriptType.SERVER).get("c"),
                    "writes after clear in the failed candidate must not publish");
            assertEquals("ryw:cand", harness.marker.first("ryw").orElseThrow()); // marker untouched

            // The old active state still works, so the next reload goes through
            harness.writeScript(ScriptType.SERVER, "entry.js", "global.keep = 'v3'");
            harness.reload(ScriptType.SERVER);
            assertEquals("v3", harness.stores.storeFor(ScriptType.SERVER).get("keep"));
        }
    }

    // ========= Conflict detection, no lost writes, no cross-type conflict, shared contention =========

    @Test
    void otherWriterDuringCandidacyConflictsWithoutLosingTheOtherWriterValue() throws Exception {
        try (Harness harness = new Harness()) {
            harness.writeScript(ScriptType.SERVER, "entry.js", "global.k = 'base'");
            harness.load(ScriptType.SERVER);

            // The candidate writes k first, with the version committed at that moment as its base. An
            // "other writer" then commits the same managed key.
            harness.writeScript(ScriptType.SERVER, "entry.js", """
                    global.k = 'cand'
                    State.write('server', 'k', 'other')
                    """);
            NekoReloadException failure = assertThrows(NekoReloadException.class,
                    () -> harness.reload(ScriptType.SERVER),
                    "another writer committing the same managed key during candidacy must fail the candidate");
            assertEquals(ReloadPhase.STATE_PLAN, failure.report().phase(),
                    "conflict must be reported at the joint state-plan phase: " + failure.report().describe());
            assertEquals("global-write-conflict", failure.report().domain(),
                    failure.report().describe());
            assertEquals("other", harness.stores.storeFor(ScriptType.SERVER).get("k"),
                    "the other writer's committed value must be retained (no lost write);"
                            + " the candidate's stale write must not overwrite it");
        }
    }

    @Test
    void crossTypeSamePrivateKeyDoesNotConflict() throws Exception {
        try (Harness harness = new Harness()) {
            harness.writeScript(ScriptType.SERVER, "entry.js", "");
            harness.load(ScriptType.SERVER);
            harness.load(ScriptType.CLIENT);

            // While the SERVER candidate writes its private global.k, CLIENT (the active generation)
            // writes its own private global.k. The two stores keep their own versions, so the writes do
            // not conflict and both hold
            harness.writeScript(ScriptType.SERVER, "entry.js", """
                    global.k = 'server'
                    State.write('client', 'k', 'client')
                    """);
            harness.reload(ScriptType.SERVER);

            assertEquals("server", harness.stores.storeFor(ScriptType.SERVER).get("k"));
            assertEquals("client", harness.stores.storeFor(ScriptType.CLIENT).get("k"),
                    "cross-type same private key must not conflict (separate backing stores)");
        }
    }

    @Test
    void sharedCompetingWriteFailsTheCandidateAndKeepsTheCompetingValue() throws Exception {
        try (Harness harness = new Harness()) {
            harness.writeScript(ScriptType.SERVER, "entry.js", "");
            harness.load(ScriptType.SERVER);

            harness.writeScript(ScriptType.SERVER, "entry.js", """
                    shared.handoff = 'server-cand'
                    State.write('shared', 'handoff', 'client-won')
                    """);
            NekoReloadException failure = assertThrows(NekoReloadException.class,
                    () -> harness.reload(ScriptType.SERVER),
                    "competing writes on the explicit shared entry must be detected");
            assertEquals("global-write-conflict", failure.report().domain(), failure.report().describe());
            assertEquals("client-won", harness.stores.sharedStore().get("handoff"),
                    "the competing committed value stays; the candidate's shared write is not published");
        }
    }

    // ==================== One candidate writes both global and shared: joint outcome ====================

    @Test
    void candidateWritingBothGlobalAndSharedCommitsJointlyOrNotAtAll() throws Exception {
        try (Harness harness = new Harness()) {
            harness.writeScript(ScriptType.SERVER, "entry.js", "");
            harness.load(ScriptType.SERVER);

            // The failure case: an external plan fails the joint preflight, so neither side publishes and
            // there is no half commit
            harness.writeScript(ScriptType.SERVER, "entry.js", """
                    global.joint = 'g'
                    shared.joint = 's'
                    State.plan('joint-fixture', 'fail-preflight')
                    """);
            NekoReloadException failure = assertThrows(NekoReloadException.class,
                    () -> harness.reload(ScriptType.SERVER),
                    "a failing joined plan must fail the whole candidate");
            assertEquals("state-plan-preflight:joint-fixture", failure.report().domain(),
                    failure.report().describe());
            assertNull(harness.stores.storeFor(ScriptType.SERVER).get("joint"),
                    "private global write set must not publish (joint failure)");
            assertNull(harness.stores.sharedStore().get("joint"),
                    "shared write set must not publish (joint failure) — no half commit");

            // The success case: both sides publish together
            harness.writeScript(ScriptType.SERVER, "entry.js", """
                    global.joint = 'g'
                    shared.joint = 's'
                    Marker.hit('candidate-sees-both:' + global.joint + ':' + shared.joint)
                    """);
            harness.reload(ScriptType.SERVER);
            assertEquals("g", harness.stores.storeFor(ScriptType.SERVER).get("joint"));
            assertEquals("s", harness.stores.sharedStore().get("joint"));
            assertEquals("candidate-sees-both:g:s", harness.marker.first("candidate-sees-both").orElseThrow());
        }
    }

    // ==================== Joint preflight, no domain semantics ====================

    @Test
    void candidateStatePlanBoundaryJoinsJointPreflightAndJointOutcome() throws Exception {
        try (Harness harness = new Harness()) {
            harness.writeScript(ScriptType.SERVER, "entry.js", "");
            harness.load(ScriptType.SERVER);

            // 1) A passing plan runs both preflight and publish, and its write set publishes with the
            // joint commit
            harness.writeScript(ScriptType.SERVER, "entry.js", """
                    global.p = 'p'
                    State.plan('passing-plan', 'pass')
                    """);
            harness.reload(ScriptType.SERVER);
            assertEquals("p", harness.stores.storeFor(ScriptType.SERVER).get("p"));
            assertEquals(1, harness.marker.countOf("plan-published:passing-plan"),
                    "a passing plan's publish must run exactly once at the commit point");

            // 2) The plan's preflight throws during STATE_PLAN, so the candidate fails and neither global
            // nor shared publishes
            harness.writeScript(ScriptType.SERVER, "entry.js", """
                    global.p2 = 'p2'
                    shared.p2 = 's2'
                    State.plan('test-plan', 'fail-preflight')
                    """);
            NekoReloadException preflightFailure = assertThrows(NekoReloadException.class,
                    () -> harness.reload(ScriptType.SERVER));
            assertEquals(ReloadPhase.STATE_PLAN, preflightFailure.report().phase());
            assertEquals("state-plan-preflight:test-plan", preflightFailure.report().domain(),
                    preflightFailure.report().describe());
            assertNull(harness.stores.storeFor(ScriptType.SERVER).get("p2"));
            assertNull(harness.stores.sharedStore().get("p2"));
            assertEquals(1, harness.marker.countOf("plan-published:passing-plan"),
                    "the failing candidate must not publish any plan");

            // 3) The plan breaks the publish rule: preflight passes, then publish throws, so the global
            // and shared write sets stay unpublished. The plan's own partial side effects are not
            // rolled back; the plan author owns them
            harness.writeScript(ScriptType.SERVER, "entry.js", """
                    global.p3 = 'p3'
                    State.plan('publish-violator', 'publish-throw')
                    """);
            NekoReloadException publishFailure = assertThrows(NekoReloadException.class,
                    () -> harness.reload(ScriptType.SERVER));
            assertEquals("state-plan-publish:publish-violator", publishFailure.report().domain(),
                    publishFailure.report().describe());
            assertNull(harness.stores.storeFor(ScriptType.SERVER).get("p3"),
                    "global/shared write sets must stay unpublished when a plan violates publish");

            // 4) Another writer slips in between STATE_PLAN and commit; the re-check under the lock at the
            // commit point catches it
            harness.writeScript(ScriptType.SERVER, "entry.js", """
                    shared.backstopKey = 'cand'
                    State.plan('sneaky-writer', 'other-writer-preflight')
                    """);
            NekoReloadException backstop = assertThrows(NekoReloadException.class,
                    () -> harness.reload(ScriptType.SERVER),
                    "a writer sneaking in between STATE_PLAN and commit must be caught by the locked re-validation");
            assertEquals("global-write-conflict", backstop.report().domain(), backstop.report().describe());
            assertEquals("other-writer", harness.stores.sharedStore().get("backstopKey"),
                    "the sneaking writer's committed value is retained; candidate write not published");
        }
    }

    // ==================== Root lifetime: retained, released, a separate root starts empty ====================

    @Test
    void rootOwnedStateSurvivesReloadAndStopCycleAndIsReleasedByRootClose() throws Exception {
        Engine engine = Engine.newBuilder().build();
        NekoJSPaths paths = NekoJSPaths.get();
        SandboxConfig config = testConfig();
        Marker marker = new Marker();
        GlobalStateStores standalone = new GlobalStateStores();
        StateTool state = new StateTool(standalone, marker);
        StubPluginRuntime pluginRuntime = new StubPluginRuntime(marker, state);
        DefaultErrorTracker tracker = new DefaultErrorTracker(paths, config);
        NekoCoreContext core = new NekoCoreContext(engine, config, new ClassFilter(config), tracker);
        ScriptCompilerRegistry compilers = ScriptCompilerRegistry.createRuntimeRegistry();
        NekoModulePipelineCache cache = com.tkisor.nekojs.testfixture.NekoModuleTestFixtures
                .newCache(paths, compilers, config);
        NekoSandboxFactory sandboxFactory = new NekoSandboxFactory(
                core, paths, compilers, pluginRuntime, cache);
        NekoRuntimeRoot root = new NekoRuntimeRoot(core, pluginRuntime, ScriptEventBridge.EMPTY,
                newPropertyRegistry(), sandboxFactory, cache);
        try {
            Files.createDirectories(ScriptTypeEnv.scriptsDir(ScriptType.STARTUP));

            // A separate root starts with empty state
            assertTrue(root.globalState().storeFor(ScriptType.SERVER).isEmpty(),
                    "a fresh root must start with empty global state");
            assertTrue(root.globalState().sharedStore().isEmpty());

            // Write the script before discover and load, because loadScripts consumes the list from discover
            Path serverDir = ScriptTypeEnv.scriptsDir(ScriptType.SERVER);
            Files.writeString(serverDir.resolve("entry.js"),
                    "global.retained = (global.retained || 0) + 1; shared.anchor = 'root-one'");
            root.createScriptManager(ScriptType.SERVER).discoverScripts();
            root.scriptManagerOf(ScriptType.SERVER).loadScripts();
            assertEquals(1, root.globalState().storeFor(ScriptType.SERVER).get("retained"));

            // A normal reload: closing the generation must not clear anything, so values that are not guest
            // values survive. entry.js increments on every run, so retained going from 1 to 2 shows the old
            // value was readable and kept
            root.reload(ScriptType.SERVER);
            assertEquals(2, root.globalState().storeFor(ScriptType.SERVER).get("retained"),
                    "generation close must not clear root-level state (reloaded script read the"
                            + " retained value and incremented it)");
            assertEquals("root-one", root.globalState().sharedStore().get("anchor"));

            // Simulates a server stop or world switch: it only clears the WORLD pack listeners, as the
            // platform hook does, and root state is retained. This leg passes an empty WORLD list, so the
            // hook path does not actually clean up entries. The real support is structural: the only
            // caller of closeAll() is NekoRuntimeRoot.closeSilently, and the production server stop and
            // world unload hook (ServerEventListener -> clearWorldPackListeners) never touches the state
            // domain.
            root.scriptManagerOf(ScriptType.SERVER).clearWorldPackListeners(List.of());
            root.scriptManagerOf(ScriptType.SERVER).discoverScripts();
            assertEquals(2, root.globalState().storeFor(ScriptType.SERVER).get("retained"),
                    "server stop / world switch must not clear global/shared");

            // Another reload: it keeps accumulating on the retained value, observed from the script side
            // across reloads
            root.reload(ScriptType.SERVER);
            assertEquals(3, root.globalState().storeFor(ScriptType.SERVER).get("retained"),
                    "reloaded scripts must see and keep incrementing the retained value");

            // Closing the root releases the state. After close, acquiring a store is explicitly rejected;
            // a reference acquired earlier reads the released, empty state
            GlobalStore serverStoreRef = root.globalState().storeFor(ScriptType.SERVER);
            GlobalStore sharedStoreRef = root.globalState().sharedStore();
            root.closeSilently();
            assertTrue(root.globalState().isClosed(), "root close must release the state domain");
            assertTrue(sharedStoreRef.isEmpty(), "released stores must not retain entries");
            assertTrue(serverStoreRef.isEmpty(), "released stores must not retain entries");
            assertEquals("3-is-gone", sharedStoreRef.containsKey("anchor")
                    ? "still-there" : "3-is-gone");
            assertThrows(IllegalStateException.class,
                    () -> root.globalState().storeFor(ScriptType.SERVER),
                    "acquiring stores from a released root must be explicitly rejected");

            // A separate root, as a test runner uses, starts empty: the two never see each other's state
            Engine engine2 = Engine.newBuilder().build();
            DefaultErrorTracker tracker2 = new DefaultErrorTracker(paths, config);
            NekoCoreContext core2 = new NekoCoreContext(engine2, config, new ClassFilter(config), tracker2);
            ScriptCompilerRegistry compilers2 = ScriptCompilerRegistry.createRuntimeRegistry();
            NekoModulePipelineCache cache2 = com.tkisor.nekojs.testfixture.NekoModuleTestFixtures
                    .newCache(paths, compilers2, config);
            NekoRuntimeRoot root2 = new NekoRuntimeRoot(core2, pluginRuntime, ScriptEventBridge.EMPTY,
                    newPropertyRegistry(), new NekoSandboxFactory(core2, paths,
                    compilers2, pluginRuntime, cache2), cache2);
            try {
                assertTrue(root2.globalState().storeFor(ScriptType.SERVER).isEmpty(),
                        "a second independent root must start empty (no cross-root state bleed)");
                assertNull(root2.globalState().sharedStore().get("anchor"),
                        "the closed root's shared state must not leak into a new root");
            } finally {
                root2.closeSilently();
                engine2.close();
            }
        } finally {
            root.closeSilently();
            engine.close();
        }
    }

    // ==================== Guest Value lifetime: no promise of deep rollback ====================

    @Test
    void storedGuestFunctionsExpireWithTheirGenerationWhileHostValuesSurvive() throws Exception {
        try (Harness harness = new Harness()) {
            harness.writeScript(ScriptType.SERVER, "entry.js", """
                    global.fn = function () { return 42 }
                    global.helper = 7
                    Marker.hit('v1-fn:' + typeof global.fn)
                    """);
            harness.load(ScriptType.SERVER);
            assertEquals("v1-fn:function", harness.marker.first("v1-fn").orElseThrow());
            assertNotNull(harness.stores.storeFor(ScriptType.SERVER).get("fn"),
                    "guest function is stored while its generation is alive");

            // Reload: the old generation is still alive while the candidate runs, so its committed guest
            // values are readable by the candidate (read-committed semantics). The old generation closes at
            // commit, and its guest values expire with it
            harness.writeScript(ScriptType.SERVER, "entry.js", """
                    Marker.hit('candidate-sees-live-old-gen:' + (typeof global.fn))
                    """);
            harness.reload(ScriptType.SERVER);
            assertEquals("candidate-sees-live-old-gen:function",
                    harness.marker.first("candidate-sees-live-old-gen").orElseThrow(),
                    "during candidate execution the old generation is still alive (committed reads)");
            assertNull(harness.stores.storeFor(ScriptType.SERVER).get("fn"),
                    "guest functions from the destroyed generation must expire at commit"
                            + " (no permanent keep-alive from being stored in the map)");

            // What is observed after commit, through a FILE reload probe inside the new active generation:
            // the guest function has expired while the host scalar value survives, because root state is
            // kept across reloads
            harness.writeScript(ScriptType.SERVER, "probe.js", """
                    Marker.hit('v2-fn:' + (typeof global.fn))
                    Marker.hit('v2-helper:' + global.helper)
                    """);
            harness.managerOf(ScriptType.SERVER).reloadScriptFile("probe.js");
            assertEquals("v2-fn:undefined", harness.marker.first("v2-fn").orElseThrow(),
                    "post-commit reads must not see the destroyed generation's guest functions");
            assertEquals("v2-helper:7", harness.marker.first("v2-helper").orElseThrow(),
                    "host scalar values must survive reloads (root-level retention)");
        }
    }

    @Test
    void nestedMutationsAreNotDeepRolledBack() throws Exception {
        try (Harness harness = new Harness()) {
            // Within one generation: file1 stores a nested object and file2 mutates the inner field in
            // place. Nested mutations never enter the write set and take effect immediately, so a failed
            // candidate explicitly does not roll them back
            harness.writeScript(ScriptType.SERVER, "one.js", "global.box = { v: 1 }");
            harness.writeScript(ScriptType.SERVER, "two.js", """
                    global.box.v = 99
                    Marker.hit('nested-mutated:' + global.box.v)
                    """);
            harness.load(ScriptType.SERVER);
            assertEquals("nested-mutated:99", harness.marker.first("nested-mutated").orElseThrow());

            // A failed candidate discards top-level writes without rolling back nested state.
            harness.writeScript(ScriptType.SERVER, "one.js", """
                    global.doomed = 'cand'
                    throw Error('candidate rejected after state mutations')
                    """);
            Files.delete(ScriptTypeEnv.scriptsDir(ScriptType.SERVER).resolve("two.js"));
            assertThrows(NekoReloadException.class, () -> harness.reload(ScriptType.SERVER));
            assertNull(harness.stores.storeFor(ScriptType.SERVER).get("doomed"),
                    "top-level candidate write is discarded");

            // The FILE reload probe inside the same active generation reads the nested value back: it is
            // still 99, so nothing was deep-rolled-back
            Files.delete(ScriptTypeEnv.scriptsDir(ScriptType.SERVER).resolve("one.js"));
            harness.writeScript(ScriptType.SERVER, "probe.js",
                    "Marker.hit('box-after-failed-candidate:' + global.box.v)");
            harness.managerOf(ScriptType.SERVER).reloadScriptFile("probe.js");
            assertEquals("box-after-failed-candidate:99",
                    harness.marker.first("box-after-failed-candidate").orElseThrow(),
                    "nested mutations are explicitly NOT deep-rolled-back by a failed candidate");
        }
    }

    // ==================== global, the state container, vs globalThis, the language global ====================

    @Test
    void globalIsTheStateContainerWhileGlobalThisStaysTheLanguageGlobal() throws Exception {
        try (Harness harness = new Harness()) {
            harness.writeScript(ScriptType.SERVER, "entry.js", """
                    global.lang = 'container'
                    Marker.hit('division:' + (global !== globalThis))
                    globalThis.lang = 'language-global'
                    Marker.hit('container-value:' + global.lang)
                    Marker.hit('language-value:' + globalThis.lang)
                    global.x_on_container = 1
                    Marker.hit('globalThis-clean:' + (typeof globalThis.x_on_container === 'undefined'))
                    Marker.hit('container-owns-global-name:' + (globalThis.global === global))
                    Marker.hit('module-internals-use-globalThis:' + (typeof globalThis.require === 'function' && typeof globalThis.__nekoNodeResolve === 'function'))
                    Marker.hit('require-intact:' + typeof require)
                    """);
            harness.load(ScriptType.SERVER);

            assertEquals("division:true", harness.marker.first("division").orElseThrow(),
                    "in the full NekoJS environment, global (state container) must differ from globalThis");
            assertEquals("container-value:container", harness.marker.first("container-value").orElseThrow());
            assertEquals("language-value:language-global", harness.marker.first("language-value").orElseThrow(),
                    "globalThis keeps independent language-global properties");
            assertEquals("globalThis-clean:true", harness.marker.first("globalThis-clean").orElseThrow(),
                    "container writes must not leak onto globalThis");
            assertEquals("container-owns-global-name:true",
                    harness.marker.first("container-owns-global-name").orElseThrow(),
                    "the binding install takes over the 'global' property name on globalThis for the"
                            + " state container (same takeover as the pre-1.2.0 binding; the Node shim's"
                            + " global===globalThis alias only holds in shim-only contexts)");
            assertEquals("module-internals-use-globalThis:true",
                    harness.marker.first("module-internals-use-globalThis").orElseThrow(),
                    "Node shim / module install and resolution keep using the correct language global"
                            + " object (globalThis), not the state container");
            assertEquals("require-intact:function", harness.marker.first("require-intact").orElseThrow());
            assertEquals("container", harness.stores.storeFor(ScriptType.SERVER).get("lang"),
                    "script writes on global go to the NekoJS state container, not globalThis");
        }
    }

    // ==================== Migration: same-type usage unchanged, cross-type implicit reads gone ====================

    @Test
    void sameTypeUsageUnchangedAndLegacyCrossTypeFallbackIsGone() throws Exception {
        try (Harness harness = new Harness()) {
            // Same-type usage is unchanged: scripts read and write global.foo directly, KubeJS style
            harness.writeScript(ScriptType.SERVER, "entry.js", """
                    global.counter = (global.counter || 0) + 1
                    shared.cross = 'explicit'
                    """);
            harness.load(ScriptType.SERVER);
            harness.reload(ScriptType.SERVER);
            assertEquals(2, harness.stores.storeFor(ScriptType.SERVER).get("counter"),
                    "same-type global.foo usage is unchanged across reloads");

            // The old implicit cross-type fallback is deleted. CLIENT reading global.cross gets undefined,
            // because no process-wide Map is shared any more, and cross-type sharing goes through the
            // explicit shared entry
            harness.writeScript(ScriptType.CLIENT, "entry.js", """
                    Marker.hit('legacy-implicit-gone:' + (typeof global.cross))
                    Marker.hit('explicit-shared:' + shared.cross)
                    """);
            harness.load(ScriptType.CLIENT);
            assertEquals("legacy-implicit-gone:undefined",
                    harness.marker.first("legacy-implicit-gone").orElseThrow(),
                    "the old implicit cross-type global fallback must not exist");
            assertEquals("explicit-shared:explicit",
                    harness.marker.first("explicit-shared").orElseThrow(),
                    "cross-type sharing goes through the explicit shared entry");
        }
    }

    // ==================== Staying in scope: no second owner and no static state ====================

    @Test
    void stateDomainHasNoStaticStateAndNoSecondOwner() throws Exception {
        // The global state domain is held entirely by the root instance, so key types in the
        // core.state package must have no mutable static fields; otherwise the old process-wide
        // NekoGlobal problem returns. Guard lint and code review cover the "add nothing" rule for the
        // permission system, the network sync protocol and a generic transaction framework. This test
        // pins down the key rule: no mutable static state.
        for (Class<?> clazz : List.of(GlobalStateStores.class, GlobalStore.class,
                GenerationGlobals.class, GlobalView.class)) {
            for (Field field : clazz.getDeclaredFields()) {
                int modifiers = field.getModifiers();
                assertFalse(Modifier.isStatic(modifiers) && !Modifier.isFinal(modifiers),
                        clazz.getSimpleName() + "." + field.getName()
                                + " must not be mutable static state (root-owned domain only)");
            }
        }
    }
}
