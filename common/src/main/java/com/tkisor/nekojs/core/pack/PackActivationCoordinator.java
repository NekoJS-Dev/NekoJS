package com.tkisor.nekojs.core.pack;

import com.tkisor.nekojs.api.ScriptType;
import com.tkisor.nekojs.api.event.EventBusJS;
import com.tkisor.nekojs.api.plugin.IPluginRuntime;
import com.tkisor.nekojs.core.ScriptEventBridge;
import com.tkisor.nekojs.core.lifecycle.CandidateDomainCollector;
import com.tkisor.nekojs.core.lifecycle.ScriptLifecycleGate;
import com.tkisor.nekojs.core.state.GenerationGlobals;
import com.tkisor.nekojs.script.ScriptContainer;
import com.tkisor.nekojs.script.ScriptManager;
import graal.graalvm.polyglot.Context;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BiFunction;
import java.util.function.Function;

/** Root-owned owner-thread batch; sources supply content and never activate contexts themselves. */
public final class PackActivationCoordinator implements AutoCloseable {
    private final BiFunction<ScriptType, ScriptPack, ScriptManager> create;
    private final Function<ScriptType, ScriptManager> legacy;
    private final Function<ScriptType, List<ScriptContainer>> discover;
    private final ScriptEventBridge events;
    private final IPluginRuntime plugins;
    private final List<CandidateDomainCollector> collectors;
    private final EnumMap<ScriptType, TypeState> types = new EnumMap<>(ScriptType.class);
    private final AtomicLong sourceEpoch = new AtomicLong();
    private final Object commitLock = new Object();
    private volatile boolean closing;
    private boolean startupEvaluated;
    private long nextActivation;
    private volatile Thread operationOwner;
    private final java.util.concurrent.CopyOnWriteArrayList<ScriptManager> inFlight = new java.util.concurrent.CopyOnWriteArrayList<>();
    private final Set<ScriptPackScope> pendingRevocations = java.util.EnumSet.noneOf(ScriptPackScope.class);

    public PackActivationCoordinator(BiFunction<ScriptType, ScriptPack, ScriptManager> create,
            Function<ScriptType, ScriptManager> legacy, Function<ScriptType, List<ScriptContainer>> discover,
            ScriptEventBridge events, IPluginRuntime plugins, List<CandidateDomainCollector> collectors) {
        this.create = create;
        this.legacy = legacy;
        this.discover = discover;
        this.events = events;
        this.plugins = plugins;
        this.collectors = collectors;
    }

    public record Result(ScriptType type, boolean success, Map<String, String> affected,
                         Map<String, String> waiting, String failure,
                         com.tkisor.nekojs.core.lifecycle.ReloadFailureReport report, List<String> cleanupFailures) {
        public Result(ScriptType type, boolean success, Map<String, String> affected, Map<String, String> waiting, String failure) {
            this(type, success, affected, waiting, failure, null, List.of());
        }
        public Result {
            affected = java.util.Collections.unmodifiableMap(new LinkedHashMap<>(affected));
            waiting = java.util.Collections.unmodifiableMap(new LinkedHashMap<>(waiting));
            cleanupFailures = List.copyOf(cleanupFailures);
        }
    }

    public record View(String selector, String sourceInstance, long activation, String version,
                       String status, String reason) {}

    private static final class TypeState {
        final ScriptLifecycleGate gate = new ScriptLifecycleGate();
        final Map<String, Unit> active = new LinkedHashMap<>();
        Map<String, List<String>> dependencies = Map.of();
        Map<String, String> waiting = Map.of();
        boolean initialized;
    }

    private record Unit(ScriptPack source, ScriptManager manager, PackCapabilityBridge capabilities, long activation, String instance) {}

    public synchronized boolean handles(ScriptType type) {
        ScriptPackRegistry.get().refreshLocalPacks();
        return types.containsKey(type) || ScriptPackRegistry.get().allPacks().stream()
                .anyMatch(pack -> pack.manifest().descriptor() != null && pack.manifest().descriptor().entries().containsKey(type));
    }

