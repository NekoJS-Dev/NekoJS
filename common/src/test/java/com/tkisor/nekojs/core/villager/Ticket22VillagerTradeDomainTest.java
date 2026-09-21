package com.tkisor.nekojs.core.villager;

import com.tkisor.nekojs.api.JSTypeAdapter;
import com.tkisor.nekojs.api.ScriptType;
import com.tkisor.nekojs.api.data.Binding;
import com.tkisor.nekojs.api.event.EventBusJS;
import com.tkisor.nekojs.api.event.EventGroup;
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
import com.tkisor.nekojs.core.state.CandidateStatePlan;
import com.tkisor.nekojs.script.ScriptTypeEnv;
import com.tkisor.nekojs.script.prop.ScriptProperty;
import com.tkisor.nekojs.script.prop.ScriptPropertyRegistry;
import com.tkisor.nekojs.testfixture.TestPlatformInit;
import graal.graalvm.polyglot.Context;
import graal.graalvm.polyglot.Engine;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Ticket 22 domain mechanism fixture (common layer, zero Minecraft): the candidate collection
 * phase, commit-time application, failure retention and the generation-bound read-only query.
 *
 * <p>It drives the real {@code NekoRuntimeRoot} + real Graal pipeline with a <b>synthetic</b>
 * trade applier that implements the same contract as the platform adapters (preflight rejects a
 * whole batch; apply owns the complete desired state; the committed snapshot is the read model).
 * The real Minecraft adapters (26.x trade registries, 1.21.1 classic pools) belong to the
 * version-tree fixtures; what is pinned here is the shared contract: inert collection, joint
 * failure with the old active kept, generation/stale query semantics and explicit unavailability.
 */
class Ticket22VillagerTradeDomainTest {

    /** Synthetic event group the scripts subscribe to (the production surface is ServerEvents). */
    static final EventGroup GROUP = EventGroup.of("TradeEvents");
    static final EventBusJS<VillagerTradeDeclarationEventJS, Void> DECLARATION =
            GROUP.server("declaration", VillagerTradeDeclarationEventJS.class);
    static final EventBusJS<VillagerTradeReloadEventJS, Void> RELOAD =
            GROUP.server("reload", VillagerTradeReloadEventJS.class);
    static final EventBusJS<ProbeEvent, Void> PROBE = GROUP.server("probe", ProbeEvent.class);

    /** Payload that reads the generation-bound query from inside a real script callback. */
    public static final class ProbeEvent {
        private final VillagerTradeDomainState state;
        private final List<Context> contexts;
        private final List<VillagerTradeQuerySurface> results;

        ProbeEvent(VillagerTradeDomainState state, List<Context> contexts, List<VillagerTradeQuerySurface> results) {
            this.state = state;
            this.contexts = contexts;
            this.results = results;
        }

        public void capture() {
            Context current = Context.getCurrent();
            contexts.add(current);
            results.add(state.query(current));
        }
    }

    /** Synthetic adapter: real contract, no Minecraft type. */
    static final class SyntheticTradeApplier implements VillagerTradeApplier {
        final Map<String, Integer> live = new LinkedHashMap<>();
        final Map<String, Integer> baseline = new LinkedHashMap<>();
        int preflightCount;
        int applyCount;
        String rejectWith;
        boolean available = true;

        void seed(String tradeSetId, int count) {
            live.put(tradeSetId, count);
            baseline.put(tradeSetId, count);
        }

        @Override
        public String adapterId() {
            return "synthetic-trades";
        }

        @Override
        public boolean available() {
            return available;
        }

        @Override
        public void preflight(List<VillagerTradeDeclaration> declarations) {
            preflightCount++;
            if (!available) {
                throw new VillagerTradeUnavailableException("synthetic node is unavailable");
            }
            if (rejectWith != null) {
                throw new IllegalArgumentException(rejectWith);
            }
            for (VillagerTradeDeclaration declaration : declarations) {
                if (!baseline.containsKey(declaration.tradeSetId())) {
                    throw new IllegalArgumentException("unknown trade set: " + declaration.tradeSetId());
                }
            }
        }

        @Override
        public void apply(List<VillagerTradeDeclaration> declarations, Set<String> obsoleteTradeSets) {
            applyCount++;
            live.clear();
            live.putAll(baseline);
            Map<String, Integer> counts = new LinkedHashMap<>();
            for (VillagerTradeDeclaration declaration : declarations) {
                live.merge(declaration.tradeSetId(), 1, Integer::sum);
                counts.merge(declaration.tradeSetId(), 1, Integer::sum);
            }
            lastSnapshot = VillagerTradeSetSnapshot.of(7L, counts);
        }

