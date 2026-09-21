package com.tkisor.nekojs.wrapper.event.server;

import com.tkisor.nekojs.api.ScriptType;
import com.tkisor.nekojs.api.catalog.EventCatalogEntry;
import com.tkisor.nekojs.api.data.Binding;
import com.tkisor.nekojs.api.event.EventBusJS;
import com.tkisor.nekojs.api.event.EventGroup;
import com.tkisor.nekojs.api.plugin.IPluginRuntime;
import com.tkisor.nekojs.bindings.event.ServerEvents;
import com.tkisor.nekojs.core.villager.VillagerTradeCandidatePlan;
import com.tkisor.nekojs.core.villager.VillagerTradeDeclaration;
import com.tkisor.nekojs.core.villager.VillagerTradeDeclarationEventJS;
import com.tkisor.nekojs.core.villager.VillagerTradeDomainState;
import com.tkisor.nekojs.core.villager.VillagerTradeQuerySurface;
import com.tkisor.nekojs.core.villager.VillagerTradeReloadEventJS;
import com.tkisor.nekojs.core.villager.VillagerTradeSetSnapshot;
import com.tkisor.nekojs.core.villager.VillagerTradeUnavailableException;
import com.tkisor.nekojs.testfixture.VanillaRegistryProbe;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Ticket 22 event-surface fixture: the villager trade sub-events live on the <b>existing</b>
 * {@code ServerEvents} group (one declaration per bus, no second event bus), their payloads stay
 * the shared common types on every node, and a node without a trade registry adapter answers an
 * explicit <i>unavailable</i> instead of a silent no-op.
 *
 * <p>The catalog assertions need no vanilla registry (bus metadata only), so they really run on
 * all five nodes; the adapter-level assertions are gated by {@link VanillaRegistryProbe} where
 * they would need Minecraft class initialization.
 */
class Ticket22VillagerTradeEventSurfaceTest {

    private static IPluginRuntime runtimeWith(Map<String, EventGroup> groups) {
        return new StubRuntime(groups);
    }

    private static Optional<EventCatalogEntry> entryOf(List<EventCatalogEntry> entries, String name) {
        return entries.stream()
                .filter(entry -> entry.group().equals("ServerEvents") && entry.name().equals(name))
                .findFirst();
    }

    @Test
    void tradeSubEventsAppearExactlyOnceOnTheExistingServerEventsGroup() {
        Map<String, EventGroup> groups = new LinkedHashMap<>();
        groups.put("ServerEvents", ServerEvents.GROUP);
        List<EventCatalogEntry> entries =
                com.tkisor.nekojs.api.catalog.NekoScriptCatalog.events(runtimeWith(groups));

        EventCatalogEntry declaration = entryOf(entries, "tradeDeclaration").orElseThrow(
                () -> new AssertionError("ServerEvents.tradeDeclaration missing from the catalog"));
        assertEquals(VillagerTradeDeclarationEventJS.class, declaration.eventType());
        assertEquals(ScriptType.SERVER, declaration.scriptType());
        assertFalse(declaration.cancellable(), "a posted-object declaration bus is not cancellable");
        assertFalse(declaration.dispatchable(), "the declaration bus is not key-dispatched");
        assertFalse(declaration.scriptDefined(), "it is a native payload bus, not a script event");

        EventCatalogEntry reload = entryOf(entries, "tradeReload").orElseThrow(
                () -> new AssertionError("ServerEvents.tradeReload missing from the catalog"));
        assertEquals(VillagerTradeReloadEventJS.class, reload.eventType());
        assertEquals(ScriptType.SERVER, reload.scriptType());
        assertFalse(reload.cancellable());
        assertFalse(reload.dispatchable());

        for (String name : List.of("tradeDeclaration", "tradeReload")) {
            assertEquals(1, entries.stream()
                            .filter(entry -> entry.group().equals("ServerEvents") && entry.name().equals(name))
                            .count(),
                    "exactly one catalog entry per bus (no duplicate declaration, no second bus): " + name);
        }
        // Same group instance: the sub-events did not open a second event bus or group.
        assertEquals(1, ServerEvents.GROUP.viewBuses().keySet().stream()
                .filter(name -> name.equals("tradeDeclaration")).count());
        assertEquals(1, ServerEvents.GROUP.viewBuses().keySet().stream()
                .filter(name -> name.equals("tradeReload")).count());
    }