    /** A legacy STARTUP boot also consumes the one loader initialization boundary. */
    public synchronized void sealStartup() { startupEvaluated = true; }

    /** Null selector means a complete type replacement, including the legacy partition. */
    public synchronized Result reload(ScriptType type, String selector) {
        if (closing) return new Result(type, false, Map.of(), Map.of(), "[NEKO-1017] Package runtime is closing");
        TypeState state = types.computeIfAbsent(type, ignored -> new TypeState());
        var decision = state.gate.tryEnter(ScriptLifecycleGate.Operation.RELOAD);
        if (decision != ScriptLifecycleGate.Decision.EXECUTED) {
            return new Result(type, false, Map.of(), state.waiting, "[NEKO-1017] Package operation rejected: " + decision);
        }
        try {
            operationOwner = Thread.currentThread();
            return replace(type, selector, state);
        } finally {
            inFlight.clear(); operationOwner = null;
            state.gate.exit(ScriptLifecycleGate.Operation.RELOAD);
            for (ScriptPackScope scope : List.copyOf(pendingRevocations)) stopScope(scope);
            pendingRevocations.clear();
        }
    }

    private Result replace(ScriptType type, String selector, TypeState state) {
        var registry = ScriptPackRegistry.get();
        registry.refreshLocalPacks();
        long epoch = sourceEpoch.get();
        List<ScriptPack> sources = registry.allPacks();
        PackDependencyPlan plan = registry.dependencyPlan();
        Map<String, ScriptPack> managed = new LinkedHashMap<>();
        sources.stream().filter(pack -> pack.manifest().descriptor() != null && pack.manifest().descriptor().entries().containsKey(type))
                .forEach(pack -> managed.putIfAbsent(pack.selector(), pack));
        for (Unit previous : state.active.values()) {
            if (sources.stream().noneMatch(source -> source.root().equals(previous.source().root())) && java.nio.file.Files.isRegularFile(
                    previous.source().root().resolve(ScriptPackManifest.FILE_NAME))) {
                return new Result(type, false, Map.of(previous.source().selector(), "invalid discovered replacement"),
                        state.waiting, "existing package content or descriptor is invalid; healthy activation retained: " + previous.source().selector());
            }
        }
        Map<String, String> affected = new LinkedHashMap<>();
        if (selector == null) {
            managed.keySet().forEach(key -> affected.put(key, "full type reload"));
            state.active.keySet().forEach(key -> affected.putIfAbsent(key, "previous activation"));
        } else {
            Set<String> selectable = new LinkedHashSet<>(managed.keySet());
            selectable.addAll(state.active.keySet());
            List<String> matches = selectable.stream().filter(key -> key.equalsIgnoreCase(selector)
                    || !selector.contains(":") && key.endsWith(":" + selector)).toList();
            if (matches.size() != 1) return new Result(type, false, Map.of(), state.waiting,
                    "selector is missing or ambiguous: " + selector);
            affected.put(matches.getFirst(), "requested package");
        }
        boolean expanded;
        do {
            expanded = false;
            Map<String, List<String>> edges = new LinkedHashMap<>(state.dependencies);
            plan.dependencies().forEach((key, values) -> {
                var joined = new LinkedHashSet<>(edges.getOrDefault(key, List.of()));
                joined.addAll(values); edges.put(key, List.copyOf(joined));
            });
            for (var edge : edges.entrySet()) {
                if (!managed.containsKey(edge.getKey()) && !state.active.containsKey(edge.getKey())) continue;
                if (!affected.containsKey(edge.getKey()) && edge.getValue().stream().anyMatch(affected::containsKey)) {
                    affected.put(edge.getKey(), "depends on a replaced provider"); expanded = true;
                }
            }
        } while (expanded);
        List<CandidateDomainCollector> domains = collectors.stream().filter(collector -> collector.scriptType() == type).toList();
        // Existing domain adapters own whole-type tables. Their complete contribution set must
        // participate; pretending those tables support per-package deltas would delete siblings.
        boolean includeLegacy = selector == null || !domains.isEmpty();
        if (!domains.isEmpty()) {
            managed.keySet().forEach(key -> affected.putIfAbsent(key, "expanded for shared domain contribution tables"));
            state.active.keySet().forEach(key -> affected.putIfAbsent(key, "expanded for shared domain contribution tables"));
        }
        if (type == ScriptType.STARTUP && (state.initialized || startupEvaluated)) {
            return new Result(type, false, affected, state.waiting, "STARTUP package changes require a loader restart");
        }
        List<ScriptContainer> scripts = discover.apply(type);
        Map<String, String> waiting = new LinkedHashMap<>();
        plan.blocked().forEach((key, reason) -> { if (managed.containsKey(key)) waiting.put(key, reason); });
        Map<String, Unit> next = new LinkedHashMap<>(state.active);
        affected.keySet().forEach(next::remove);
        List<ScriptManager.PreparedPack> candidates = new ArrayList<>();
        List<PackCapabilityBridge> preparedBindings = new ArrayList<>();
        List<ScriptManager> newManagers = new ArrayList<>();
        ScriptEventBridge.ListenerBatch listenerBatch = null;
        com.tkisor.nekojs.core.lifecycle.ReloadPhase phase = com.tkisor.nekojs.core.lifecycle.ReloadPhase.EXECUTION;
        List<String> cleanupFailures = new ArrayList<>();
        try {
            plugins.fireBeforeScriptsLoaded(type);
            try {
                if (includeLegacy) {
                    inFlight.addIfAbsent(legacy.apply(type));
                    candidates.add(legacy.apply(type).preparePackCandidate(scripts.stream()
                            .filter(script -> script.pack == null || script.pack.manifest().descriptor() == null).toList(), context -> {}));
                }
                for (ScriptPack source : plan.ordered()) {
                    if (!managed.containsKey(source.selector()) || !affected.containsKey(source.selector())) continue;
                    String missingEntryProvider = plan.dependencies().getOrDefault(source.selector(), List.of()).stream()
                            .filter(managed::containsKey).filter(provider -> !next.containsKey(provider) || !next.get(provider).capabilities().live())
                            .findFirst().orElse(null);
                    if (missingEntryProvider != null) {
                        waiting.put(source.selector(), "waiting for provider activation " + missingEntryProvider);
                        continue;
                    }
                    String missing = missingCapability(source, sources, next);
                    if (missing != null) { waiting.put(source.selector(), missing); continue; }
                    Unit previous = state.active.get(source.selector());
                    String instance = registry.instanceKey(source);
                    boolean reuse = previous != null && previous.instance().equals(instance);
                    ScriptManager manager = reuse ? previous.manager() : create.apply(type, source);
                    inFlight.addIfAbsent(manager);
                    if (!reuse) newManagers.add(manager);
                    long activation = ++nextActivation;
                    PackCapabilityBridge[] binding = new PackCapabilityBridge[1];
                    candidates.add(manager.preparePackCandidate(scripts.stream()
                            .filter(script -> script.pack != null && script.pack.selector().equals(source.selector())).toList(), context -> {
                        binding[0] = new PackCapabilityBridge(source, activation, context, provider -> {
                            ScriptPack resolved = PackDependencyPlan.providerFor(source, provider, sources);
                            Unit unit = resolved == null ? null : next.get(resolved.selector());
                            return unit == null ? null : unit.capabilities();
                        });
                        preparedBindings.add(binding[0]);
                        context.getBindings("js").putMember("Pack", binding[0]);
                    }));
                    for (String name : source.manifest().descriptor().provides()) {
                        if (!binding[0].available(name)) throw new IllegalStateException("[NEKO-1017] Declared capability was not provided: " + source.selector() + "/" + name);
                    }
                    binding[0].seal();
                    Unit candidate = new Unit(source, manager, binding[0], activation, instance);
                    candidates.getLast().activationGuard(() -> !closing && plan.dependencies().getOrDefault(source.selector(), List.of()).stream()
                            .allMatch(provider -> !managed.containsKey(provider)
                                    || next.containsKey(provider) && next.get(provider).capabilities().live()));
                    next.put(source.selector(), candidate);
                }
            } finally {
                plugins.fireAfterScriptsLoaded(type);
            }
            if (!candidates.isEmpty()) {
                phase = com.tkisor.nekojs.core.lifecycle.ReloadPhase.DOMAIN_PLAN;
                CandidateDomainCollector.Handle merged = mergedHandle(type, candidates);
                for (var domain : domains) domain.collect(merged);
            }
            List<GenerationGlobals> globals = candidates.stream().map(ScriptManager.PreparedPack::globals).toList();
            phase = com.tkisor.nekojs.core.lifecycle.ReloadPhase.STATE_PLAN;
            GenerationGlobals.preflightBatch(globals);
            assertEpoch(epoch, candidates);
            Set<Context> old = new LinkedHashSet<>();
            for (var candidate : candidates) if (candidate.previousContext() != null) old.add(candidate.previousContext());
            for (String key : affected.keySet()) {
                Unit previous = state.active.get(key);
                if (previous != null && (!next.containsKey(key) || previous.manager() != next.get(key).manager())) {
                    // A removal contributes no pending listeners, but its old route still joins the switch.
                    old.add(previous.capabilities().contextForOwner());
                }
            }
            List<EventBusJS.PendingListener> pending = candidates.stream().flatMap(candidate -> candidate.listeners().stream()).toList();
            phase = com.tkisor.nekojs.core.lifecycle.ReloadPhase.EVENT_PLAN;
            listenerBatch = events.preparePackListeners(type, pending, old);
            for (var candidate : candidates) candidate.publishMetadata();
            phase = com.tkisor.nekojs.core.lifecycle.ReloadPhase.COMMIT;
            synchronized (commitLock) {
                assertEpoch(epoch, candidates);
                GenerationGlobals.publishBatch(globals);
                for (var candidate : candidates) candidate.commitRoute();
                listenerBatch.finish();
            }
            // Invalidate borrowers before releasing providers; all new routes already point at
            // candidates. Old handles cannot observe a new provider through a reused selector.
            for (String key : affected.keySet()) {
                Unit previous = state.active.get(key);
                if (previous != null) previous.capabilities().invalidate();
            }
            List<Unit> previous = new ArrayList<>(state.active.values());
            state.active.clear(); state.active.putAll(next);
            state.dependencies = plan.dependencies(); state.waiting = Map.copyOf(waiting);
            state.initialized = true;
            for (int index = candidates.size() - 1; index >= 0; index--) closeResource(candidates.get(index), cleanupFailures);
            for (int index = previous.size() - 1; index >= 0; index--) {
                Unit unit = previous.get(index);
                if (affected.containsKey(unit.source().selector()) && (!next.containsKey(unit.source().selector())
                        || unit.manager() != next.get(unit.source().selector()).manager())) closeResource(unit.manager(), cleanupFailures);
            }
            return new Result(type, true, affected, waiting, null, null, cleanupFailures);
        } catch (Throwable failure) {
            if (listenerBatch != null) {
                try { listenerBatch.rollback(); } catch (Throwable cleanup) { failure.addSuppressed(cleanup); }
            }
            preparedBindings.forEach(PackCapabilityBridge::invalidate);
            for (int index = candidates.size() - 1; index >= 0; index--) closeResource(candidates.get(index), cleanupFailures);
            for (int index = newManagers.size() - 1; index >= 0; index--) closeResource(newManagers.get(index), cleanupFailures);
            String domain = failure instanceof com.tkisor.nekojs.core.state.GlobalStateException stateFailure
                    ? stateFailure.domain() + ": " : "";
            var report = failure instanceof com.tkisor.nekojs.core.lifecycle.NekoReloadException reloadFailure ? reloadFailure.report()
                    : new com.tkisor.nekojs.core.lifecycle.ReloadFailureReport(type, nextActivation, phase, null,
                            "PackActivation[" + type.name + "]", domain.isEmpty() ? "package-activation" : domain.substring(0, domain.length() - 2), failure);
            return new Result(type, false, affected, state.waiting, domain + failure, report, cleanupFailures);
        }
    }