        private VillagerTradeSetSnapshot lastSnapshot = VillagerTradeSetSnapshot.EMPTY;

        @Override
        public VillagerTradeSetSnapshot committedSnapshot() {
            return lastSnapshot;
        }
    }

    /** Collector mirroring the production owner: dispatch both sub-events, register the plan. */
    static final class SyntheticCollector implements CandidateDomainCollector, AutoCloseable {
        final SyntheticTradeApplier applier;
        final VillagerTradeDomainState state;
        final List<Context> probeContexts = new ArrayList<>();
        final List<VillagerTradeQuerySurface> probeResults = new ArrayList<>();

        SyntheticCollector(SyntheticTradeApplier applier, VillagerTradeDomainState state) {
            this.applier = applier;
            this.state = state;
        }

        @Override
        public String domain() {
            return VillagerTradeCandidatePlan.DOMAIN;
        }

        @Override
        public ScriptType scriptType() {
            return ScriptType.SERVER;
        }

        @Override
        public void collect(Handle handle) {
            VillagerTradeCandidatePlan plan = new VillagerTradeCandidatePlan(applier, state);
            handle.dispatch(DECLARATION, new VillagerTradeDeclarationEventJS(plan));
            handle.dispatch(RELOAD, new VillagerTradeReloadEventJS(plan, state.committedSnapshot()));
            handle.dispatch(PROBE, new ProbeEvent(state, probeContexts, probeResults));
            handle.registerPlan(plan);
        }

        /** Root close path (production owners are AutoCloseable too): drop the domain records. */
        @Override
        public void close() {
            state.reset();
        }
    }

    static final class StubPluginRuntime implements IPluginRuntime {
        final SyntheticCollector collector;

        StubPluginRuntime(SyntheticCollector collector) {
            this.collector = collector;
        }

        @Override public Map<String, Binding> bindings(ScriptType type) { return Map.of(); }
        @Override public Map<String, EventGroup> eventGroups() { return Map.of("TradeEvents", GROUP); }
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

    /** Real root + real Graal pipeline + synthetic trade adapter. */
    static final class Harness implements AutoCloseable {
        final Engine engine = Engine.newBuilder().build();
        final SyntheticTradeApplier applier = new SyntheticTradeApplier();
        final VillagerTradeDomainState state = new VillagerTradeDomainState("synthetic-trades", true);
        final SyntheticCollector collector = new SyntheticCollector(applier, state);
        final StubPluginRuntime pluginRuntime = new StubPluginRuntime(collector);
        final DefaultScriptEventBridge bridge = new DefaultScriptEventBridge(null);
        final NekoRuntimeRoot root;

        Harness() {
            bridge.setPluginRuntime(pluginRuntime);
            NekoJSPaths paths = NekoJSPaths.get();
            SandboxConfig config = new SandboxConfig(false, false, false, false, true, true, false, true, 5, 100_000L, 0);
            DefaultErrorTracker tracker = new DefaultErrorTracker(paths, config);
            NekoCoreContext core = new NekoCoreContext(engine, config, new ClassFilter(config), tracker);
            ScriptCompilerRegistry compilers = ScriptCompilerRegistry.createRuntimeRegistry();
            NekoModulePipelineCache cache =
                    com.tkisor.nekojs.testfixture.NekoModuleTestFixtures.newCache(paths, compilers, config);
            NekoSandboxFactory sandboxFactory = new NekoSandboxFactory(core, paths, compilers, pluginRuntime, cache);
            root = new NekoRuntimeRoot(core, pluginRuntime, bridge, newPropertyRegistry(), sandboxFactory, cache);
            root.registerDomainCollector(collector);
            root.createScriptManager(ScriptType.SERVER).discoverScripts();
        }

        void writeServerScript(String name, String source) throws Exception {
            Files.writeString(ScriptTypeEnv.scriptsDir(ScriptType.SERVER).resolve(name), source);
        }

        void load() {
            root.scriptManagerOf(ScriptType.SERVER).loadScripts();
        }

