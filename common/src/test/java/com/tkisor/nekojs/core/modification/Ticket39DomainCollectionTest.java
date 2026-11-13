package com.tkisor.nekojs.core.modification;

import com.tkisor.nekojs.api.ScriptType;
import com.tkisor.nekojs.api.data.Binding;
import com.tkisor.nekojs.api.JSTypeAdapter;
import com.tkisor.nekojs.api.event.EventGroup;
import com.tkisor.nekojs.api.event.EventBusJS;
import com.tkisor.nekojs.api.plugin.IPluginRuntime;
import com.tkisor.nekojs.core.DefaultScriptEventBridge;
import com.tkisor.nekojs.core.NekoCoreContext;
import com.tkisor.nekojs.core.NekoSandboxFactory;
import com.tkisor.nekojs.core.compiler.ScriptCompilerRegistry;
import com.tkisor.nekojs.core.config.SandboxConfig;
import com.tkisor.nekojs.core.error.DefaultErrorTracker;
import com.tkisor.nekojs.core.fs.ClassFilter;
import com.tkisor.nekojs.core.fs.NekoJSPaths;
import com.tkisor.nekojs.core.lifecycle.CandidateDomainCollector;
import com.tkisor.nekojs.core.lifecycle.NekoReloadException;
import com.tkisor.nekojs.core.lifecycle.NekoRuntimeRoot;
import com.tkisor.nekojs.core.lifecycle.ReloadPhase;
import com.tkisor.nekojs.core.module.NekoModulePipelineCache;
import com.tkisor.nekojs.core.state.GlobalStateStores;
import com.tkisor.nekojs.script.ScriptEnvironmentFactory;
import com.tkisor.nekojs.script.ScriptTypeEnv;
import com.tkisor.nekojs.script.prop.ScriptProperty;
import com.tkisor.nekojs.script.prop.ScriptPropertyRegistry;
import com.tkisor.nekojs.testfixture.TestPlatformInit;
import graal.graalvm.polyglot.Engine;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Fixture for the domain plan mechanism in the common layer, with no Minecraft dependency: the
 * DOMAIN_PLAN collection phase, candidates staying inert until commit, and the joint preflight and
 * joint commit with the global/shared write sets.
 *
 * <p>It uses a real {@link NekoRuntimeRoot}, a real Graal pipeline and <b>synthetic</b> domain
 * collector and Adapter (a numeric store for synthetic:target that follows the same "restore the
 * baseline, then apply in declaration order" contract as the real Item/Block Adapters). Behavior of
 * the real Minecraft adapters belongs to the version tree fixtures (the 26.x E2E and 1.21.1 paired
 * files); this file pins the root-owned collection mount point and how the plans commit together.
 *
 * <p>Every assertion goes through public entry points: reload results, the synthetic Adapter's
 * public counters and store, and committed reads through root.globalState(); no private fields are
 * read.
 */
class Ticket39DomainCollectionTest {

    /** Synthetic event bus backing the script-side ModEvents.modification(event => ...). */
    static final EventGroup GROUP = EventGroup.of("ModEvents");
    static final EventBusJS<CollectingEvent, Void> MODIFICATION =
            GROUP.server("modification", CollectingEvent.class);

    /** Key-dispatch bus; domain collection must explicitly reject buses that carry a dispatch key. */
    static final EventBusJS<CollectingEvent, String> DISPATCHED =
            GROUP.server("dispatched", CollectingEvent.class,
                    com.tkisor.nekojs.api.event.DispatchKey.of(String.class, event -> "synthetic:alpha"));

    /** Payload of the collection event: modify(target, cb) records one normalized declaration in the
     * plan. */
    public static final class CollectingEvent {
        private final ModificationCandidatePlan plan;

        CollectingEvent(ModificationCandidatePlan plan) {
            this.plan = plan;
        }

        public void modify(String target, Consumer<PropView> modifier) {
            if (target == null || !target.contains(":")) {
                plan.fail("invalid target '" + target + "' (expected namespace:path)", null);
                return;
            }
            PropView view = new PropView();
            try {
                modifier.accept(view);
            } catch (Throwable t) {
                plan.fail("modifier callback threw for " + target, t);
                return;
            }
            Map<String, Object> properties = new LinkedHashMap<>();
            if (view.power != null) properties.put("power", view.power);
            if (view.label != null) properties.put("label", view.label);
            plan.add(new ModificationDeclaration("synthetic", target, properties, null));
        }

        public int getDeclaredCount() {
            return plan.declarations().size();
        }
    }

    /** Synthetic property view with explicit setters; parity with the real properties is covered by
     * the version tree fixtures. */
    public static final class PropView {
        Integer power;
        String label;

        public void setPower(int power) {
            this.power = power;
        }

        public void setLabel(String label) {
            this.label = label;
        }
    }

    /**
     * Synthetic Adapter: mimics the platform target store and the "restore the baseline, then apply
     * in declaration order" contract. Preflight failure can be injected; the apply counter and the
     * store are the public observation points.
     */
    static final class SyntheticApplier implements ModificationApplier {
        final Map<String, Integer> targets = new LinkedHashMap<>();
        final Map<String, Integer> baselines = new LinkedHashMap<>();
        int preflightCount;
        int applyCount;
        String failPreflightWith;

        void seed(String target, int value) {
            targets.put(target, value);
            baselines.put(target, value);
        }

        @Override
        public String adapterId() {
            return "synthetic-test";
        }

        @Override
        public void preflight(List<ModificationDeclaration> declarations) {
            preflightCount++;
            if (failPreflightWith != null) {
                throw new IllegalStateException(failPreflightWith);
            }
            for (ModificationDeclaration declaration : declarations) {
                if (!targets.containsKey(declaration.targetId())) {
                    throw new IllegalArgumentException("unknown target: " + declaration.targetId());
                }
                Object power = declaration.properties().get("power");
                if (power instanceof Number number && number.intValue() > 100) {
                    throw new IllegalArgumentException("power out of range on " + declaration.targetId());
                }
            }
        }

        @Override
        public void apply(List<ModificationDeclaration> declarations) {
            applyCount++;
            // Restore every baseline held by NekoJS first, then apply the full new plan in declaration order.
            targets.putAll(baselines);
            for (ModificationDeclaration declaration : declarations) {
                if ("power".equals(firstKey(declaration))) {
                    targets.put(declaration.targetId(), ((Number) declaration.properties().get("power")).intValue());
                }
            }
        }

        private static String firstKey(ModificationDeclaration declaration) {
            return declaration.properties().containsKey("power") ? "power" : null;
        }
    }

    /** Domain collector: dispatches the collection events of this candidate and registers the plan,
     * structured like the real Item/Block collectors. */
    static final class SyntheticCollector implements CandidateDomainCollector {
        final SyntheticApplier applier;

        SyntheticCollector(SyntheticApplier applier) {
            this.applier = applier;
        }

        @Override
        public String domain() {
            return "synthetic-modification";
        }

        @Override
        public ScriptType scriptType() {
            return ScriptType.SERVER;
        }

        @Override
        public void collect(Handle handle) {
            ModificationCandidatePlan plan = new ModificationCandidatePlan(applier);
            handle.dispatch(MODIFICATION, new CollectingEvent(plan));
            handle.registerPlan(plan);
        }
    }

    static final class StubPluginRuntime implements IPluginRuntime {
        @Override
        public Map<String, Binding> bindings(ScriptType type) {
            return Map.of();
        }

        @Override
        public Map<String, EventGroup> eventGroups() {
            return Map.of("ModEvents", GROUP);
        }

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

    /** Real root assembly: the SERVER script type only, with the synthetic collector registered. */
    static final class Harness implements AutoCloseable {
        final Engine engine = Engine.newBuilder().build();
        final SyntheticApplier applier = new SyntheticApplier();
        final SyntheticCollector collector = new SyntheticCollector(applier);
        final StubPluginRuntime pluginRuntime = new StubPluginRuntime();
        final DefaultScriptEventBridge bridge = new DefaultScriptEventBridge(null);
        final NekoRuntimeRoot root;

        Harness() {
            bridge.setPluginRuntime(pluginRuntime);
            NekoJSPaths paths = NekoJSPaths.get();
            SandboxConfig config = testConfig();
            DefaultErrorTracker tracker = new DefaultErrorTracker(paths, config);
            NekoCoreContext core = new NekoCoreContext(engine, config, new ClassFilter(config), tracker);
            ScriptCompilerRegistry compilers = ScriptCompilerRegistry.createRuntimeRegistry();
            NekoModulePipelineCache cache = com.tkisor.nekojs.testfixture.NekoModuleTestFixtures
                    .newCache(paths, compilers, config);
            NekoSandboxFactory sandboxFactory = new NekoSandboxFactory(
                    core, paths, compilers, pluginRuntime, cache);
            root = new NekoRuntimeRoot(core, pluginRuntime, bridge,
                    newPropertyRegistry(), sandboxFactory, cache);
            root.registerDomainCollector(collector);
            root.createScriptManager(ScriptType.SERVER).discoverScripts();
        }

        void writeServerScript(String name, String source) throws Exception {
            Files.writeString(ScriptTypeEnv.scriptsDir(ScriptType.SERVER).resolve(name), source);
        }

        @Override
        public void close() throws Exception {
            root.closeSilently();
            engine.close();
        }
    }

    private static SandboxConfig testConfig() {
        return new SandboxConfig(false, false, false, false, true, true, false, true);
    }

    @BeforeAll
    static void initPlatform() {
        TestPlatformInit.ensureInitialized();
    }

    @BeforeEach
    @AfterEach
    void cleanScriptDirs() throws Exception {
        Path dir = ScriptTypeEnv.scriptsDir(ScriptType.SERVER);
        Files.createDirectories(dir);
        try (var stream = Files.list(dir)) {
            for (Path path : stream.toList()) {
                Files.deleteIfExists(path);
            }
        }
    }

    // ==================== Candidates stay inert until the commit applies the plan once ====================

    @Test
    void candidateCollectsInertPlanAndAppliesExactlyOnceAtCommit() throws Exception {
        try (Harness harness = new Harness()) {
            harness.applier.seed("synthetic:alpha", 1);
            harness.applier.seed("synthetic:beta", 2);
            harness.writeServerScript("mod.js", """
                    global.duringCollection = 'unset'
                    ModEvents.modification(event => {
                      global.duringCollection = 'collected'
                      event.modify('synthetic:alpha', p => p.setPower(7))
                      event.modify('synthetic:beta', p => p.setPower(9))
                    })
                    """);
            harness.root.scriptManagerOf(ScriptType.SERVER).loadScripts();
            // The initial load is non-transactional: there is no candidate, so DOMAIN_PLAN does not run.
            // The collector only takes part in a transactional reload.
            assertEquals(0, harness.applier.applyCount, "initial non-transactional load must not run domain collection");
            assertEquals(1, harness.applier.targets.get("synthetic:alpha"));

            harness.root.reload(ScriptType.SERVER);

            assertEquals(1, harness.applier.applyCount, "commit applies the plan exactly once");
            assertEquals(7, harness.applier.targets.get("synthetic:alpha"));
            assertEquals(9, harness.applier.targets.get("synthetic:beta"));
            assertEquals("collected", harness.root.globalState().storeFor(ScriptType.SERVER).get("duringCollection"),
                    "collection dispatch runs inside the candidate context (global view write set published at commit)");
        }
    }

    @Test
    void candidatePlanIsInertUntilCommit() throws Exception {
        // The first reload has applied 7. While the second candidate is being built (after collection
        // dispatch, before commit), the target store must still hold the old active value. A probe
        // collector records what it sees during DOMAIN_PLAN.
        try (Harness harness = new Harness()) {
            harness.applier.seed("synthetic:alpha", 1);
            harness.writeServerScript("mod.js", "ModEvents.modification(e => e.modify('synthetic:alpha', p => p.setPower(7)))");
            harness.root.scriptManagerOf(ScriptType.SERVER).loadScripts();
            harness.root.reload(ScriptType.SERVER);
            assertEquals(7, harness.applier.targets.get("synthetic:alpha"));

            List<Integer> seenDuringCandidate = new ArrayList<>();
            CandidateDomainCollector probe = new CandidateDomainCollector() {
                @Override public String domain() { return "probe"; }
                @Override public ScriptType scriptType() { return ScriptType.SERVER; }
                @Override public void collect(Handle handle) {
                    // The synthetic collector was registered before this probe and has already dispatched this
                    // round's declarations, yet the store must still hold the old active value: collection
                    // dispatch has no side effect on live targets.
                    seenDuringCandidate.add(harness.applier.targets.get("synthetic:alpha"));
                }
            };
            harness.root.registerDomainCollector(probe);
            harness.writeServerScript("mod.js", "ModEvents.modification(e => e.modify('synthetic:alpha', p => p.setPower(8)))");
            harness.root.reload(ScriptType.SERVER);

            assertEquals(List.of(7), seenDuringCandidate,
                    "during candidate construction the live store still holds the OLD active values");
            assertEquals(8, harness.applier.targets.get("synthetic:alpha"), "commit applies the new plan");
        }
    }

    // ==================== Collection or preflight failure keeps the old active plan ====================

    @Test
    void collectionListenerErrorFailsTheCandidateAndKeepsOldActivePlan() throws Exception {
        try (Harness harness = new Harness()) {
            harness.applier.seed("synthetic:alpha", 1);
            harness.writeServerScript("mod.js", "ModEvents.modification(e => e.modify('synthetic:alpha', p => p.setPower(7)))");
            harness.root.scriptManagerOf(ScriptType.SERVER).loadScripts();
            harness.root.reload(ScriptType.SERVER);
            assertEquals(1, harness.applier.applyCount);
            assertEquals(7, harness.applier.targets.get("synthetic:alpha"));

            // New round: the collection callback throws, so DOMAIN_PLAN fails.
            harness.writeServerScript("mod.js", "ModEvents.modification(e => { throw new Error('boom in collector') })");
            NekoReloadException failure = assertThrows(NekoReloadException.class,
                    () -> harness.root.reload(ScriptType.SERVER));

            assertEquals(ReloadPhase.DOMAIN_PLAN, failure.report().phase());
            assertTrue(failure.report().domain().startsWith("domain-collect:synthetic-modification"),
                    "domain=" + failure.report().domain());
            assertEquals(1, harness.applier.applyCount, "failed candidate must not apply anything");
            assertEquals(7, harness.applier.targets.get("synthetic:alpha"),
                    "old active modifications keep serving (no restore, no partial apply)");
        }
    }

    @Test
    void planMarkedFailedDuringCollectionFailsJointPreflightWithoutPartialApply() throws Exception {
        try (Harness harness = new Harness()) {
            harness.applier.seed("synthetic:alpha", 1);
            // An unknown target with a well-formed id: collection records the declaration, the Adapter
            // preflight rejects it, and the joint STATE_PLAN preflight fails with the domain attributed
            // to the domain plan. Nothing is applied partially.
            harness.writeServerScript("mod.js", "ModEvents.modification(e => e.modify('synthetic:missing', p => p.setPower(5)))");
            harness.root.scriptManagerOf(ScriptType.SERVER).loadScripts();

            NekoReloadException failure = assertThrows(NekoReloadException.class,
                    () -> harness.root.reload(ScriptType.SERVER));
            assertEquals(ReloadPhase.STATE_PLAN, failure.report().phase(),
                    "unknown target is rejected at the joint preflight (STATE_PLAN)");
            assertTrue(failure.report().domain().contains("state-plan-preflight"),
                    "domain=" + failure.report().domain());

            // A range rejection (power > 100) also goes through the joint preflight, not through collection
            // or apply.
            harness.writeServerScript("mod.js", "ModEvents.modification(e => e.modify('synthetic:alpha', p => p.setPower(500)))");
            NekoReloadException second = assertThrows(NekoReloadException.class,
                    () -> harness.root.reload(ScriptType.SERVER));
            assertEquals(ReloadPhase.STATE_PLAN, second.report().phase(),
                    "range validation joins the preflight, not the apply path");

            // A failure mark recorded during collection (invalid target text with no namespace): the post
            // phase is not interrupted, which keeps the existing observable order, but the plan must fail
            // at the joint preflight. Leaving it silently stale is not a success.
            harness.writeServerScript("mod.js", "ModEvents.modification(e => e.modify('no-namespace', p => p.setPower(5)))");
            NekoReloadException third = assertThrows(NekoReloadException.class,
                    () -> harness.root.reload(ScriptType.SERVER));
            assertEquals(ReloadPhase.STATE_PLAN, third.report().phase());
            assertTrue(third.report().domain().contains("state-plan-preflight:" + ModificationCandidatePlan.DOMAIN),
                    "collection failure marker surfaces at the joint preflight (domain="
                            + third.report().domain() + ")");

            assertEquals(0, harness.applier.applyCount, "blocked batches must never apply");
            assertEquals(1, harness.applier.targets.get("synthetic:alpha"));
        }
    }

    // ==================== Removed declarations restore the baseline; an empty plan still commits ====================

    @Test
    void removedDeclarationsRestoreBaselinesViaAnEmptyPlanAtCommit() throws Exception {
        try (Harness harness = new Harness()) {
            harness.applier.seed("synthetic:alpha", 1);
            harness.writeServerScript("mod.js", "ModEvents.modification(e => e.modify('synthetic:alpha', p => p.setPower(7)))");
            harness.root.scriptManagerOf(ScriptType.SERVER).loadScripts();
            harness.root.reload(ScriptType.SERVER);
            assertEquals(7, harness.applier.targets.get("synthetic:alpha"));

            // The declaration is gone: the empty plan still applies at commit and restores the baseline
            // instead of leaving it stale.
            harness.writeServerScript("mod.js", "global.noModification = true");
            harness.root.reload(ScriptType.SERVER);

            assertEquals(2, harness.applier.applyCount, "empty plan still applies at commit (restore path)");
            assertEquals(1, harness.applier.targets.get("synthetic:alpha"),
                    "removed declaration restores the NekoJS-held baseline (no silent stale)");
        }
    }

    // ==================== Joint preflight and joint commit with the global write set ====================

    @Test
    void domainPlanAndGlobalWriteSetCommitJointlyOrNotAtAll() throws Exception {
        try (Harness harness = new Harness()) {
            harness.applier.seed("synthetic:alpha", 1);
            // One script does both a top-level global write and a modification declaration. Success case:
            // both are published together.
            harness.writeServerScript("mod.js", """
                    global.answer = 42
                    ModEvents.modification(e => e.modify('synthetic:alpha', p => p.setPower(7)))
                    """);
            harness.root.scriptManagerOf(ScriptType.SERVER).loadScripts();
            harness.root.reload(ScriptType.SERVER);
            assertEquals(42, harness.root.globalState().storeFor(ScriptType.SERVER).get("answer"));
            assertEquals(7, harness.applier.targets.get("synthetic:alpha"));

            // Failure case: the Adapter preflight rejects, so the global write set is not published either
            // and the old active plan stays.
            harness.applier.failPreflightWith = "adapter cannot prove recoverability";
            harness.writeServerScript("mod.js", """
                    global.answer = 99
                    ModEvents.modification(e => e.modify('synthetic:alpha', p => p.setPower(8)))
                    """);
            NekoReloadException failure = assertThrows(NekoReloadException.class,
                    () -> harness.root.reload(ScriptType.SERVER));

            assertEquals(ReloadPhase.STATE_PLAN, failure.report().phase());
            assertTrue(failure.report().domain().contains(ModificationCandidatePlan.DOMAIN),
                    "domain=" + failure.report().domain());
            assertEquals(42, harness.root.globalState().storeFor(ScriptType.SERVER).get("answer"),
                    "global write set must not publish when the domain plan preflight fails");
            assertEquals(7, harness.applier.targets.get("synthetic:alpha"),
                    "old active plan keeps serving when the batch is blocked");
        }
    }

    @Test
    void dispatchKeyedBusIsRejectedByDomainCollectionInsteadOfSilentlyBroadcasting() throws Exception {
        // A key-dispatched bus needs the key to decide which listeners receive the event, and collection
        // dispatch has no key context. Reject it explicitly with UnsupportedOperationException instead
        // of silently ignoring the key and broadcasting to every listener.
        try (Harness harness = new Harness()) {
            harness.applier.seed("synthetic:alpha", 1);
            harness.root.registerDomainCollector(new CandidateDomainCollector() {
                @Override public String domain() { return "dispatch-probe"; }
                @Override public ScriptType scriptType() { return ScriptType.SERVER; }
                @Override public void collect(Handle handle) {
                    handle.dispatch(DISPATCHED, new CollectingEvent(new ModificationCandidatePlan(harness.applier)));
                }
            });
            harness.writeServerScript("mod.js", "global.noModification = true");

            NekoReloadException failure = assertThrows(NekoReloadException.class,
                    () -> harness.root.reload(ScriptType.SERVER));

            assertEquals(ReloadPhase.DOMAIN_PLAN, failure.report().phase());
            assertTrue(failure.report().domain().startsWith("domain-collect:dispatch-probe"),
                    "domain=" + failure.report().domain());
            assertEquals(0, harness.applier.applyCount, "rejected collection must not apply anything");
            assertEquals(1, harness.applier.targets.get("synthetic:alpha"));
        }
    }

    @Test
    void rootCloseClosesCollectorsSoIndependentRootsDoNotBleed() throws Exception {
        try (Harness harness = new Harness()) {
            harness.applier.seed("synthetic:alpha", 1);
            harness.writeServerScript("mod.js", "ModEvents.modification(e => e.modify('synthetic:alpha', p => p.setPower(7)))");
            harness.root.scriptManagerOf(ScriptType.SERVER).loadScripts();
            harness.root.reload(ScriptType.SERVER);
            assertEquals(7, harness.applier.targets.get("synthetic:alpha"));

            // root close closes AutoCloseable collectors, restoring the real domain baseline, and clears the
            // collector registry.
            List<String> closed = new ArrayList<>();
            harness.root.registerDomainCollector(new AutoCloseableCandidateDomainCollector(closed));
            harness.root.closeSilently();
            assertEquals(List.of("closed"), closed, "root close must close AutoCloseable collectors (AC5)");
            assertNull(harness.root.domainCollector("close-probe"), "closed root clears its collector registry");
        }
    }

    /** Minimal AutoCloseable collector that records close. */
    static final class AutoCloseableCandidateDomainCollector implements CandidateDomainCollector, AutoCloseable {
        private final List<String> closed;

        AutoCloseableCandidateDomainCollector(List<String> closed) {
            this.closed = closed;
        }

        @Override public String domain() { return "close-probe"; }
        @Override public ScriptType scriptType() { return ScriptType.SERVER; }
        @Override public void collect(Handle handle) { }
        @Override public void close() { closed.add("closed"); }
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
}