    private static void closeResource(AutoCloseable resource, List<String> failures) {
        try { resource.close(); }
        catch (Throwable failure) {
            failures.add(failure.toString());
            com.tkisor.nekojs.NekoJS.LOGGER.error("[NEKO-1017] Package resource release failed; remaining resources continue closing", failure);
        }
    }

    private static String missingCapability(ScriptPack source, List<ScriptPack> sources, Map<String, Unit> active) {
        for (var requirement : source.manifest().descriptor().requires().entrySet()) {
            ScriptPack provider = PackDependencyPlan.providerFor(source, requirement.getKey(), sources);
            Unit unit = provider == null ? null : active.get(provider.selector());
            for (String name : requirement.getValue()) {
                if (unit == null || !unit.capabilities().available(name)) return "waiting for capability " + requirement.getKey() + "/" + name;
            }
        }
        return null;
    }

    private void assertEpoch(long epoch, List<ScriptManager.PreparedPack> candidates) {
        if (closing || epoch != sourceEpoch.get()) throw new IllegalStateException("[NEKO-1017] Package source session changed before commit");
        candidates.forEach(ScriptManager.PreparedPack::assertCanCommit);
    }

    private static CandidateDomainCollector.Handle mergedHandle(ScriptType type, List<ScriptManager.PreparedPack> candidates) {
        return new CandidateDomainCollector.Handle() {
            public Context candidateContext() { return candidates.getFirst().context(); }
            public List<Context> candidateContexts() { return candidates.stream().map(ScriptManager.PreparedPack::context).toList(); }
            public ScriptType scriptType() { return type; }
            public List<EventBusJS.PendingListener> listenersOf(EventBusJS<?, ?> bus) {
                List<EventBusJS.PendingListener> listeners = new ArrayList<>();
                candidates.forEach(candidate -> listeners.addAll(candidate.collectionHandle().listenersOf(bus)));
                listeners.sort((left, right) -> -Byte.compare(left.priority(), right.priority()));
                return List.copyOf(listeners);
            }
            public void execute(EventBusJS.PendingListener listener, Object event) { listener.executeForCollection(event); }
            public void registerPlan(com.tkisor.nekojs.core.state.CandidateStatePlan plan) { candidates.getFirst().globals().addPlan(plan); }
        };
    }