        /**
         * Captures the currently <b>active</b> generation token by reading the query from inside a
         * real active-bus callback (a script's own generation). Uses a dedicated probe event so it
         * does not disturb the DOMAIN_PLAN probe records.
         */
        Context getActiveToken() {
            List<Context> tokens = new ArrayList<>();
            List<VillagerTradeQuerySurface> views = new ArrayList<>();
            PROBE.post(new ProbeEvent(state, tokens, views));
            assertEquals(1, tokens.size(), "the active bus must reach the probe listener");
            assertEquals(VillagerTradeQuerySurface.Status.ACTIVE, views.get(0).getStatus(),
                    "the token captured from the active generation must read active");
            return tokens.get(0);
        }

        @Override
        public void close() {
            root.closeSilently();
            engine.close();
        }
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

    /** Probe listener executed inside a real script callback (captures the generation token). */
    private static final String PROBE_SCRIPT = "TradeEvents.probe(event => event.capture())";

    /**
     * Reads the query from inside an <b>active-generation</b> script callback: posting to the
     * live bus reaches the listeners of the currently active generation, which is exactly what a
     * script does at runtime. (The DOMAIN_PLAN probe above runs inside the candidate, which must
     * report stale.)
     */
    private static VillagerTradeQuerySurface readFromActiveGeneration(Harness harness) {
        int before = harness.collector.probeResults.size();
        PROBE.post(new ProbeEvent(harness.state, harness.collector.probeContexts, harness.collector.probeResults));
        assertEquals(before + 1, harness.collector.probeResults.size(),
                "the active bus must reach the probe listener of the active generation");
        return harness.collector.probeResults.get(harness.collector.probeResults.size() - 1);
    }

    private static String declarationScript(String tradeSet, int count) {
        return """
                TradeEvents.declaration(event => {
                  event.add('%s', { cost: '1x minecraft:emerald', result: '5x minecraft:apple', maxUses: 12, xp: 2 })
                })
                """.formatted(tradeSet, count);
    }

    // ==================== AC4: collection is inert, the commit applies exactly once ====================

    @Test
    void candidateCollectionIsInertAndCommitAppliesTheBatchExactlyOnce() throws Exception {
        try (Harness harness = new Harness()) {
            harness.applier.seed("synthetic:set", 1);
            harness.writeServerScript("trades.js", declarationScript("synthetic:set", 1));
            harness.load();
            assertEquals(0, harness.applier.applyCount, "the initial non-transactional load runs no domain collection");
            assertEquals(1, harness.applier.live.get("synthetic:set"), "live state is untouched before any commit");

            List<Map<String, Integer>> seenDuringCandidate = new ArrayList<>();
            harness.root.registerDomainCollector(new CandidateDomainCollector() {
                @Override public String domain() { return "inert-probe"; }
                @Override public ScriptType scriptType() { return ScriptType.SERVER; }
                @Override public void collect(Handle handle) {
                    seenDuringCandidate.add(new LinkedHashMap<>(harness.applier.live));
                }
            });

            harness.root.reload(ScriptType.SERVER);

            assertEquals(List.of(Map.of("synthetic:set", 1)), seenDuringCandidate,
                    "during candidate collection the live registry still holds the OLD active content");
            assertEquals(1, harness.applier.applyCount, "the batch is applied exactly once, at the commit point");
            assertEquals(2, harness.applier.live.get("synthetic:set"), "baseline 1 + the one declared trade");
        }
    }

    @Test
    void duplicateDeclarationsInOneBatchCollapseIntoOneTrade() throws Exception {
        try (Harness harness = new Harness()) {
            harness.applier.seed("synthetic:set", 0);
            harness.writeServerScript("trades.js", """
                    TradeEvents.declaration(event => {
                      event.add('synthetic:set', { cost: '1x minecraft:emerald', result: '1x minecraft:apple' })
                      event.add('synthetic:set', { cost: '1x minecraft:emerald', result: '1x minecraft:apple' })
                    })
                    """);
            harness.load();
            harness.root.reload(ScriptType.SERVER);
            assertEquals(1, harness.applier.live.get("synthetic:set"),
                    "the same declaration twice in one batch is one listing (idempotent re-declaration)");
        }
    }

    // ==================== AC4: a rejected batch keeps the old active ====================