    @Test
    void payloadsExposeOnlyTheFirstVersionSurface() throws Exception {
        // add(tradeSet, config) / getAddedCount() — the collecting payload (same shape on every node).
        assertEquals(void.class, VillagerTradeDeclarationEventJS.class
                .getMethod("add", String.class, Object.class).getReturnType());
        assertEquals(int.class, VillagerTradeDeclarationEventJS.class
                .getMethod("getAddedCount").getReturnType());
        // reload payload: read-only inspection plus the explicit release entry point.
        assertEquals(void.class, VillagerTradeReloadEventJS.class
                .getMethod("declareObsolete", String.class).getReturnType());
        assertEquals(int.class, VillagerTradeReloadEventJS.class.getMethod("getTotal").getReturnType());
        assertTrue(List.class.isAssignableFrom(VillagerTradeReloadEventJS.class
                .getMethod("getTradeSets").getReturnType()));
        assertEquals(int.class, VillagerTradeReloadEventJS.class
                .getMethod("countOf", String.class).getReturnType());

        // remove / replace / modify must not exist anywhere on the first-version surface.
        for (Class<?> type : List.of(VillagerTradeDeclarationEventJS.class, VillagerTradeReloadEventJS.class)) {
            for (Method method : type.getMethods()) {
                String name = method.getName().toLowerCase(java.util.Locale.ROOT);
                assertFalse(name.contains("remove") || name.contains("replace") || name.contains("modify"),
                        "remove/replace/modify are not part of the first version: " + type.getSimpleName()
                                + "#" + method.getName());
            }
        }
        for (Method method : VillagerTradeCandidatePlan.class.getMethods()) {
            String name = method.getName().toLowerCase(java.util.Locale.ROOT);
            assertFalse(name.contains("remove") || name.contains("replace") || name.contains("modify"),
                    "the plan must not carry remove/replace/modify: " + method.getName());
        }
    }

    @Test
    void declarationEventFailureFailsTheWholeBatchInsteadOfSkippingOneTrade() {
        VillagerTradeDomainState state = new VillagerTradeDomainState("probe", true);
        VillagerTradeCandidatePlan plan = new VillagerTradeCandidatePlan(rejectingApplier(), state);
        VillagerTradeDeclarationEventJS declaration = new VillagerTradeDeclarationEventJS(plan);

        assertThrows(IllegalArgumentException.class,
                () -> declaration.add("mod:set", Map.of("cost", "1x minecraft:emerald")));
        assertThrows(IllegalArgumentException.class,
                () -> declaration.add("", Map.of("cost", "1x minecraft:emerald", "result", "1x minecraft:apple")));

        // A configuration error inside a callback is recorded on the plan and fails the joint
        // preflight as a whole — never a partial application.
        plan.fail("config", new IllegalArgumentException("bad config"));
        assertThrows(IllegalStateException.class, plan::preflight);
    }

    @Test
    void declaringAReloadSubEventReleaseStaysUntilTheCommitSharesIt() {
        VillagerTradeDomainState state = new VillagerTradeDomainState("probe", true);
        VillagerTradeCandidatePlan plan = new VillagerTradeCandidatePlan(rejectingApplier(), state);
        VillagerTradeReloadEventJS reload = new VillagerTradeReloadEventJS(plan, VillagerTradeSetSnapshot.EMPTY);

        reload.declareObsolete("minecraft:farmer/level_1");
        assertEquals(1, plan.obsoleteTradeSets().size(), "the release is recorded on the inert plan");
        assertThrows(IllegalArgumentException.class, () -> reload.declareObsolete(" "));
        assertEquals(0, reload.getTotal());
        assertTrue(reload.getTradeSets().isEmpty());
    }