    public synchronized List<View> views(ScriptType type) {
        TypeState state = types.get(type);
        if (state == null) return List.of();
        List<View> result = new ArrayList<>();
        state.active.values().forEach(unit -> result.add(new View(unit.source().selector(), unit.instance(),
                unit.activation(), unit.source().version(), unit.manager().isActiveFailed() ? "FAILED" : "ACTIVE", null)));
        state.waiting.forEach((key, reason) -> { if (!state.active.containsKey(key)) result.add(new View(key, null, 0, null, "WAITING", reason)); });
        return List.copyOf(result);
    }

    public synchronized List<PackScriptView> entryViews(ScriptType type) {
        TypeState state = types.get(type);
        return state == null ? List.of() : state.active.values().stream().flatMap(unit -> unit.manager().packRuntimeView().entries().stream()).toList();
    }

    public synchronized List<com.tkisor.nekojs.core.error.ScriptError> errors() {
        return types.values().stream().flatMap(state -> state.active.values().stream())
                .flatMap(unit -> unit.manager().packErrors().stream()).toList();
    }

    public void sourcesRevoked(ScriptPackScope scope) {
        synchronized (commitLock) { sourceEpoch.incrementAndGet(); }
        if (!isOwnerOperation()) inFlight.forEach(ScriptManager::cancelPackCandidate);
        synchronized (this) {
            if (isOwnerOperation()) {
                pendingRevocations.add(scope);
                for (var state : types.values()) {
                    Set<String> stopped = stoppedByScope(state, scope);
                    state.active.forEach((key, unit) -> { if (stopped.contains(key)) unit.capabilities().invalidate(); });
                }
                return;
            }
            stopScope(scope);
        }
    }