    @Test
    void rejectedBatchKeepsOldActivePlanAndPublishesNothing() throws Exception {
        try (Harness harness = new Harness()) {
            harness.applier.seed("synthetic:set", 1);
            harness.writeServerScript("trades.js", declarationScript("synthetic:set", 1));
            harness.load();
            harness.root.reload(ScriptType.SERVER);
            assertEquals(2, harness.applier.live.get("synthetic:set"));
            long committedGeneration = harness.state.committedGeneration();

            // Unknown trade set (deferred to the adapter's joint preflight, not a partial apply).
            harness.writeServerScript("trades.js", declarationScript("synthetic:unknown", 1));
            NekoReloadException failure = assertThrows(NekoReloadException.class,
                    () -> harness.root.reload(ScriptType.SERVER));

            assertEquals(ReloadPhase.STATE_PLAN, failure.report().phase(),
                    "an unknown trade set is rejected by the joint preflight");
            assertTrue(failure.report().domain().contains(VillagerTradeCandidatePlan.DOMAIN),
                    "domain=" + failure.report().domain());
            assertEquals(1, harness.applier.applyCount, "the rejected batch must not apply anything");
            assertEquals(2, harness.applier.live.get("synthetic:set"), "old active trades keep serving");
            assertEquals(committedGeneration, harness.state.committedGeneration(),
                    "a failed candidate does not advance the domain generation");
        }
    }

    @Test
    void invalidConfigurationFailsTheWholeBatchInsteadOfSkippingOneTrade() throws Exception {
        try (Harness harness = new Harness()) {
            harness.applier.seed("synthetic:set", 0);
            harness.writeServerScript("trades.js", """
                    TradeEvents.declaration(event => {
                      event.add('synthetic:set', { cost: '1x minecraft:emerald', result: '1x minecraft:apple' })
                      event.add('synthetic:set', { cost: '1x minecraft:emerald' })
                    })
                    """);
            harness.load();

            NekoReloadException failure = assertThrows(NekoReloadException.class,
                    () -> harness.root.reload(ScriptType.SERVER));

            assertEquals(ReloadPhase.DOMAIN_PLAN, failure.report().phase(),
                    "a config error fails the collection phase (no partial mutation)");
            assertTrue(failure.report().domain().startsWith("domain-collect:" + VillagerTradeCandidatePlan.DOMAIN),
                    "domain=" + failure.report().domain());
            assertEquals(0, harness.applier.applyCount);
            assertEquals(0, harness.applier.live.get("synthetic:set"));
        }
    }

    @Test
    void unavailableNodeFailsTheBatchExplicitlyInsteadOfSilentlySucceeding() throws Exception {
        try (Harness harness = new Harness()) {
            harness.applier.available = false;
            harness.applier.seed("synthetic:set", 0);
            harness.writeServerScript("trades.js", declarationScript("synthetic:set", 1));
            harness.load();

            NekoReloadException failure = assertThrows(NekoReloadException.class,
                    () -> harness.root.reload(ScriptType.SERVER));

            assertEquals(ReloadPhase.STATE_PLAN, failure.report().phase());
            assertEquals(0, harness.applier.applyCount, "an unavailable node never claims success");
            assertInstanceOf(VillagerTradeUnavailableException.class, rootCause(failure));
        }
    }

    // ==================== AC3/AC7: generation-bound query, stale tokens, unrestored records ====================

    @Test
    void committedGenerationIsReadableAndOlderTokensAreExplicitlyStale() throws Exception {
        try (Harness harness = new Harness()) {
            harness.applier.seed("synthetic:set", 1);
            harness.writeServerScript("trades.js", declarationScript("synthetic:set", 1) + "\n" + PROBE_SCRIPT);
            harness.load();
            // Probe inside the candidate (DOMAIN_PLAN): the candidate generation is not active, so
            // its own token must answer stale — a candidate never reads committed data as its own.
            harness.root.reload(ScriptType.SERVER);
            VillagerTradeQuerySurface candidateView = harness.collector.probeResults.get(harness.collector.probeResults.size() - 1);
            assertEquals(VillagerTradeQuerySurface.Status.STALE, candidateView.getStatus(),
                    "a candidate generation reads stale, not the committed snapshot");

            Context firstActiveToken = harness.getActiveToken();
            // The committed generation reads active through a real callback on the active bus.
            harness.root.reload(ScriptType.SERVER);
            VillagerTradeQuerySurface latest = readFromActiveGeneration(harness);
            assertEquals(VillagerTradeQuerySurface.Status.ACTIVE, latest.getStatus());
            assertEquals("synthetic-trades", latest.getAdapterId());
            assertEquals(1, latest.countOf("synthetic:set"));
            assertEquals(1, latest.getTotal());
            assertTrue(latest.describe().startsWith("villager-trades query: ACTIVE"));

            Context oldToken = firstActiveToken;
            assertNotNull(oldToken, "the first probe captured a real generation token");
            VillagerTradeQuerySurface stale = harness.state.query(oldToken);
            assertEquals(VillagerTradeQuerySurface.Status.STALE, stale.getStatus(),
                    "a superseded generation token must not read the new generation's data");
            assertEquals(VillagerTradeQuerySurface.Status.STALE, harness.state.query(null).getStatus(),
                    "no context at all is an explicit stale answer, not an exception");
            assertEquals(0, stale.getTotal());
            assertTrue(stale.getTradeSetIds().isEmpty());
            assertEquals(0, stale.countOf("synthetic:set"));
            assertTrue(stale.getAdapterId() == null, "a stale token exposes no adapter identity");
            assertTrue(stale.getUnrestoredListingKeys().isEmpty());
            assertTrue(stale.describe().contains("STALE"));
        }
    }