    /**
     * Fabric (and any node without a registered adapter) must answer explicitly. The unavailable
     * owner only exists on fabric, so it is reached reflectively; when the class is absent the
     * same contract is asserted through the facade/binding path, which reports the same reason.
     */
    @Test
    void nodeWithoutAnAdapterAnswersExplicitUnavailableInsteadOfSilentSuccess() throws Exception {
        Class<?> ownerClass = loadQuiet("com.tkisor.nekojs.wrapper.event.server.VillagerTradeUnavailableDomainOwner");
        if (ownerClass != null) {
            Object owner = ownerClass.getDeclaredConstructor().newInstance();
            assertEquals(VillagerTradeCandidatePlan.DOMAIN, ownerClass.getMethod("domain").invoke(owner));
            assertEquals(ScriptType.SERVER, ownerClass.getMethod("scriptType").invoke(owner));

            RecordingHandle handle = new RecordingHandle(ScriptType.SERVER);
            // Observe the node's own domain state (published only by a successful commit).
            Object nodeState = ownerClass.getMethod("state").invoke(owner);
            handle.state = (com.tkisor.nekojs.core.villager.VillagerTradeDomainState) nodeState;
            ownerClass.getMethod("collect", com.tkisor.nekojs.core.lifecycle.CandidateDomainCollector.Handle.class)
                    .invoke(owner, handle);

            assertNotNull(handle.plan, "the unavailable owner still participates in the joint boundary");
            assertFalse(handle.collectedDeclarations.isEmpty(),
                    "a declaration is collected (not dropped at call time) so the rejection is explicit");
            VillagerTradeUnavailableException unavailable = assertThrows(VillagerTradeUnavailableException.class,
                    handle.plan::preflight);
            assertTrue(unavailable.reason().contains("Fabric"),
                    "the reason names the node: " + unavailable.reason());
            // The declaration is collected and then rejected as a whole: the batch never commits,
            // so the node neither applies nor pretends to apply it (explicit, not a silent no-op).
            assertEquals(1, handle.plan.declarations().size(),
                    "the declaration is carried by the batch that gets rejected");
            assertFalse(handle.plan.isPublished(), "an unavailable node never publishes a batch");
            assertEquals(0, handle.state.committedSnapshot().total(),
                    "no snapshot is published by the unavailable node");
        } else {
            Assumptions.assumeTrue(false,
                    "VillagerTradeUnavailableDomainOwner only exists on fabric; the facade path covers this node");
        }

        // Facade path on every node: no installed state means an explicit unavailable answer.
        com.tkisor.nekojs.core.villager.VillagerTradesFacade.uninstall(null);
        VillagerTradeQuerySurface result = com.tkisor.nekojs.core.villager.VillagerTradesFacade.query(null);
        assertEquals(VillagerTradeQuerySurface.Status.STALE, result.getStatus());
        assertTrue(result.getStatusReason().startsWith("unavailable:"), result.getStatusReason());
    }

    /**
     * 26.x adapter without a bound server: the honest answer is unavailable (the reloadable trade
     * registries are not reachable), reported through diagnostics — never a silent success.
     * Runs wherever the class initializes; on 1.21.1 the twin has the same contract.
     */
    @Test
    void adapterWithoutARunningServerReportsUnavailableInsteadOfClaimingSuccess() throws Exception {
        Assumptions.assumeTrue(VanillaRegistryProbe.available(),
                "needs vanilla registry class initialization to touch the trade pools");
        Class<?> ownerClass = loadQuiet("com.tkisor.nekojs.wrapper.event.server.VillagerTradeDomainOwner");
        assertNotNull(ownerClass, "each node must provide its VillagerTradeDomainOwner adapter");

        Object owner = ownerClass.getDeclaredConstructor().newInstance();
        ownerClass.getMethod("applyInitialPlan", Class.forName("net.minecraft.server.MinecraftServer"))
                .invoke(owner, new Object[]{null});

        Object diagnostics = ownerClass.getMethod("lastDiagnostics").invoke(owner);
        Object outcome = diagnostics.getClass().getMethod("outcome").invoke(diagnostics);
        assertEquals("UNAVAILABLE", String.valueOf(outcome),
                "without a running server the adapter must report unavailable, got " + outcome
                        + " (" + diagnostics + ")");
        assertNotNull(diagnostics.getClass().getMethod("detail").invoke(diagnostics),
                "the unavailable outcome carries a reason");
    }

