package com.tkisor.nekojs.core.plugin;

import com.tkisor.nekojs.api.NekoJSPlugin;
import com.tkisor.nekojs.api.ScriptType;
import com.tkisor.nekojs.api.ScriptTypePredicate;
import com.tkisor.nekojs.api.contract.VerifiedApiContract;
import com.tkisor.nekojs.api.contract.VerifiedContractSet;
import com.tkisor.nekojs.api.data.Binding;
import com.tkisor.nekojs.api.data.BindingRegistry;
import com.tkisor.nekojs.api.plugin.OwnedPlugin;
import com.tkisor.nekojs.api.surface.ApiContributionRegistry;
import com.tkisor.nekojs.api.surface.ApiRuntimeProvider;
import com.tkisor.nekojs.api.surface.ApiSymbolId;
import com.tkisor.nekojs.api.surface.EnvironmentKey;
import com.tkisor.nekojs.api.surface.LegacyGlobalReservation;
import com.tkisor.nekojs.core.api.FrozenApiRegistrySet;
import com.tkisor.nekojs.platform.Platform;
import com.tkisor.nekojs.script.ScriptTypedValue;
import com.tkisor.nekojs.script.prop.ScriptPropertyRegistry;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Plugin bootstrap engine: extension point registration → point-priority collection → produces {@link NekoPluginRuntime}.
 *
 * <p><b>Point-priority execution order:</b> sequentially executes for each extension point:
 * {@code initializer builds accumulator → iterates plugin collectors → finisher produces results → publish → next point}.
 * Extension points are completed sequentially by registration order (built-in {@code nekojs:*} first, custom later); later registered points can
 * reference artifacts of earlier registered points in {@code initializer} / {@code collector} ({@link NekoPluginExtensionContext#result}),
 * which is the engine semantics of dependencies between extension points. Artifacts are simultaneously published to three locations: bootstrap context (for reference by later points),
 * registration handle {@link NekoPluginExtensionHandle} (for retrieval by registrant), and the result container
 * ({@code Map<pointId, artifact>}, finally handed to {@link NekoPluginRuntime}).
 */
public final class NekoPluginBootstrap {

    private NekoPluginBootstrap() {
    }

    public static NekoPluginRuntime bootstrap(List<NekoJSPlugin> plugins, ScriptPropertyRegistry scriptProperties) {
        return new NekoPluginRuntime(collect(plugins, plugin -> plugin.getClass().getName(), scriptProperties),
                null, Map.of());
    }

    public static NekoPluginRuntime bootstrapOwned(
            List<OwnedPlugin> ownedPlugins,
            ScriptPropertyRegistry scriptProperties,
            VerifiedContractSet contracts) {
        return bootstrapOwned(ownedPlugins, scriptProperties, contracts, List.of(), Map.of());
    }

    static NekoPluginRuntime bootstrapOwned(
            List<OwnedPlugin> ownedPlugins,
            ScriptPropertyRegistry scriptProperties,
            VerifiedContractSet contracts,
            List<ApiContributionRegistry> builtInContributions,
            Map<ApiSymbolId, Object> managedApiImplementations) {
        Objects.requireNonNull(ownedPlugins, "ownedPlugins");
        Objects.requireNonNull(contracts, "contracts");
        Objects.requireNonNull(builtInContributions, "builtInContributions");
        Objects.requireNonNull(managedApiImplementations, "managedApiImplementations");

        List<NekoJSPlugin> plugins = ownedPlugins.stream()
                .map(OwnedPlugin::plugin)
                .toList();
        // Addon attribution for extension-registry failures: provider
        // registration, duplicate ids, freeze-window violations and dependency errors
        // name the plugin that caused them. Owned bootstrap uses the owner identity,
        // the plain overload falls back to the plugin class name.
        Map<NekoJSPlugin, String> ownerLabels = new LinkedHashMap<>();
        for (OwnedPlugin owned : ownedPlugins) {
            ownerLabels.putIfAbsent(owned.plugin(), owned.identity().ownerId());
        }

        Map<String, Object> products = collect(plugins, ownerLabels::get, scriptProperties);

        List<ApiContributionRegistry> managedContributions = new ArrayList<>(builtInContributions);
        for (OwnedPlugin owned : ownedPlugins) {
            List<VerifiedApiContract> ownerContracts = contracts.forOwner(owned.identity().ownerId());
            if (ownerContracts.isEmpty()) {
                continue;
            }
            VerifiedContractSet ownerContractSet = VerifiedContractSet.of(
                    ownerContracts.toArray(VerifiedApiContract[]::new));
            ApiContributionRegistry registry = ApiContributionRegistry.ownedBy(
                    owned.identity(), ownerContractSet);
            owned.plugin().registerApiSurface(registry);
            managedContributions.add(registry);
        }

        List<LegacyGlobalReservation> legacyReservations = buildLegacyReservations(products);

        List<EnvironmentKey> environmentKeys = buildEnvironmentKeys();

        ApiRuntimeProvider apiRuntimeProvider = new FrozenApiRegistrySet(
                contracts, managedContributions, legacyReservations, environmentKeys);

        return new NekoPluginRuntime(products, apiRuntimeProvider, managedApiImplementations);
    }

    static ScriptTypePredicate bindingPredicate(boolean client) {
        return client
                ? ScriptTypePredicate.any()
                : ScriptTypePredicate.exact(ScriptType.CLIENT).negate();
    }

    /**
     * Registers and collects all extension points in topological order, returning a result container of {@code pointId → artifact}.
     *
     * <p>Full rebuild per bootstrap: extension point list (including built-in points) and accumulators are created new this round,
     * neither extension point instances nor accumulators carry cross-round state—consecutive bootstrap rounds (full reload) do not interfere.
     *
     * <p>Execution order (ADR-0002): performs Kahn topological sort on all {@code dependsOn} edges during freeze
     * (registration order used for same-level tie-breaking; built-in precedence is guaranteed by the graph root); fail-fast on cycles, printing the full path,
     * and fail early on freeze if dependency IDs are unregistered. Points with false environment predicates are skipped and registered,
     * allowing {@link NekoPluginExtensionContext} to distinguish between "skipped" and "incomplete".
     *
     * @param ownerLabels plugin → addon label (owner id or class name) used to attribute
     *                   extension-registry failures to the causing addon; null-safe
     *                   (a missing label falls back to the plugin class name)
     */
    static Map<String, Object> collect(List<NekoJSPlugin> plugins,
                                        java.util.function.Function<NekoJSPlugin, String> ownerLabels,
                                        ScriptPropertyRegistry scriptProperties) {
        BootstrapContext context = new BootstrapContext(Platform.isClient());
        ExtensionRegistry registry = new ExtensionRegistry();
        // ADR-0003: Built-in point definition plugins precede explicitly (via the same provider path as third-party; built-in precedence phase
        // is the engine's guarantee for the graph root, not dependent on plugin priority)
        new NekoBuiltinPointsPlugin(scriptProperties, context.client(), plugin ->
                plugin instanceof NekoCommonBuiltinPlugin ? "nekojs" : ownerLabels.apply(plugin))
                .registerPluginExtensionPoints(registry.scopedTo("nekojs:builtin-points"));
        for (NekoJSPlugin plugin : plugins) {
            if (plugin instanceof NekoPluginExtensionProvider provider) {
                String label = ownerLabels != null ? ownerLabels.apply(plugin) : null;
                if (label == null) {
                    label = plugin.getClass().getName();
                }
                provider.registerPluginExtensionPoints(registry.scopedTo(label));
            }
        }
        List<NekoPluginExtensionPoint<?, ?, ?>> extensionPoints = registry.freeze();
        context.registeredIds(extensionPoints.stream()
                .map(NekoPluginExtensionPoint::id)
                .collect(Collectors.toList()));

        Map<String, Object> products = new LinkedHashMap<>();
        for (NekoPluginExtensionPoint<?, ?, ?> extensionPoint : extensionPoints) {
            if (!extensionPoint.enabled().test(context)) {
                context.markSkipped(extensionPoint.id());
                continue;
            }
            Object product = collectPoint(extensionPoint, plugins, context);
            context.publish(extensionPoint.id(), product);
            products.put(extensionPoint.id(), product);
            registry.publishProduct(extensionPoint.id(), product);
        }
        return Collections.unmodifiableMap(products);
    }

    /** Collection for a single extension point: init accumulator → iterate plugin collectors (in order) → finisher produces results → seal accumulator. */
    private static <P extends NekoJSPlugin, A> Object collectPoint(
            NekoPluginExtensionPoint<P, A, ?> extensionPoint,
            List<NekoJSPlugin> plugins,
            NekoPluginExtensionContext context) {
        A accumulator = extensionPoint.initializer().apply(context);
        for (NekoJSPlugin plugin : plugins) {
            if (extensionPoint.pluginType().isInstance(plugin)) {
                extensionPoint.collector().accept(extensionPoint.pluginType().cast(plugin), accumulator);
            }
        }
        Object product = extensionPoint.finisher().apply(accumulator);
        // ADR-0001: Accumulator sealed after finish (buckets implementing Sealable henceforth reject collection)
        if (accumulator instanceof Sealable sealable) {
            sealable.seal();
        }
        return product;
    }

    private static List<LegacyGlobalReservation> buildLegacyReservations(Map<String, Object> products) {
        List<LegacyGlobalReservation> reservations = new ArrayList<>();
        Map<ScriptType, Map<String, Binding>> bindings = cast(products.get(BindingsPoint.ID));
        for (Map<String, Binding> typeBindings : bindings.values()) {
            for (Binding binding : typeBindings.values()) {
                String diagId = "global:" + binding.name() + "#" + binding.valueType().getName();
                reservations.add(new LegacyGlobalReservation(
                        binding.name(), new ApiSymbolId("global", diagId)));
            }
        }
        for (com.tkisor.nekojs.api.event.EventGroup group : EventsPoint.mergedEventGroups(products).values()) {
            String diagId = "event-group:" + group.name();
            reservations.add(new LegacyGlobalReservation(
                    group.name(), new ApiSymbolId("global", diagId)));
        }
        return List.copyOf(reservations);
    }

    private static List<EnvironmentKey> buildEnvironmentKeys() {
        List<EnvironmentKey> keys = new ArrayList<>();
        for (ScriptType type : ScriptType.values()) {
            keys.add(EnvironmentKey.current(type));
        }
        return List.copyOf(keys);
    }

    static Map<ScriptType, Map<String, Binding>> freezeBindings(ScriptTypedValue<BindingRegistry> registries) {
        Map<ScriptType, Map<String, Binding>> globals = registries.stream()
                .collect(Collectors.toMap(BindingRegistry::scriptType, reg -> Map.copyOf(reg.viewRegistered())));
        var modules = registries.stream().flatMap(reg -> reg.moduleBindings().stream()).toList();
        return new BindingsPoint.Snapshot(globals, modules);
    }

    @SuppressWarnings("unchecked")
    private static <T> T cast(Object value) {
        return (T) value;
    }

    /** Registration registry for a single bootstrap: registration window closes after freeze (synchronized with collection phase). */
    private static final class ExtensionRegistry {
        private final Map<String, NekoPluginExtensionPoint<?, ?, ?>> extensionPoints = new LinkedHashMap<>();
        private final Map<String, NekoPluginExtensionHandle<Object>> handles = new LinkedHashMap<>();
        /** Extension point id to the label of the addon that registered it, so failure output can name
         * the addon. */
        private final Map<String, String> registrants = new LinkedHashMap<>();
        private boolean frozen;

        /**
         * Registration view handed to a single provider: registrations and rule
         * violations through this view carry that addon's label — including a provider
         * that escapes with the view and registers after freeze.
         */
        NekoPluginExtensionRegistry scopedTo(String ownerLabel) {
            return new NekoPluginExtensionRegistry() {
                @Override
                public <P extends NekoJSPlugin, A, R> NekoPluginExtensionHandle<R> register(
                        NekoPluginExtensionPoint<P, A, R> extensionPoint) {
                    if (frozen) {
                        throw new IllegalStateException("Plugin extension registry is frozen after bootstrap"
                                + " collection; late registration of '" + extensionPoint.id()
                                + "' by plugin '" + ownerLabel + "' is rejected"
                                + " (the registration window closes at freeze)");
                    }
                    return ExtensionRegistry.this.register(extensionPoint, ownerLabel);
                }
            };
        }

        <P extends NekoJSPlugin, A, R> NekoPluginExtensionHandle<R> register(
                NekoPluginExtensionPoint<P, A, R> extensionPoint, String ownerLabel) {
            if (frozen) {
                throw new IllegalStateException("Plugin extension registry is frozen after bootstrap"
                        + " collection; registration of '" + extensionPoint.id() + "' is rejected");
            }
            if (extensionPoints.containsKey(extensionPoint.id())) {
                throw new IllegalArgumentException("Plugin extension point '" + extensionPoint.id()
                        + "' is already registered by plugin '" + registrants.get(extensionPoint.id())
                        + "'; duplicate registration by plugin '" + ownerLabel + "' is rejected");
            }
            extensionPoints.put(extensionPoint.id(), extensionPoint);
            registrants.put(extensionPoint.id(), ownerLabel);
            NekoPluginExtensionHandle<R> handle = new NekoPluginExtensionHandle<>(extensionPoint.id());
            @SuppressWarnings("unchecked")
            NekoPluginExtensionHandle<Object> erased = (NekoPluginExtensionHandle<Object>) handle;
            handles.put(extensionPoint.id(), erased);
            return handle;
        }

        List<NekoPluginExtensionPoint<?, ?, ?>> freeze() {
            frozen = true;
            // ADR-0002 ①: Fail early on unregistered dependency IDs (typos exposed before collection starts). Optional dependencies
            // (dependsOnOptionalId) do not trigger fail-early: bootstrap missing the other side (e.g., common tests without version tree
            // plugins) do not build edges and do not error, see NekoPluginExtensionPoint javadoc ③
            for (NekoPluginExtensionPoint<?, ?, ?> point : extensionPoints.values()) {
                for (Object dependency : point.dependsOn()) {
                    String depId = NekoPluginExtensionPoint.dependencyId(dependency);
                    if (!extensionPoints.containsKey(depId)) {
                        throw new IllegalStateException("Plugin extension point '" + point.id()
                                + "' (registered by plugin '" + registrants.get(point.id())
                                + "') dependsOn unregistered point '" + depId + "' (typo?)");
                    }
                }
            }
            // ADR-0002: Kahn topological sort, same-level by registration order (registration order is only a stable tiebreaker, carries no dependency semantics);
            // fail-fast on cycles, error prints full path
            Map<String, Integer> inDegree = new LinkedHashMap<>();
            Map<String, List<String>> dependents = new LinkedHashMap<>();
            for (String id : extensionPoints.keySet()) {
                inDegree.put(id, 0);
                dependents.put(id, new ArrayList<>());
            }
            for (NekoPluginExtensionPoint<?, ?, ?> point : extensionPoints.values()) {
                for (Object dependency : point.dependsOn()) {
                    dependents.get(NekoPluginExtensionPoint.dependencyId(dependency)).add(point.id());
                    inDegree.merge(point.id(), 1, Integer::sum);
                }
                // Optional temporal dependency: build edges only if the other side is registered (equivalent to hard dependency if present, includes cycle detection)
                for (Object dependency : point.optionalDependsOn()) {
                    String depId = NekoPluginExtensionPoint.dependencyId(dependency);
                    if (extensionPoints.containsKey(depId)) {
                        dependents.get(depId).add(point.id());
                        inDegree.merge(point.id(), 1, Integer::sum);
                    }
                }
            }
            List<NekoPluginExtensionPoint<?, ?, ?>> order = new ArrayList<>();
            java.util.Deque<String> ready = new java.util.ArrayDeque<>();
            for (String id : extensionPoints.keySet()) {
                if (inDegree.get(id) == 0) {
                    ready.add(id);
                }
            }
            while (!ready.isEmpty()) {
                String id = ready.poll();
                order.add(extensionPoints.get(id));
                for (String dependent : dependents.get(id)) {
                    if (inDegree.merge(dependent, -1, Integer::sum) == 0) {
                        ready.add(dependent);
                    }
                }
            }
            if (order.size() != extensionPoints.size()) {
                List<String> cycle = new ArrayList<>();
                for (String id : extensionPoints.keySet()) {
                    if (inDegree.get(id) > 0) {
                        cycle.add(id + " (by '" + registrants.get(id) + "')");
                    }
                }
                throw new IllegalStateException("Plugin extension point dependency cycle fail-fast: "
                        + String.join(" -> ", cycle) + " -> " + cycle.get(0));
            }
            return List.copyOf(order);
        }

        void publishProduct(String pointId, Object product) {
            NekoPluginExtensionHandle<Object> handle = handles.get(pointId);
            if (handle == null) {
                throw new IllegalStateException("No handle for extension point '" + pointId + "'");
            }
            handle.publish(product);
        }
    }

    /** Collection context for a single bootstrap: environment flags + registry + skipped registration + artifact table gradually enriched point-by-point. */
    private static final class BootstrapContext implements NekoPluginExtensionContext {
        private final boolean client;
        private final Map<String, Object> products = new LinkedHashMap<>();
        private final java.util.Set<String> registeredIds = new java.util.LinkedHashSet<>();
        private final java.util.Set<String> skippedIds = new java.util.LinkedHashSet<>();
        private final java.util.Set<String> finishedIds = new java.util.LinkedHashSet<>();

        BootstrapContext(boolean client) {
            this.client = client;
        }

        /** Registers all extension point IDs for the round after freeze (to distinguish between "unregistered" and "incomplete"). */
        void registeredIds(java.util.Collection<String> ids) {
            registeredIds.addAll(ids);
        }

        /** Points with false environment predicates registered as skipped. */
        void markSkipped(String pointId) {
            skippedIds.add(pointId);
        }

        @Override
        public boolean client() {
            return client;
        }

        @Override
        @SuppressWarnings("unchecked")
        public <R> R result(NekoPluginExtensionPoint<?, ?, R> point) {
            return (R) lookup(point.id());
        }

        @Override
        public <R> R result(String pointId, Class<R> type) {
            Object product = lookup(pointId);
            if (product == null) {
                return null;
            }
            return checkType(pointId, product, type);
        }

        @Override
        @SuppressWarnings("unchecked")
        public <R> R resultOrThrow(NekoPluginExtensionPoint<?, ?, R> point) {
            return (R) require(point.id());
        }

        @Override
        public <R> R resultOrThrow(String pointId, Class<R> type) {
            return checkType(pointId, require(pointId), type);
        }

        /** Optional dependency: returns null when the point is unregistered or skipped, and throws an
         * ordering-violation error when it is registered but has not finished. */
        private Object lookup(String pointId) {
            if (finishedIds.contains(pointId)) {
                return products.get(pointId);
            }
            if (!registeredIds.contains(pointId) || skippedIds.contains(pointId)) {
                return null;
            }
            throw new IllegalStateException("Data-dependency ordering violation: extension point '"
                    + pointId + "' is registered but has not finished yet. Declare dependsOn('"
                    + pointId + "') (or dependsOnId) so the bootstrap topological order runs it first.");
        }

        /** Required dependency: any kind of absence throws an error that states the reason. */
        private Object require(String pointId) {
            if (finishedIds.contains(pointId)) {
                return products.get(pointId);
            }
            if (!registeredIds.contains(pointId)) {
                throw new IllegalStateException("Required extension point '" + pointId
                        + "' is not registered in this bootstrap");
            }
            if (skippedIds.contains(pointId)) {
                throw new IllegalStateException("Required extension point '" + pointId
                        + "' is skipped in this environment (e.g. client-only on a dedicated server)");
            }
            throw new IllegalStateException("Required extension point '" + pointId
                    + "' has not finished yet; declare dependsOn('" + pointId + "')");
        }

        private <R> R checkType(String pointId, Object product, Class<R> type) {
            if (!type.isInstance(product)) {
                throw new IllegalStateException("Plugin extension point '" + pointId
                        + "' product type mismatch: expected " + type.getName()
                        + " but was " + product.getClass().getName());
            }
            return type.cast(product);
        }

        void publish(String pointId, Object product) {
            products.put(pointId, product);
            finishedIds.add(pointId);
        }
    }
}