    private void stopScope(ScriptPackScope scope) {
        List<String> failures = new ArrayList<>();
        for (var entry : types.entrySet()) {
            var state = entry.getValue();
            Set<String> stopped = stoppedByScope(state, scope);
            List<Unit> units = new ArrayList<>(state.active.values());
            units.stream().filter(unit -> stopped.contains(unit.source().selector())).forEach(unit -> unit.capabilities().invalidate());
            for (int index = units.size() - 1; index >= 0; index--) {
                Unit unit = units.get(index);
                if (stopped.contains(unit.source().selector())) {
                    state.active.remove(unit.source().selector()); closeResource(unit.manager(), failures);
                }
            }
            if (!stopped.isEmpty()) reconcileDomains(entry.getKey(), state, failures);
        }
    }

    private static Set<String> stoppedByScope(TypeState state, ScriptPackScope scope) {
        Set<String> stopped = new LinkedHashSet<>();
        state.active.forEach((key, unit) -> { if (unit.source().scope() == scope) stopped.add(key); });
        boolean changed;
        do {
            changed = false;
            for (var edge : state.dependencies.entrySet()) if (!stopped.contains(edge.getKey())
                    && edge.getValue().stream().anyMatch(stopped::contains)) changed |= stopped.add(edge.getKey());
        } while (changed);
        return stopped;
    }