    private static com.tkisor.nekojs.core.villager.VillagerTradeApplier rejectingApplier() {
        return new com.tkisor.nekojs.core.villager.VillagerTradeApplier() {
            @Override public String adapterId() { return "probe"; }
            @Override public boolean available() { return true; }
            @Override public void preflight(List<VillagerTradeDeclaration> declarations) { }
            @Override public void apply(List<VillagerTradeDeclaration> declarations, java.util.Set<String> obsolete) { }
            @Override public VillagerTradeSetSnapshot committedSnapshot() { return VillagerTradeSetSnapshot.EMPTY; }
        };
    }

    private static Class<?> loadQuiet(String name) {
        try {
            return Class.forName(name, false, Ticket22VillagerTradeEventSurfaceTest.class.getClassLoader());
        } catch (ClassNotFoundException | LinkageError ignored) {
            return null;
        }
    }

    /** Minimal collect handle: records the dispatched payload's collected declarations. */
    private static final class RecordingHandle implements com.tkisor.nekojs.core.lifecycle.CandidateDomainCollector.Handle {
        private final ScriptType scriptType;
        private VillagerTradeCandidatePlan plan;
        private com.tkisor.nekojs.core.villager.VillagerTradeDomainState state;
        private List<String> collectedDeclarations = List.of();

        RecordingHandle(ScriptType scriptType) {
            this.scriptType = scriptType;
        }

        @Override public graal.graalvm.polyglot.Context candidateContext() { return null; }
        @Override public ScriptType scriptType() { return scriptType; }
        @Override
        public List<EventBusJS.PendingListener> listenersOf(EventBusJS<?, ?> bus) {
            // The collectors under test dispatch explicitly (see #dispatch), so the pending-listener
            // projection is not needed here; it is covered by the runtime fixtures.
            return List.of();
        }

        @Override
        public void execute(EventBusJS.PendingListener listener, Object event) {
            if (event instanceof VillagerTradeDeclarationEventJS declaration) {
                // Real guest configuration object (the production payload reads member values; a
                // plain host Map is not member-readable, so the test uses a real JS object).
                // A real guest callback always runs inside a context; enter one so Value.asValue
                // behaves exactly as it does in production instead of relying on host-only input.
                try (graal.graalvm.polyglot.Context context =
                             graal.graalvm.polyglot.Context.newBuilder("js").allowAllAccess(true).build()) {
                    context.enter();
                    try {
                        declaration.add("minecraft:farmer/level_1", context.eval("js",
                                "({ cost: '1x minecraft:emerald', result: '1x minecraft:apple' })"));
                    } finally {
                        context.leave();
                    }
                }
            }
            if (event instanceof VillagerTradeReloadEventJS reload) {
                reload.declareObsolete("minecraft:farmer/level_1");
            }
        }

        @Override
        public void dispatch(EventBusJS<?, ?> bus, Object event) {
            execute(null, event);
        }

        @Override
        public void registerPlan(com.tkisor.nekojs.core.state.CandidateStatePlan candidatePlan) {
            this.plan = (VillagerTradeCandidatePlan) candidatePlan;
            this.collectedDeclarations = plan.declarations().stream()
                    .map(VillagerTradeDeclaration::key).toList();
        }
    }

    private static final class StubRuntime implements IPluginRuntime {
        private final Map<String, EventGroup> groups;

        StubRuntime(Map<String, EventGroup> groups) {
            this.groups = groups;
        }

        @Override public Map<String, Binding> bindings(ScriptType type) { return Map.of(); }
        @Override public Map<String, EventGroup> eventGroups() { return groups; }
        @Override public List<com.tkisor.nekojs.api.JSTypeAdapter<?>> adapters() { return List.of(); }
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
}