    @Test
    void declarationsDroppedByANewGenerationAreRecordedAsUnrestoredInsteadOfBeingDeleted() throws Exception {
        try (Harness harness = new Harness()) {
            harness.applier.seed("synthetic:set", 1);
            harness.writeServerScript("trades.js", declarationScript("synthetic:set", 1) + "\n" + PROBE_SCRIPT);
            harness.load();
            harness.root.reload(ScriptType.SERVER);

            // The new generation declares nothing: the previous listing stays active in the
            // adapter and is reported as unrestored (ordinary reload never deletes entries).
            harness.writeServerScript("trades.js", "global.noTrades = true\n" + PROBE_SCRIPT);
            harness.root.reload(ScriptType.SERVER);

            VillagerTradeQuerySurface latest = readFromActiveGeneration(harness);
            assertEquals(VillagerTradeQuerySurface.Status.ACTIVE, latest.getStatus());
            assertEquals(0, latest.getTotal(), "no declaration is active any more");
            assertEquals(1, latest.getUnrestoredListingKeys().size(),
                    "the dropped listing is recorded as unrestored, not physically deleted");
            assertTrue(latest.getUnrestoredListingKeys().get(0).startsWith("synthetic:set|"));
        }
    }

    @Test
    void querySurfaceExposesNoWritableOrLiveMember() {
        assertTrue(Modifier.isFinal(VillagerTradeQuerySurface.class.getModifiers()));
        for (Method method : VillagerTradeQuerySurface.class.getMethods()) {
            String name = method.getName();
            assertFalse(name.startsWith("set") || name.startsWith("put") || name.startsWith("remove")
                            || name.startsWith("clear") || name.startsWith("add"),
                    "query surface must stay read-only, found: " + name);
        }
        assertFalse(VillagerTradeSetSnapshot.class.isInterface());
    }

    @Test
    void unavailableNodeQueryReportsUnavailableInsteadOfAnEmptyActiveSnapshot() {
        VillagerTradeQuerySurface unavailable = VillagerTradeQuerySurface.unavailable("no adapter");
        assertEquals(VillagerTradeQuerySurface.Status.STALE, unavailable.getStatus());
        assertTrue(unavailable.getStatusReason().startsWith("unavailable:"),
                "reason=" + unavailable.getStatusReason());
        assertEquals(0, unavailable.getTotal());

        // No state installed at all (a node without any villager trade implementation).
        com.tkisor.nekojs.core.villager.VillagerTradesFacade.uninstall(null);
        VillagerTradeQuerySurface facadeResult =
                com.tkisor.nekojs.core.villager.VillagerTradesFacade.query(null);
        assertEquals(VillagerTradeQuerySurface.Status.STALE, facadeResult.getStatus());
        assertTrue(facadeResult.getStatusReason().startsWith("unavailable:"),
                "an absent adapter must surface explicitly, reason=" + facadeResult.getStatusReason());
    }

    @Test
    void rootCloseResetsTheDomainRecordsSoIndependentRootsDoNotBleed() throws Exception {
        try (Harness harness = new Harness()) {
            harness.applier.seed("synthetic:set", 1);
            harness.writeServerScript("trades.js", declarationScript("synthetic:set", 1));
            harness.load();
            harness.root.reload(ScriptType.SERVER);
            assertEquals(1L, harness.state.committedGeneration());

            harness.root.closeSilently();
            assertEquals(0L, harness.state.committedGeneration(), "root close clears the committed generation");
            assertEquals(0, harness.state.committedSnapshot().total());
        }
    }

    private static Throwable rootCause(Throwable error) {
        Throwable cause = error;
        while (cause.getCause() != null && cause.getCause() != cause) {
            cause = cause.getCause();
        }
        return cause;
    }
}