    public synchronized void flushTimers(ScriptType type) {
        TypeState state = types.get(type);
        if (state == null || closing) return;
        Set<String> stopped = new LinkedHashSet<>();
        state.active.forEach((key, unit) -> { if (!unit.capabilities().live()) stopped.add(key); });
        if (!stopped.isEmpty()) {
            List<String> failures = new ArrayList<>();
            Map<String, String> waiting = new LinkedHashMap<>(state.waiting);
            List<Unit> units = new ArrayList<>(state.active.values());
            units.stream().filter(unit -> stopped.contains(unit.source().selector())).forEach(unit -> unit.capabilities().invalidate());
            for (int index = units.size() - 1; index >= 0; index--) {
                Unit unit = units.get(index);
                if (!stopped.contains(unit.source().selector())) continue;
                String reason = unit.manager().isActiveFailed() ? "provider activation failed; explicit reload required"
                        : "waiting for a healthy provider activation";
                waiting.put(unit.source().selector(), reason);
                state.active.remove(unit.source().selector()); closeResource(unit.manager(), failures);
            }
            state.waiting = Map.copyOf(waiting);
            reconcileDomains(type, state, failures);
        }
        for (Unit unit : List.copyOf(state.active.values())) unit.manager().flushReadyNodeTimers();
    }

    private void reconcileDomains(ScriptType type, TypeState state, List<String> failures) {
        Thread previousOwner = operationOwner;
        operationOwner = Thread.currentThread();
        try {
            List<ScriptManager> survivors = new ArrayList<>();
            survivors.add(legacy.apply(type));
            state.active.values().forEach(unit -> survivors.add(unit.manager()));
            for (var collector : collectors) {
                if (collector.scriptType() != type) continue;
                try { publishReconciledDomain(type, collector, survivors); }
                catch (Throwable failure) {
                    if (closing) return;
                    failures.add(failure.toString());
                    com.tkisor.nekojs.NekoJS.LOGGER.error(
                            "[NEKO-1017] Package contribution reconciliation failed; withdrawing the domain until explicit reload: domain={}",
                            collector.domain(), failure);
                    try { publishReconciledDomain(type, collector, List.of()); }
                    catch (Throwable cleanup) {
                        failures.add(cleanup.toString());
                        com.tkisor.nekojs.NekoJS.LOGGER.error(
                                "[NEKO-1017] Package domain withdrawal failed; domain resources require recovery: domain={}", collector.domain(), cleanup);
                    }
                }
            }
        } finally {
            operationOwner = previousOwner;
            if (previousOwner == null && !closing && !pendingRevocations.isEmpty()) {
                var pending = List.copyOf(pendingRevocations);
                pendingRevocations.clear();
                pending.forEach(this::stopScope);
            }
        }
    }

    private void publishReconciledDomain(ScriptType type, CandidateDomainCollector collector, List<ScriptManager> managers) {
        long epoch = sourceEpoch.get();
        List<com.tkisor.nekojs.core.state.CandidateStatePlan> plans = new ArrayList<>();
        List<Context> contexts = managers.stream().map(ScriptManager::activeContextForPackOwner).filter(java.util.Objects::nonNull).toList();
        CandidateDomainCollector.Handle handle = new CandidateDomainCollector.Handle() {
            public Context candidateContext() { return contexts.isEmpty() ? null : contexts.getFirst(); }
            public List<Context> candidateContexts() { return contexts; }
            public ScriptType scriptType() { return type; }
            public List<EventBusJS.PendingListener> listenersOf(EventBusJS<?, ?> bus) {
                if (bus.canDispatch()) throw new UnsupportedOperationException("[NEKO-1017] Domain reconciliation requires a non-dispatch bus");
                return managers.stream().flatMap(manager -> manager.activeListenersForPackOwner().stream())
                        .filter(listener -> listener.owner() == bus)
                        .sorted((left, right) -> -Byte.compare(left.priority(), right.priority())).toList();
            }
            public void execute(EventBusJS.PendingListener listener, Object event) {
                Context context = listener.listenerValue().getContext();
                synchronized (context) {
                    if (ScriptManager.isContextDead(context)) throw new IllegalStateException("[NEKO-1017] Domain participant activation is unavailable");
                    listener.executeForCollection(event);
                }
            }
            public void registerPlan(com.tkisor.nekojs.core.state.CandidateStatePlan plan) { plans.add(plan); }
        };
        try {
            collector.reconcileAfterRemoval(handle);
            for (var plan : plans) plan.preflight();
            synchronized (commitLock) {
                if (closing || epoch != sourceEpoch.get()) throw new IllegalStateException("[NEKO-1017] Domain reconciliation was preempted by source teardown");
                for (var plan : plans) plan.publish();
            }
        } finally {
            List<String> failures = new ArrayList<>();
            for (int index = plans.size() - 1; index >= 0; index--) closeResource(plans.get(index)::discard, failures);
        }
    }

    @Override
    public void close() {
        requestClose();
        if (isOwnerOperation()) return;
        synchronized (this) {
            List<String> failures = new ArrayList<>();
            for (var state : types.values()) {
                List<Unit> units = new ArrayList<>(state.active.values());
                units.forEach(unit -> unit.capabilities().invalidate());
                for (int index = units.size() - 1; index >= 0; index--) closeResource(units.get(index).manager(), failures);
                state.active.clear();
            }
            types.clear();
            if (!failures.isEmpty()) throw new IllegalStateException("[NEKO-1017] Package runtime closed with resource release failures: " + failures);
        }
    }

    public boolean isOwnerOperation() { return operationOwner == Thread.currentThread(); }

    public void requestClose() {
        synchronized (commitLock) { closing = true; sourceEpoch.incrementAndGet(); }
        boolean interrupt = !isOwnerOperation();
        inFlight.forEach(manager -> manager.requestPackClose(interrupt));
    }
}
