package com.tkisor.nekojs.script;

import com.tkisor.nekojs.script.ScriptTypeEnv;
import com.tkisor.nekojs.api.ScriptType;
import com.tkisor.nekojs.core.lifecycle.CandidateDomainCollector;
import com.tkisor.nekojs.NekoJS;
import com.tkisor.nekojs.core.JavaClassLoadTelemetrySink;
import com.tkisor.nekojs.api.event.ScriptEventRegistrar;
import com.tkisor.nekojs.api.event.EventBusJS;
import com.tkisor.nekojs.api.event.ScriptEvents;
import com.tkisor.nekojs.api.event.ScriptBindingSchema;
import com.tkisor.nekojs.core.JavaClassLoadTelemetry;
import com.tkisor.nekojs.core.ScriptEventBridge;
import com.tkisor.nekojs.core.ScriptFilePolicy;
import com.tkisor.nekojs.core.ScriptLocator;
import com.tkisor.nekojs.core.config.SandboxConfig;
import com.tkisor.nekojs.core.error.ErrorTracker;
import com.tkisor.nekojs.core.error.DefaultErrorTracker;
import com.tkisor.nekojs.core.error.ScriptError;
import com.tkisor.nekojs.core.fs.ClassFilter;
import com.tkisor.nekojs.core.fs.NekoJSPaths;
import com.tkisor.nekojs.core.lifecycle.NekoReloadException;
import com.tkisor.nekojs.core.lifecycle.ReloadFailureReport;
import com.tkisor.nekojs.core.lifecycle.ReloadPhase;
import com.tkisor.nekojs.core.lifecycle.ReloadProgressTracker;
import com.tkisor.nekojs.core.lifecycle.ScriptLifecycleGate;
import com.tkisor.nekojs.core.log.LoggerStream;
import com.tkisor.nekojs.core.module.NekoModulePipelineCache;
import com.tkisor.nekojs.core.node.NekoNodeRuntime;
import com.tkisor.nekojs.api.plugin.IPluginRuntime;
import com.tkisor.nekojs.script.ScriptContextRegistry;
import com.tkisor.nekojs.script.prop.ScriptPropertyRegistry;
import graal.graalvm.polyglot.Context;
import graal.graalvm.polyglot.PolyglotException;
import graal.graalvm.polyglot.Value;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;

import com.tkisor.nekojs.api.annotation.CalledByDynamicCode;

/**
 * Core lifecycle scheduler for the NekoJS script engine.
 * <p>
 * Each instance manages the complete script lifecycle for a specific {@link ScriptType} (STARTUP, SERVER, CLIENT, or TEST).
 * Collaborators such as {@link ScriptEventBridge}, {@link ErrorTracker}, and {@link NekoJSPaths} are injected via the constructor.
 */
public final class ScriptManager implements AutoCloseable {

    // ---- Minimal static: Context → ScriptManager reverse lookup ----
    // Use ConcurrentHashMap instead of synchronizedMap(WeakHashMap): isContextDead / reportContextKilled.
    // In the hot path of every event and timer callback, synchronizedMap serializes all callbacks on the map lock.
    // Keys are changed to strong references; the invariant is that Context destruction must go through closeRuntimeResources (which guarantees removal).
    // All current destruction paths (kill-rebuild, transaction reload success/failure, resetEnvironment, close) satisfy this.
    // If a new destruction path bypassing closeRuntimeResources is added, it will leak both Context and ScriptManager.
    private static final Map<Context, ScriptManager> CONTEXT_TO_MANAGER = new ConcurrentHashMap<>();

    /**
     * Reverse lookup of the ScriptManager instance from a GraalVM Context.
     * Used in JS callback scenarios like EventBusJS / NekoNodeTimers, which only have a Context reference.
     */
    public static ScriptManager from(Context context) {
        ScriptManager sm = CONTEXT_TO_MANAGER.get(context);
        if (sm == null) {
            throw new IllegalStateException("No ScriptManager registered for Context: " + context);
        }
        return sm;
    }

    /**
     * Attributes a cancelled or resource-exhausted Context to its active or candidate generation.
     * Event and timer failures share this path; unregistered Contexts are ignored.
     */
    public static void reportContextKilled(Context context, Throwable t) {
        if (context == null || !ScriptExecutor.isContextTerminated(t)) return;
        ScriptManager manager = CONTEXT_TO_MANAGER.get(context);
        if (manager != null) {
            manager.markContextKilled(context);
        }
    }

    /**
     * Short-circuit check for JS callback dispatch (event listeners and timers): has the Context captured
     * by the closure died?
     *
     * <p>The relocated Graal polyglot API used here has no {@code Context.isOpen()}, so this mirrors it
     * with ScriptManager state:
     * <ul>
     *   <li>active Context: dead once {@code contextKilled} is set, until an explicit recovery;</li>
     *   <li>candidate Context (being built): dead once {@code candidateKilled} is set. Candidate timers
     *       can run on the candidate execution path (the {@code waitForEvaluation} flush), but candidate
     *       listeners are not attached to the bus before commit, so a candidate listener can never
     *       receive production events early;</li>
     *   <li>registered but neither active nor candidate: a leftover closure of an old generation, so
     *       dead. This stops the old generation from receiving new callbacks right after a commit, even
     *       when a concurrent dispatch slips in between the bus sweep and the activation, so old and new
     *       never both run.</li>
     * </ul>
     * Unregistered Contexts (tests, for example) return false and take the existing try/catch path.
     */
    public static boolean isContextDead(Context context) {
        if (context == null) return false;
        ScriptManager manager = CONTEXT_TO_MANAGER.get(context);
        if (manager == null) return false;
        // Graal's Value.getContext() might return a wrapper object with a different reference but
        // same equals/hashCode as Context.Builder.build(); must compare using equals, otherwise normal callbacks will be misidentified as dead.
        Context active = manager.runtime.context();
        if (active != null && active.equals(context)) {
            return manager.contextKilled || !manager.packActivationAllowed.getAsBoolean();
        }
        Context candidate = manager.candidateContext;
        if (candidate != null && candidate.equals(context)) {
            return manager.candidateKilled;
        }
        return true;
    }

    /** Prepared listeners can exist on a bus before the surrounding state batch publishes. */
    public static boolean isCandidateContext(Context context) {
        ScriptManager manager = CONTEXT_TO_MANAGER.get(context);
        return manager != null && manager.candidateContext != null && manager.candidateContext.equals(context);
    }

    /**
     * The committed generation number of the manager that owns this Context, or {@code -1} when no
     * manager knows the Context.
     *
     * <p>Only the currently active generation of a live manager answers with a non-negative number: a
     * candidate being built, a Context of a superseded generation, a killed active Context, a closed
     * manager and an unregistered Context all report {@code -1}. Read-only domain queries (see
     * {@code com.tkisor.nekojs.core.villager.VillagerTradeDomainState}) use it to turn "this token
     * belongs to an old generation" into an explicit stale result instead of silently serving the new
     * generation's data to the old one.
     */
    public static long activeGenerationOf(Context context) {
        if (context == null) return -1L;
        ScriptManager manager = CONTEXT_TO_MANAGER.get(context);
        if (manager == null || manager.lifecycleGate.isClosed()) return -1L;
        Context active = manager.runtime.context();
        if ((active == null || !active.equals(context)) || manager.contextKilled) return -1L;
        return manager.generation;
    }

    // ---- Instance fields ----

    private final ScriptEventBridge scriptEventBridge;
    private final IPluginRuntime pluginRuntime;
    private final ScriptPropertyRegistry scriptProperties;
    private final ErrorTracker errorTracker;
    private final NekoJSPaths paths;
    private final SandboxConfig sandboxConfig;
    private final ScriptExecutor scriptExecutor;
    private final ScriptEnvironmentFactory environmentFactory;
    /** Candidate domain collector owned by the root (consumed in the TICKET 39 DOMAIN_PLAN phase; shared by reference with the root). */
    private final List<com.tkisor.nekojs.core.lifecycle.CandidateDomainCollector> domainCollectors;
    /**
     * Prepared module cache, injected explicitly: production passes the root-owned instance through
     * {@code NekoRuntimeRoot}, which owns the module session lifetime. The other constructor creates its
     * own isolated instance.
     */
    /** Root-owned cache/session owner; mutable module entries live only in generation sessions. */
    private final NekoModulePipelineCache preparationCache;

    /**
     * The script type this manager handles.
     */
    public final ScriptType scriptType;

    /**
     * An inseparable snapshot of the context and its companion resources (node runtime, out/err streams).
     *
     * <p>The whole snapshot must be published through a single volatile reference. The tick thread reads
     * these fields without a lock, so separate fields would let it see a new context paired with an old
     * runtime (after a reload a timer fires into the closed runtime), or a field cleared between the null
     * check and its use, which throws an NPE.
     */
    private record RuntimeEnvironment(Context context, NekoNodeRuntime nodeRuntime,
                                      LoggerStream outStream, LoggerStream errStream,
                                      com.tkisor.nekojs.core.state.GenerationGlobals globals,
                                      NekoModulePipelineCache moduleSession) {
        static final RuntimeEnvironment EMPTY = new RuntimeEnvironment(null, null, null, null, null, null);

        boolean isEmpty() {
            return context == null;
        }
    }

    private volatile RuntimeEnvironment runtime = RuntimeEnvironment.EMPTY;
    private List<ScriptContainer> scripts;
    private volatile java.util.Map<String, Path> mixinCandidateSources = java.util.Map.of();
    private final java.util.Map<String, com.tkisor.nekojs.core.pack.PackScriptView> executionEntries =
            new java.util.LinkedHashMap<>();

    /** Reads completed active entry outcomes without entering a Context or rescanning sources. */
    public synchronized com.tkisor.nekojs.core.pack.PackRuntimeView packRuntimeView() {
        return new com.tkisor.nekojs.core.pack.PackRuntimeView(scriptType, generation,
                isActiveFailed(), List.copyOf(executionEntries.values()));
    }

    /** Builds a read-only file plan; preload diagnostics do not replace active errors or outcomes. */
    public synchronized com.tkisor.nekojs.core.pack.PackExecutionPlan packExecutionPlan() {
        com.tkisor.nekojs.core.pack.ScriptPackRegistry.get().refreshLocalPacks();
        List<ScriptContainer> planned = com.tkisor.nekojs.core.ScriptLocator.discover(scriptType, scriptProperties);
        for (ScriptContainer script : planned) script.preload();
        var order = ScriptLoadOrderSorter.applyAfterOrder(planned, ScriptContainer::shouldRun);
        return new com.tkisor.nekojs.core.pack.PackExecutionPlan(scriptType,
                planned.stream().map(script -> com.tkisor.nekojs.core.pack.PackScriptView.of(script, false)).toList(),
                order.hasProblems() ? order.describe() : "",
                com.tkisor.nekojs.core.pack.ScriptPackRegistry.get().dependencyPlan().blocked());
    }

    private void recordExecution(ScriptContainer script) {
        var entry = com.tkisor.nekojs.core.pack.PackScriptView.of(script, true);
        executionEntries.put(entry.scriptId(), entry);
    }

    /**
     * Number of the committed generation, increasing monotonically.
     *
     * <p>All writes happen inside the owner thread's instance-lock critical section (initial load, kill
     * rebuild, transactional commit). {@link #generationId()} is a public read point, though, and the
     * command surface and platform callers may read it on other threads, so the field is volatile like
     * the other state published with it. The cost is negligible (one write per reload) and readers need
     * no lock of their own.
     */
    private volatile long generation;

    /**
     * Context of the candidate generation being built; null when no candidate is in progress. During the
     * candidate stages (preparation/execution/binding) this Context is alive only temporarily: timer
     * callbacks can run on the candidate execution path (the candidate flush in
     * {@code ScriptExecutor.waitForEvaluation}), but candidate listeners are only collected
     * ({@link #pendingListeners}) and never attached to the production bus, so they stay invisible until
     * commit.
     */
    private volatile Context candidateContext;

    /**
     * The full candidate environment being built, published together with {@link #candidateContext}. When
     * close preempts an in-flight candidate, it uses this to interrupt the candidate evaluation and to
     * discard all candidate resources during the deterministic teardown. It is written and cleared only
     * inside the instance-lock critical section; a stale value read by close before it takes the lock
     * only makes the interruption faster and does not affect correctness, which the
     * {@code closeRequested} check before commit guarantees.
     */
    private volatile RuntimeEnvironment candidateEnvironment;

    /** Candidate termination is tracked separately from the active {@link #contextKilled} state. */
    private volatile boolean candidateKilled;

    /** First candidate script whose execution terminated its Context, used for failure attribution. */
    private ScriptContainer candidateKillScript;

    /** Active error state saved while candidate execution is allowed to reuse script ids. */

    /**
     * Pending listener registrations collected by the candidate generation
     * ({@code EventBusJS.PendingListener}). They are read and written only on the owner thread, which
     * holds the instance lock during a reload, and are all activated at the commit point; on failure they
     * are discarded with the candidate.
     */
    private final List<com.tkisor.nekojs.api.event.EventBusJS.PendingListener> pendingListeners = new ArrayList<>();
    private final java.util.concurrent.CopyOnWriteArrayList<EventBusJS.PendingListener> activePackListeners =
            new java.util.concurrent.CopyOnWriteArrayList<>();

    /** One-shot flag: the non-transactional semantics of a STARTUP reload are warned about only once (one
     * instance per ScriptType). */
    private boolean warnedStartupReloadNonTransactional;

    /** The active Context has terminated; callback dispatch stops until explicit lifecycle recovery. */
    private volatile boolean contextKilled;

    /** Owner-thread lifecycle policy for reentrancy, close priority and failed-active isolation. */
    private final ScriptLifecycleGate lifecycleGate = new ScriptLifecycleGate();

    // ---- Constructors ----

    /**
     * Constructor that takes the set of candidate domain collectors: {@code NekoRuntimeRoot
     * .createScriptManager} passes the root-owned collector registry, which the DOMAIN_PLAN stage of a
     * reload consumes (see {@link com.tkisor.nekojs.core.lifecycle.CandidateDomainCollector}). The
     * collectors are shared by reference, so a root registration that happens after the manager was
     * created is still visible, and reads happen only inside the owner-thread critical section of a
     * reload.
     */
    /**
     * Constructor that takes the root-owned prepared cache: {@code NekoRuntimeRoot.createScriptManager}
     * passes the same instance the execution side shares (sandbox factory, module host and filesystem).
     */
    public ScriptManager(ScriptType scriptType, ScriptEventBridge scriptEventBridge, IPluginRuntime pluginRuntime, ScriptPropertyRegistry scriptProperties, ErrorTracker errorTracker, NekoJSPaths paths, SandboxConfig sandboxConfig, ScriptEnvironmentFactory environmentFactory, List<com.tkisor.nekojs.core.lifecycle.CandidateDomainCollector> domainCollectors, NekoModulePipelineCache preparationCache) {
        this.scriptType = scriptType;
        this.scriptEventBridge = scriptEventBridge;
        this.pluginRuntime = pluginRuntime;
        this.scriptProperties = scriptProperties;
        this.errorTracker = errorTracker;
        this.paths = paths;
        this.sandboxConfig = sandboxConfig;
        this.scriptExecutor = new ScriptExecutor(errorTracker, paths, sandboxConfig, this::markContextKilled);
        this.environmentFactory = environmentFactory;
        this.domainCollectors = domainCollectors;
        this.preparationCache = preparationCache;
    }

    /** Number of the currently committed generation, used in diagnostics and failure results. Callers
     * must not rely on it as a stable contract. */
    public long generationId() {
        return generation;
    }

    private String packInstance;
    private Path packModuleRoot;
    private java.util.function.BooleanSupplier packActivationAllowed = () -> true;
    private java.util.function.Supplier<List<ScriptContainer>> packDiscovery;

    public java.util.Collection<ScriptError> packErrors() { return errorTracker.getAllErrors(); }
    public Context activeContextForPackOwner() { return runtime.context(); }
    public List<EventBusJS.PendingListener> activeListenersForPackOwner() { return List.copyOf(activePackListeners); }
    public void restrictPackModules(Path root) { packModuleRoot = root; }
    public void requestPackClose(boolean interrupt) {
        lifecycleGate.requestClose();
        if (interrupt) interruptCandidateBestEffort();
    }

    /** Cancel preparation without closing a healthy active manager retained by rollback. */
    public void cancelPackCandidate() { interruptCandidateBestEffort(); }

    /** Root configures a partition before its first evaluation. */
    public void configurePackPartition(String instance,
            java.util.function.Supplier<List<ScriptContainer>> discovery) {
        if (!runtime.isEmpty()) throw new IllegalStateException("[NEKO-1017] Cannot repartition an active script manager");
        packInstance = instance;
        packDiscovery = java.util.Objects.requireNonNull(discovery);
    }

    /** A prepared unit stays inert until its root publishes the entire batch. */
    public synchronized PreparedPack preparePackCandidate(List<ScriptContainer> entries,
            java.util.function.Consumer<Context> initialize) {
        entries = new ArrayList<>(entries);
        var decision = lifecycleGate.tryEnter(ScriptLifecycleGate.Operation.RELOAD);
        if (decision != ScriptLifecycleGate.Decision.EXECUTED) throw reloadRejected(decision, "pack-prepare");
        RuntimeEnvironment candidate = null;
        try {
            candidate = createCandidateEnvironment(generation + 1);
            initialize.accept(candidate.context());
            environmentFactory.installEnvironmentBindings(candidate.context(), scriptType,
                    candidate.globals(), candidate.moduleSession());
            loadCandidateScripts(entries, candidate.context(), candidate.nodeRuntime());
            if (candidateKilled) throw new IllegalStateException("[NEKO-1017] Package candidate context was terminated");
            for (var listener : List.copyOf(pendingListeners)) listener.prepareForActivation();
            return new PreparedPack(candidate, runtime, List.copyOf(entries));
        } catch (Throwable failure) {
            try { discardCandidate(candidate); }
            catch (Throwable cleanup) { failure.addSuppressed(cleanup); }
            finally { lifecycleGate.exit(ScriptLifecycleGate.Operation.RELOAD); }
            if (failure instanceof RuntimeException exception) throw exception;
            if (failure instanceof Error error) throw error;
            throw new IllegalStateException("[NEKO-1017] Package candidate preparation failed", failure);
        }
    }

    public final class PreparedPack implements AutoCloseable {
        private final RuntimeEnvironment candidate;
        private final RuntimeEnvironment previous;
        private final List<ScriptContainer> entries;
        private final ScriptBindingSchema.Snapshot previousSchema;
        private final java.util.Map<com.tkisor.nekojs.api.data.ScriptId, ScriptError> previousErrors;
        private boolean metadataPublished;
        private boolean committed;
        private boolean finished;
        private java.util.function.BooleanSupplier activationAllowed = () -> true;

        private PreparedPack(RuntimeEnvironment candidate, RuntimeEnvironment previous, List<ScriptContainer> entries) {
            this.candidate = candidate;
            this.previous = previous;
            this.entries = entries;
            previousSchema = candidate.moduleSession().snapshotBindingSchema(scriptType);
            previousErrors = defaultErrorTracker() == null ? java.util.Map.of() : defaultErrorTracker().snapshotType(scriptType);
        }

        public Context context() { return candidate.context(); }
        public Context previousContext() { return previous.context(); }
        public com.tkisor.nekojs.core.state.GenerationGlobals globals() { return candidate.globals(); }
        public List<com.tkisor.nekojs.api.event.EventBusJS.PendingListener> listeners() { return List.copyOf(pendingListeners); }
        public CandidateDomainCollector.Handle collectionHandle() { return new CandidateCollectionHandleImpl(context()); }
        public void activationGuard(java.util.function.BooleanSupplier guard) { activationAllowed = java.util.Objects.requireNonNull(guard); }

        public void assertCanCommit() {
            if (lifecycleGate.isCloseRequested() || lifecycleGate.isClosed() || candidateKilled) {
                throw new IllegalStateException("[NEKO-1017] Package candidate was preempted before commit");
            }
        }

        /** Publishes the metadata that can still be rolled back before the state that cannot. */
        public void publishMetadata() {
            assertCanCommit();
            metadataPublished = true;
            environmentFactory.publishEnvironmentBindings(context(), candidate.moduleSession());
            if (defaultErrorTracker() != null) defaultErrorTracker().publishCandidateErrors(scriptType, context());
            activateModuleViews(context(), candidate.moduleSession(), generation + 1);
        }

        /** No guest code runs in this route switch. Old resources are released after every unit switches. */
        public void commitRoute() {
            runtime = candidate;
            scripts = entries;
            generation++;
            executionEntries.clear();
            for (var script : entries) {
                var view = com.tkisor.nekojs.core.pack.PackScriptView.of(script, true);
                executionEntries.put(view.scriptId(), view);
            }
            contextKilled = false;
            packActivationAllowed = activationAllowed;
            candidateContext = null;
            candidateEnvironment = null;
            candidateKilled = false;
            activePackListeners.clear(); activePackListeners.addAll(pendingListeners);
            pendingListeners.clear();
            lifecycleGate.clearActiveFailed();
            committed = true;
        }

        @Override
        public void close() {
            synchronized (ScriptManager.this) {
                if (finished) return;
                finished = true;
                try {
                    if (committed) {
                        if (!previous.isEmpty()) closeRuntimeResources(previous);
                    } else {
                        releaseAll(() -> {
                            if (metadataPublished) {
                                candidate.moduleSession().restoreBindingSchema(scriptType, previousSchema);
                                if (defaultErrorTracker() != null) defaultErrorTracker().restoreType(scriptType, previousErrors);
                            }
                        }, () -> discardCandidate(candidate));
                    }
                } finally {
                    lifecycleGate.exit(ScriptLifecycleGate.Operation.RELOAD);
                }
            }
        }
    }

    /**
     * Registration hook for api.event callbacks (called by {@code EventBusJS.execute}): when the Context
     * that registers is the candidate generation being built, the pending registration goes into the
     * candidate collector and this returns true (the registration stays invisible to the production bus
     * and is activated at the commit point); otherwise it returns false, meaning the normal registration
     * path, where the caller activates it.
     *
     * <p>Threading: candidate construction and collection both happen on the owner thread of a reload,
     * which holds the instance lock, so there are no concurrent writes. The full owner-thread queue and
     * reentrant scheduling are handled elsewhere.
     */
    public static boolean collectPendingListener(Context context, com.tkisor.nekojs.api.event.EventBusJS.PendingListener pending) {
        if (context == null || pending == null) return false;
        ScriptManager manager = CONTEXT_TO_MANAGER.get(context);
        if (manager == null) return false;
        Context candidate = manager.candidateContext;
        if (candidate == null || !candidate.equals(context)) {
            if (context.equals(manager.runtime.context())) manager.activePackListeners.add(pending);
            return false;
        }
        manager.pendingListeners.add(pending);
        return true;
    }

    // ---- Candidate domain plans ----

    /** Returns only an engine-resolved candidate entry, never a caller-supplied path. */
    public static Path mixinCandidateSource(Context context) {
        ScriptManager manager = CONTEXT_TO_MANAGER.get(context);
        if (manager == null || manager.candidateContext == null || !manager.candidateContext.equals(context)
                || manager.candidateEnvironment == null) throw new IllegalStateException("[NEKO-4091] Mixin registration requires an executing candidate Context");
        if (!manager.candidateEnvironment.moduleSession().onlyLocalSources()) throw new IllegalStateException("[NEKO-4091] Mixin registration rejects candidate sessions with remote sources");
        String scriptId = ScriptContextRegistry.currentScriptIdOf(context);
        Path source = manager.mixinCandidateSources.get(scriptId);
        if (source == null) throw new IllegalStateException("[NEKO-4091] Candidate entry source is unavailable: script=" + scriptId);
        return source;
    }

    /** Preflight repeats provenance checks after the candidate has loaded all dependencies. */
    public static boolean mixinCandidateOnlyLocal(Context context) {
        ScriptManager manager = CONTEXT_TO_MANAGER.get(context);
        return manager != null && manager.candidateContext != null && manager.candidateContext.equals(context)
                && manager.candidateEnvironment != null && manager.candidateEnvironment.moduleSession().onlyLocalSources();
    }

    /** Native interception conservatively bypasses all script callbacks during any candidate transaction. */
    public static boolean isAnyCandidatePreparing() {
        return CONTEXT_TO_MANAGER.values().stream().anyMatch(manager -> manager.candidateContext != null);
    }

    /**
     * Registers a plan while a candidate runs: when the source Context belongs to the candidate
     * generation being built, the plan is attached to that candidate's {@code GenerationGlobals} (the
     * STATE_PLAN stage prechecks all plans together, and they are all published at the commit point or
     * none of them on failure), and this returns true. A non-candidate Context returns false, and the
     * caller decides whether to reject. Java-side bindings and test plans enter this way, so domain
     * semantics never reach the global owner.
     */
    public static boolean registerCandidatePlan(Context context,
            com.tkisor.nekojs.core.state.CandidateStatePlan plan) {
        if (context == null || plan == null) return false;
        ScriptManager manager = CONTEXT_TO_MANAGER.get(context);
        if (manager == null) return false;
        Context candidate = manager.candidateContext;
        if (candidate == null || !candidate.equals(context)) return false;
        RuntimeEnvironment candidateEnvironment = manager.candidateEnvironment;
        if (candidateEnvironment == null || candidateEnvironment.globals() == null) return false;
        candidateEnvironment.globals().addPlan(plan);
        return true;
    }

    // ---- UI roots owned by a generation ----

    /**
     * Registers a UI root: attaches the root to the {@code GenerationGlobals} of the generation that
     * owns the source Context. A Context of a candidate being built attaches to the candidate globals
     * (invisible to production before commit, discarded with the candidate on failure); an active
     * Context attaches to the active globals (released when the old generation closes as a commit
     * switches generations). Returns that generation's binding info (globals, whether candidate,
     * generation number) for the caller's epoch checks, and returns null when the Context is neither
     * candidate nor active (superseded, closed or unregistered). In that case the caller must fail
     * creation explicitly instead of attaching to any global registry.
     */
    public static UiRootBinding registerUiRoot(Context context,
            com.tkisor.nekojs.core.state.GenerationGlobals.UiRoot root) {
        if (context == null || root == null) return null;
        ScriptManager manager = CONTEXT_TO_MANAGER.get(context);
        if (manager == null) return null;
        RuntimeEnvironment candidateEnvironment = manager.candidateEnvironment;
        if (candidateEnvironment != null && candidateEnvironment.context() != null
                && candidateEnvironment.context().equals(context)
                && candidateEnvironment.globals() != null) {
            candidateEnvironment.globals().registerUiRoot(root);
            return new UiRootBinding(candidateEnvironment.globals(), true, manager.generation + 1);
        }
        RuntimeEnvironment active = manager.runtime;
        if (active.context() != null && active.context().equals(context) && active.globals() != null) {
            active.globals().registerUiRoot(root);
            return new UiRootBinding(active.globals(), false, manager.generation);
        }
        return null;
    }

    /** Generation binding result for a UI root: when the root attaches to a candidate, the number is the
     * next generation, still in flight. */
    public record UiRootBinding(com.tkisor.nekojs.core.state.GenerationGlobals globals,
                                boolean candidate, long generation) {}

    // ---- Candidate collection handles: built by the DOMAIN_PLAN stage, alive only inside the collect
    // call ----

    /**
     * Implementation of {@link CandidateDomainCollector.Handle}: collection dispatch reads only the
     * subset of this candidate's {@link #pendingListeners} that belongs to the target bus (nothing is
     * attached to the production bus before commit) and sorts it stably in the same order as the
     * {@code EventBusBase} compiled snapshot (priority from high to low, registration order within one
     * priority). Exceptions from listener callbacks propagate up to the collection logic, which fails the
     * candidate or handles them itself.
     */
    private final class CandidateCollectionHandleImpl implements CandidateDomainCollector.Handle {

        private final Context candidate;

        CandidateCollectionHandleImpl(Context candidate) {
            this.candidate = candidate;
        }

        @Override
        public Context candidateContext() {
            return candidate;
        }

        @Override
        public ScriptType scriptType() {
            return ScriptManager.this.scriptType;
        }

        @Override
        public List<com.tkisor.nekojs.api.event.EventBusJS.PendingListener> listenersOf(
                com.tkisor.nekojs.api.event.EventBusJS<?, ?> bus) {
            if (bus.canDispatch()) {
                // A key-dispatched bus needs a key to decide its delivery subset, and collection dispatch has no
                // key context. Reject it explicitly instead of silently degrading to a full dispatch that
                // ignores the key; see the Handle#listenersOf contract.
                throw new UnsupportedOperationException(
                        "domain collection dispatch does not support key-dispatched buses: " + bus);
            }
            List<com.tkisor.nekojs.api.event.EventBusJS.PendingListener> ofBus = new ArrayList<>();
            for (com.tkisor.nekojs.api.event.EventBusJS.PendingListener pending : pendingListeners) {
                if (pending.owner() == bus) {
                    ofBus.add(pending);
                }
            }
            // Priority from high to low (HIGHEST = Byte.MAX_VALUE first); List.sort is stable, so listeners with
            // the same priority keep their registration order.
            ofBus.sort((a, b) -> -Byte.compare(a.priority(), b.priority()));
            return List.copyOf(ofBus);
        }

        @Override
        public void execute(com.tkisor.nekojs.api.event.EventBusJS.PendingListener listener, Object event) {
            listener.executeForCollection(event);
        }

        @Override
        public void registerPlan(com.tkisor.nekojs.core.state.CandidateStatePlan plan) {
            if (!registerCandidatePlan(candidate, plan)) {
                throw new IllegalStateException("candidate plan registration failed for " + plan.domain()
                        + ": source context is not the candidate being built");
            }
        }
    }

    // ---- Callback markers, public observation points and explicit scheduling entry points ----

    /**
     * Marks entry into a managed callback: the four EventBusJS dispatch points and the NekoNodeTimers
     * callback body call this before and after they run a guest callback. It only bumps a counter local
     * to the calling thread, takes no lock and does not touch the Context. A lifecycle request on the
     * same thread that sees a non-zero count is rejected right away; it neither recurses nor waits on
     * its own queue.
     */
    public static void noteCallbackEnter() {
        ScriptLifecycleGate.enterCallback();
    }

    /** Marks the end of a managed callback, paired with {@link #noteCallbackEnter()}; call it in a
     * finally block. */
    public static void noteCallbackExit() {
        ScriptLifecycleGate.exitCallback();
    }

    /** Whether a terminated active generation is isolated pending explicit reload/load or close. */
    public boolean isActiveFailed() {
        return lifecycleGate.isActiveFailed();
    }

    /**
     * Whether terminal close has finished. After this returns true, every new lifecycle request
     * is rejected.
     */
    public boolean isClosed() {
        return lifecycleGate.isClosed();
    }

    /**
     * Whether close has been requested but has not finished yet. A reload or load that has not
     * started yet is rejected because of it.
     */
    public boolean isCloseRequested() {
        return lifecycleGate.isCloseRequested();
    }

    /**
     * Explicit reload entry for callbacks and any thread. It uses the same scheduling gate as
     * {@link #reloadScripts()}. When the gate rejects the call (reentrant, inside a callback,
     * closing, already closed), it returns the decision and does no work. When the gate lets the
     * call through, the reload runs synchronously: a call from another thread first queues on the
     * instance lock monitor, and that queueing is itself observable. A failure inside the work
     * still throws {@link NekoReloadException} (structured phase result), same as a direct call.
     */
    public synchronized ScriptLifecycleGate.Decision requestReload() {
        ScriptLifecycleGate.Decision decision =
                lifecycleGate.tryEnter(ScriptLifecycleGate.Operation.RELOAD);
        if (decision != ScriptLifecycleGate.Decision.EXECUTED) {
            return decision;
        }
        try {
            doReloadScripts();
            return ScriptLifecycleGate.Decision.EXECUTED;
        } finally {
            lifecycleGate.exit(ScriptLifecycleGate.Operation.RELOAD);
        }
    }

    /**
     * Explicit scheduling entry for a non-owner thread or a thread created by guest code that
     * needs to reach a managed lifecycle. The work runs in the instance lock monitor queue,
     * in the same sequence as evaluate/reload/close, so it never touches the Context, bindings,
     * listeners or timers concurrently with another lifecycle. This method does not enter the
     * lifecycle gate itself; lifecycle calls inside the work pass it individually. Guest
     * advanced Java/thread features (allowThreads and the like) are unaffected; only the
     * entry point into a managed lifecycle is constrained. Once closed, the call is rejected.
     */
    public synchronized <T> T scheduleOnOwner(Callable<T> work) throws Exception {
        if (lifecycleGate.isClosed()) {
            throw new IllegalStateException(
                    "ScriptManager[" + scriptType.name + "] is closed; scheduleOnOwner rejected");
        }
        if (work == null) {
            throw new NullPointerException("scheduleOnOwner work");
        }
        return work.call();
    }

    // ---- Configuration ----

    public void setJavaClassLoadTelemetrySink(JavaClassLoadTelemetrySink sink) {
        JavaClassLoadTelemetry.setSink(sink);
    }

    // ---- Context access (lazy initialization) ----

    /** Marks termination only for the active or candidate Context, never an obsolete generation. */
    void markContextKilled(Context context) {
        Context candidate = this.candidateContext;
        if (candidate != null && candidate.equals(context)) {
            this.candidateKilled = true;
        } else {
            Context active = this.runtime.context();
            if (active == null || !active.equals(context)) {
                // Kill report from a leftover closure of an old generation, dispatched concurrently
                // between the commit sweep and the bus activation. Such a Context is neither active
                // nor candidate, so isContextDead already reports it dead and nothing has side
                // effects; it is ignored here too. Otherwise a healthy new active would be marked
                // as an isolated failure, and the next getOrCreateEnvironment would rebuild it,
                // breaking the rule that a second active is never created automatically.
                return;
            }
            this.contextKilled = true;
            // Dead callbacks remain isolated; only explicit lifecycle work may recover the active generation.
            this.lifecycleGate.markActiveFailed();
        }
    }

    private synchronized RuntimeEnvironment getOrCreateEnvironment() {
        if (runtime.isEmpty() || contextKilled) {
            if (!runtime.isEmpty()) {
                // Release registrations and resources belonging to the terminated Context.
                // This must match the teardown in resetEnvironment / close: listener closures hold
                // Values that point at the dead Context, and the errorTracker, modules, ESM cache
                // and binding state also belong to the old environment. Miss one step and a kill
                // rebuild silently carries dirty state, for example leftover errorTracker entries
                // that are never cleared, or a cached old Context helper that reports closed on
                // next use. Scripts cannot be re-run here to rebuild listeners (a rebuild only
                // creates an empty environment); recovery needs another reload.
                resetEnvironment();
            }
            NekoModulePipelineCache moduleSession = preparationCache.openSession(
                    preparationCache.activeBindingSchemaView(scriptType));
            ScriptEnvironmentFactory.Environment env;
            RuntimeEnvironment created = null;
            com.tkisor.nekojs.core.state.GenerationGlobals generation =
                    environmentFactory.newGeneration(scriptType, false);
            try {
                env = environmentFactory.createContext(scriptType, generation, moduleSession);
                created = new RuntimeEnvironment(
                        env.context(), env.nodeRuntime(), env.outStream(), env.errStream(), env.globals(), moduleSession);
                environmentFactory.installEnvironmentBindings(env.context(), scriptType, env.globals(), moduleSession);
                environmentFactory.publishEnvironmentBindings(env.context(), moduleSession);
            } catch (RuntimeException | Error failure) {
                if (created != null) {
                    closeRuntimeResources(created);
                } else {
                    generation.close();
                    moduleSession.closeSession();
                }
                throw failure;
            }
            activateModuleViews(created.context(), moduleSession, this.generation + 1);
            this.runtime = created;
            CONTEXT_TO_MANAGER.put(created.context(), this);
            ScriptContextRegistry.bind(created.context(), scriptType);
            contextKilled = false;
            // A rebuild on an explicit lifecycle entry counts as recovery, so the isolated failure
            // is cleared. Only explicit lifecycle calls (the loadScripts / reloadScriptFile path)
            // reach this method; the timer/event dispatch path never does, so it cannot clear the
            // isolated failure by accident.
            lifecycleGate.clearActiveFailed();
            // A new active environment instance means a new generation: an initial load and a kill
            // rebuild each bump it once, and a transactional reload bumps it at the commit point.
            this.generation++;
            return created;
        }
        return runtime;
    }

    private Context getOrCreateContext() {
        return getOrCreateEnvironment().context();
    }

    // ---- Script discovery ----

    /**
     * Discovers the script files that belong to this type.
     */
    public void discoverScripts() {
        List<ScriptContainer> discovered = discoverWithPacks();
        this.scripts = discovered;
        com.tkisor.nekojs.script.ScriptTypeEnv.logger(scriptType).info("发现了 {} 个 {} 脚本 — scripts discovered", discovered.size(), scriptType.name());
    }

    /**
     * Refreshes local pack intent before discovery. World mounting remains lifecycle-owned.
     */
    private List<ScriptContainer> discoverWithPacks() {
        if (packDiscovery != null) return new ArrayList<>(packDiscovery.get());
        com.tkisor.nekojs.core.pack.ScriptPackRegistry.get().refreshLocalPacks();
        return ScriptLocator.discover(scriptType, scriptProperties);
    }

    /**
     * Unregisters everything a world script pack registered on this type: it removes event
     * listeners by the pack's scriptId prefix (including dynamic event definitions) and cancels
     * its timers. Platform hooks call this on serverStopped and on disconnect. It is not a full
     * reload; the Context and the flat/global pack scripts stay alive.
     */
    public synchronized void clearWorldPackListeners(List<com.tkisor.nekojs.core.pack.ScriptPack> packs) {
        for (com.tkisor.nekojs.core.pack.ScriptPack pack : packs) {
            if (pack.scope() != com.tkisor.nekojs.core.pack.ScriptPackScope.WORLD) continue;
            String prefix = pack.scriptIdPrefix(scriptType);
            scriptEventBridge.clearListenersByPrefix(scriptType, prefix);
            NekoNodeRuntime nodeRuntime = this.runtime.nodeRuntime();
            if (nodeRuntime != null) {
                nodeRuntime.timers().cancelScriptByPrefix(prefix);
            }
            executionEntries.values().removeIf(entry -> entry.scriptId().startsWith(prefix));
        }
    }

    // ---- Script loading and execution ----

    /**
     * Loads and runs all scripts in order.
     *
     * <p>synchronized: shares the instance lock with {@link #reloadScripts()} and
     * {@link #reloadScriptFile(String)}. The first load (platform init / client setup) and a
     * command-triggered reload can come from different threads; running them concurrently would
     * let both threads create a candidate Context in {@link #getOrCreateContext()}, and the
     * loser's Context (with its timer dispatch thread) would never be closed. The lock is
     * reentrant, so a reloadScripts call into this method is unaffected.
     */
    public synchronized void loadScripts() {
        if (scriptType != ScriptType.STARTUP && scriptEventBridge.requiresTransactionalLoad(scriptType)) {
            reloadScripts();
            return;
        }
        // The instance lock serializes evaluate/reload/close for one ScriptType; the gate only
        // decides reentrancy (a nested LOAD inside the STARTUP RELOAD is allowed), requests made
        // inside a callback, and close priority or already closed. On rejection it throws a
        // structured failure without recursing and without waiting on its own queue.
        ScriptLifecycleGate.Decision decision =
                lifecycleGate.tryEnter(ScriptLifecycleGate.Operation.LOAD);
        if (decision != ScriptLifecycleGate.Decision.EXECUTED) {
            throw reloadRejected(decision, "load");
        }
        try {
            doLoadScripts();
            // A success on an explicit load entry counts as recovery. But if the active was killed
            // during this round (contextKilled, for example from a runaway entry), the isolated
            // failure must survive instead of being cleared right after it was set.
            if (!contextKilled) {
                lifecycleGate.clearActiveFailed();
            }
        } finally {
            lifecycleGate.exit(ScriptLifecycleGate.Operation.LOAD);
        }
    }

    private void doLoadScripts() {
        // Initial loading owns its progress session; a nested STARTUP load leaves completion
        // to the surrounding reload when begin reports an existing session.
        final boolean progressOwned = ReloadProgressTracker.begin(scriptType.name, 1);
        try {
            pluginRuntime.fireBeforeScriptsLoaded(scriptType);
            try {
                loadScriptsInto(scripts);
                if (scriptType == ScriptType.STARTUP) {
                    flushReadyNodeTimers();
                    ScriptEvents.post(getScriptEventRegistrar());
                }
            } finally {
                pluginRuntime.fireAfterScriptsLoaded(scriptType);
            }
            ReloadProgressTracker.step(scriptType.name, "scripts executed");
            if (progressOwned) {
                ReloadProgressTracker.finish(scriptType.name, true);
            }
        } catch (Throwable t) {
            if (progressOwned) {
                ReloadProgressTracker.finish(scriptType.name, false);
            }
            throw t;
        }
    }

    /** Executes initial-load entries with the current environment and retains per-entry error attribution. */
    private void loadScriptsInto(List<ScriptContainer> scriptsToLoad) {
        executionEntries.clear();
        if (!prepareScriptsForLoad(scriptsToLoad)) {
            return;
        }
        for (ScriptContainer script : scriptsToLoad) {
            if (script.shouldRun()) {
                // Each entry observes the current owner-managed environment.
                RuntimeEnvironment env = getOrCreateEnvironment();
                scriptExecutor.executeEntry(env.context(), script, env.nodeRuntime(), env.moduleSession());
            }
            recordExecution(script);
        }
    }

    private boolean prepareScriptsForLoad(List<ScriptContainer> scriptsToLoad) {
        return prepareScriptsForLoad(scriptsToLoad, null);
    }

    private boolean prepareScriptsForLoad(List<ScriptContainer> scriptsToLoad, Context context) {
        if (scriptsToLoad == null || scriptsToLoad.isEmpty()) {
            com.tkisor.nekojs.script.ScriptTypeEnv.logger(scriptType).info("没有需要加载的 {} 脚本 — no scripts to load", scriptType.name());
            return false;
        }
        for (var script : scriptsToLoad) {
            script.preload();
            reportPreloadFailure(script, context);
        }
        ScriptLoadOrderSorter.Result orderResult =
                ScriptLoadOrderSorter.applyAfterOrder(scriptsToLoad, ScriptContainer::shouldRun);
        if (orderResult.hasProblems()) {
            com.tkisor.nekojs.script.ScriptTypeEnv.logger(scriptType).warn("[NEKO-1004] {} 脚本 after 依赖排序存在问题：{} — problem in after dependency order", scriptType.name(), orderResult.describe());
        }
        return true;
    }

    /**
     * When a preload read fails (encoding, permission, the file disappeared), {@code disabled}
     * is set and {@code shouldRun()} skips the script silently; nothing reads the old
     * {@code lastError}, so the script simply vanishes from the logs and the error panel. This
     * reports to the error panel and writes an error log, so "why didn't my script run" stays
     * visible.
     */
    private void reportPreloadFailure(ScriptContainer script) {
        reportPreloadFailure(script, null);
    }

    private void reportPreloadFailure(ScriptContainer script, Context context) {
        if (script.disabled && script.lastError != null) {
            errorTracker.record(context, script, script.lastError);
            com.tkisor.nekojs.script.ScriptTypeEnv.logger(scriptType).error("[NEKO-1005] 无法读取脚本 {}，已跳过：{} — script unreadable, skipped", script.path, script.lastError.toString());
        }
    }

        // ---- Reload ----

        public synchronized void reloadScripts () {
            // Reload scheduling gate: concurrent reloads queue on the instance lock monitor, so
            // each round has exactly one candidate. A reentrant call on the same thread or a
            // request from inside a callback is rejected outright, as is a request while
            // closeRequested or closed.
            ScriptLifecycleGate.Decision decision =
                    lifecycleGate.tryEnter(ScriptLifecycleGate.Operation.RELOAD);
            if (decision != ScriptLifecycleGate.Decision.EXECUTED) {
                throw reloadRejected(decision, "reload");
            }
            try {
                doReloadScripts();
            } finally {
                lifecycleGate.exit(ScriptLifecycleGate.Operation.RELOAD);
            }
        }

        private void doReloadScripts () {
            ReloadProgressTracker.begin(scriptType.name, scriptType == ScriptType.STARTUP ? 3 : 5);
            boolean progressSuccess = false;
            try {
                com.tkisor.nekojs.script.ScriptTypeEnv.logger(scriptType).info("正在重载 {} 脚本... — reloading scripts", scriptType.name());
                if (scriptType == ScriptType.STARTUP) {
                    if (!warnedStartupReloadNonTransactional) {
                        warnedStartupReloadNonTransactional = true;
                        com.tkisor.nekojs.script.ScriptTypeEnv.logger(scriptType).warn(
                                "[NEKO-1006] {} 脚本重载为非事务式语义（STARTUP 涉及物品/方块/实体等不可逆注册，无法安全回滚）；"
                                        + "若重载期间脚本出错，已注册内容不会回退 — startup reload is not transactional",
                                scriptType.name());
                    }
                    // STARTUP registers things that cannot be undone (items, blocks, entities), so
                    // it cannot roll back safely. It keeps the reset+load behavior.
                    resetEnvironment();
                    ReloadProgressTracker.step(scriptType.name, "environment reset");
                    discoverScripts();
                    ReloadProgressTracker.step(scriptType.name, "scripts discovered");
                    loadScripts();
                    ReloadProgressTracker.step(scriptType.name, "scripts executed");
                } else {
                    reloadScriptsTransactional();
                }
                progressSuccess = true;
                com.tkisor.nekojs.script.ScriptTypeEnv.logger(scriptType).info("{} 脚本重载完毕 — script reload finished", scriptType.name());
            } finally {
                ReloadProgressTracker.finish(scriptType.name, progressSuccess);
            }
        }

        /**
         * Transactional full reload. A candidate generation goes through preparation, binding and
         * execution (including event plan collection), and only then does a single commit point
         * switch it to active. If any stage fails, the candidate is closed and the active is kept.
         *
         * <p>Generation isolation:
         * <ul>
         *   <li>The candidate Context, binding installation and script execution leave the
         *       production routing of the active untouched throughout. Candidate listeners are
         *       only collected as {@link EventBusJS.PendingListener} (they are not on the bus
         *       before commit), and candidate timers only go into the candidate's own node
         *       runtime (the production tick flush still flushes the active);</li>
         *   <li>On failure, the candidate Context, timers, listeners (pending registrations) and
         *       compiled module artifacts are all closed, and the active Context, listeners,
         *       timers and script state stay usable as they were;</li>
         *   <li>On commit: the candidate state is published, then the pending activation batch
         *       atomically replaces the old listeners, then the old module session is released,
         *       then the old environment is released in timer-before-Context ownership order.</li>
         * </ul>
         *
         * <p>The commit point cannot fail. Before listeners are activated, the EVENT_PLAN stage
         * already moved work that would throw during commit, such as converting dispatch keys,
         * forward ({@link EventBusJS.PendingListener#prepareForActivation()}). So any candidate
         * failure happens before commit and always takes the {@link #discardCandidate} path;
         * there is never a half-activated generation where the old listeners are already swept,
         * the candidate runtime is published and activation throws midway.
         *
         * <p>Threading: the candidate is built and executed synchronously on the calling (owner)
         * thread, and an instance lock serializes reloads. Candidate work never calls
         * {@link com.tkisor.nekojs.api.data.Binding#close(ScriptType)}: that callback may clean
         * process-wide dynamic registry, post-effect or platform listener state, and a failed
         * candidate cannot restore those live mutations. Reversible candidate plans for domain
         * adapters belong to the candidate collectors; old-generation resources are released only
         * on a successful commit or on a full manager teardown.
         */
        private void reloadScriptsTransactional () {
            ReloadProgressTracker.begin(scriptType.name, 7);
            boolean progressSuccess = false;
            try {
                com.tkisor.nekojs.script.ScriptTypeEnv.logger(scriptType).info("正在重载 {} 脚本... — reloading scripts", scriptType.name());
                long candidateGeneration = this.generation + 1;
                RuntimeEnvironment oldEnvironment = this.runtime;
                RuntimeEnvironment candidateEnvironment = null;
                try {
                    // Candidate errors are staged per Context. Active errors stay live, so an
                    // active callback observed while the candidate runs is not lost if the
                    // candidate fails.
                    // ---- Phase PREPARATION: candidate Context + node runtime (production routing untouched) ----
                    try {
                        candidateEnvironment = createCandidateEnvironment(candidateGeneration);
                    } catch (Throwable t) {
                        throw reloadFailure(candidateGeneration, ReloadPhase.PREPARATION, null, "candidate-context", t);
                    }
                    ReloadProgressTracker.step(scriptType.name, "candidate environment created");

                    // ---- Phase BINDING: install event groups, plugin bindings, schema and the
                    // global+shared views into the candidate Context ----
                    try {
                        environmentFactory.installEnvironmentBindings(
                                candidateEnvironment.context(), scriptType, candidateEnvironment.globals(),
                                candidateEnvironment.moduleSession());
                    } catch (Throwable t) {
                        throw reloadFailure(candidateGeneration, ReloadPhase.BINDING, null, "binding-install", t);
                    }
                    ReloadProgressTracker.step(scriptType.name, "candidate bindings installed");

                    // ---- Phase EXECUTION + EVENT_PLAN: run the candidate scripts; listeners are
                    // only collected and timers only go into the candidate collector ----
                    List<ScriptContainer> candidateScripts;
                    try {
                        candidateScripts = discoverWithPacks();
                        com.tkisor.nekojs.script.ScriptTypeEnv.logger(scriptType).info("发现了 {} 个 {} 脚本 — scripts discovered", candidateScripts.size(), scriptType.name());
                        ReloadProgressTracker.step(scriptType.name, "discovered " + candidateScripts.size() + " scripts");

                        this.candidateKilled = false;
                        this.candidateKillScript = null;
                        pluginRuntime.fireBeforeScriptsLoaded(scriptType);
                        try {
                            loadCandidateScripts(candidateScripts, candidateEnvironment.context(), candidateEnvironment.nodeRuntime());
                        } finally {
                            pluginRuntime.fireAfterScriptsLoaded(scriptType);
                        }
                        ReloadProgressTracker.step(scriptType.name, "candidate scripts executed");

                        if (this.candidateKilled) {
                            // Never commit a terminated candidate; preserve close-preemption attribution.
                            boolean preempted = lifecycleGate.isCloseRequested() || lifecycleGate.isClosed();
                            throw reloadFailure(candidateGeneration, ReloadPhase.EXECUTION,
                                    sourceOf(this.candidateKillScript),
                                    preempted ? "close-preempted" : "candidate-killed",
                                    new RuntimeException(scriptType.name()
                                            + (preempted
                                                    ? " reload preempted by close during candidate execution"
                                                    : " candidate context was terminated during reload")));
                        }
                    } catch (NekoReloadException f) {
                        throw f;
                    } catch (Throwable t) {
                        throw reloadFailure(candidateGeneration, ReloadPhase.EXECUTION, null, "script-execution", t);
                    }

                    // ---- Phase EVENT_PLAN: completeness and mountability preparation for the
                    // candidate's pending listeners ----
                    // The commit point cannot be rolled back, so work that would throw during
                    // commit is moved here: converting an EventBusJS dispatch key
                    // (Value.as(keyType)). Candidate listeners are still not on the production bus
                    // before commit; this stage only resolves keys and produces no bus or mirror
                    // side effects. A failure therefore happens before commit and takes the
                    // discardCandidate path (candidate resources all closed, active untouched),
                    // instead of leaving a half-activated state where the old listeners are
                    // already swept, the candidate runtime is published and activation throws
                    // midway. The activation step in commitGeneration is then left with
                    // registrations that cannot throw.
                    try {
                        for (var pending : List.copyOf(this.pendingListeners)) {
                            try {
                                pending.prepareForActivation();
                            } catch (Throwable t) {
                                throw reloadFailure(candidateGeneration, ReloadPhase.EVENT_PLAN,
                                        sourceOfScriptId(candidateScripts, pending.scriptId()),
                                        "candidate-listener-plan", t);
                            }
                        }
                    } catch (NekoReloadException f) {
                        throw f;
                    } catch (Throwable t) {
                        throw reloadFailure(candidateGeneration, ReloadPhase.EVENT_PLAN, null,
                                "candidate-listener-plan", t);
                    }
                    ReloadProgressTracker.step(scriptType.name, "candidate event plan prepared");

                    // ---- Phase DOMAIN_PLAN: domain collection ----
                    // root-owned domain collectors dispatch their collection events into the
                    // candidate's pending listeners (they read only this candidate's
                    // pendingListeners subset and never touch the production bus) and produce
                    // inert domain plans (such as Item/Block modification) that are attached to
                    // the candidate plan. Together with the global/shared top-level write set they
                    // are preflighted in STATE_PLAN and published jointly at the commit point:
                    // success or failure happens as a whole, never half committed. An exception
                    // from a collector or from a listener callback propagates from here, fails the
                    // candidate and keeps the old active; collection order is collector
                    // registration order.
                    for (com.tkisor.nekojs.core.lifecycle.CandidateDomainCollector collector : List.copyOf(domainCollectors)) {
                        if (collector.scriptType() != scriptType) continue;
                        try {
                            collector.collect(new CandidateCollectionHandleImpl(candidateEnvironment.context()));
                        } catch (NekoReloadException f) {
                            throw f;
                        } catch (Throwable t) {
                            throw reloadFailure(candidateGeneration, ReloadPhase.DOMAIN_PLAN, null,
                                    "domain-collect:" + collector.domain(),
                                    new IllegalStateException(scriptType.name + " candidate domain collection failed for '"
                                            + collector.domain() + "'", t));
                        }
                    }
                    ReloadProgressTracker.step(scriptType.name, "candidate domain plans collected");

                    // ---- Phase STATE_PLAN: joint preflight of managed state ----
                    // The global private write set, the shared write set and external candidate
                    // plans (CandidateStatePlan) are preflighted together: if another writer
                    // committed the same managed top-level key during the candidate (or committed
                    // to that store after a clear), it is a conflict, so the candidate fails, none
                    // of the write sets are published, and the other writer's committed values
                    // survive (no write is lost). The authoritative recheck still happens inside
                    // publishJoint at the commit point, under the lock; failing early here only
                    // attributes the conflict to the candidate stage and avoids needless commit
                    // work.
                    try {
                        candidateEnvironment.globals().preflightJoint();
                    } catch (com.tkisor.nekojs.core.state.GlobalStateException e) {
                        throw reloadFailure(candidateGeneration, ReloadPhase.STATE_PLAN, null, e.domain(), e);
                    } catch (Throwable t) {
                        throw reloadFailure(candidateGeneration, ReloadPhase.STATE_PLAN, null,
                                "state-plan-unknown", t);
                    }
                    ReloadProgressTracker.step(scriptType.name, "candidate state plan validated");

                    // ---- Close preemption check before COMMIT: close wins over a candidate that
                    // has not committed yet. When closeRequested is already set (whether the
                    // in-flight candidate is failing fast because of an interrupt or happens to
                    // reach this point), the candidate is discarded here, so a half-activated
                    // generation never appears and nothing commits after close.
                    if (lifecycleGate.isCloseRequested() || lifecycleGate.isClosed()) {
                        throw reloadFailure(candidateGeneration, ReloadPhase.COMMIT, null,
                                "close-preempted",
                                new IllegalStateException(scriptType.name()
                                        + " reload preempted by close before commit"));
                    }

                    // ---- COMMIT POINT (a single atomic switch, run synchronously on the owner thread) ----
                    commitGeneration(candidateGeneration, candidateEnvironment, candidateScripts, oldEnvironment);
                    ReloadProgressTracker.step(scriptType.name, "committed");
                    progressSuccess = true;
                    com.tkisor.nekojs.script.ScriptTypeEnv.logger(scriptType).info("{} 脚本重载完毕 — script reload finished", scriptType.name());
                } catch (NekoReloadException f) {
                    try { discardCandidate(candidateEnvironment); }
                    catch (Throwable cleanup) { f.addSuppressed(cleanup); }
                    com.tkisor.nekojs.script.ScriptTypeEnv.logger(scriptType).error("[NEKO-1008] {} 脚本事务重载失败，候选 generation 已关闭，active 环境保持不变 — transactional script reload failed",
                            scriptType.name(), f);
                    throw f;
                }
            } finally {
                ReloadProgressTracker.finish(scriptType.name, progressSuccess);
            }
        }

        /**
         * Creates the candidate generation environment: the Context, the node runtime and the
         * global/shared views of a transactional generation (candidate top-level writes go into
         * the write set and are published only at commit), and registers the Context to manager
         * mapping and the candidate marker (the liveness check of candidate timer callbacks
         * depends on that registration). reloadScriptsTransactional runs the BINDING stage
         * separately so the stage split stays as it is.
         */
        private RuntimeEnvironment createCandidateEnvironment (long candidateGeneration) {
            com.tkisor.nekojs.core.state.GenerationGlobals candidateGlobals =
                    packInstance == null ? environmentFactory.newGeneration(scriptType, true)
                            : environmentFactory.globalStores().newPackGeneration(scriptType, packInstance, true);
            NekoModulePipelineCache moduleSession = preparationCache.openSession(ScriptBindingSchema.emptyView());
            if (packModuleRoot != null) moduleSession.restrictToPackRoot(packModuleRoot);
            ScriptEnvironmentFactory.Environment candidate;
            try {
                candidate = environmentFactory.createContext(scriptType, candidateGlobals, moduleSession);
            } catch (RuntimeException | Error failure) {
                candidateGlobals.discard();
                moduleSession.closeSession();
                throw failure;
            }
            RuntimeEnvironment candidateEnvironment = new RuntimeEnvironment(
                    candidate.context(), candidate.nodeRuntime(), candidate.outStream(),
                    candidate.errStream(), candidate.globals(), moduleSession);
            try {
                activateCandidateModuleViews(candidate.context(), moduleSession, candidateGeneration);
                CONTEXT_TO_MANAGER.put(candidate.context(), this);
                ScriptContextRegistry.bind(candidate.context(), scriptType);
                this.candidateContext = candidate.context();
                this.candidateEnvironment = candidateEnvironment;
                this.candidateKilled = false;
                this.candidateKillScript = null;
                this.pendingListeners.clear();
                return candidateEnvironment;
            } catch (Throwable failure) {
                discardCandidateModuleViews(candidate.context());
                environmentFactory.discardEnvironmentBindings(candidate.context(), moduleSession);
                CONTEXT_TO_MANAGER.remove(candidate.context());
                ScriptContextRegistry.unbind(candidate.context());
                closeRuntimeResources(candidateEnvironment);
                if (failure instanceof RuntimeException runtimeFailure) throw runtimeFailure;
                if (failure instanceof Error errorFailure) throw errorFailure;
                throw new IllegalStateException("Failed to register candidate NekoJS environment", failure);
            }
        }

        /**
         * The single commit point: the atomic switch after every candidate stage has passed.
         *
         * <p>Order of steps, all inside the owner thread's critical section:
         * <ol>
         *   <li>Joint managed state publish (step 0): the global private and shared write sets
         *       plus external candidate plans are published together under one lock, with no
         *       half commit. It can throw only when no commit-time change has happened yet (a
         *       recheck conflict, or a plan violating the publish contract); throwing takes the
         *       discardCandidate path and leaves the active intact;</li>
         *   <li>Sweep the old generation's listeners. At this moment the bus type bucket only
         *       holds tokens of the old generation (candidate listeners are collected pending
         *       and never reached the bus), so clearing the whole type is the old-generation
         *       sweep. The shared static bus cannot bucket by generation, so this step and the
         *       next together amount to "switch production routing and stop the old generation
         *       from receiving"; the critical section for cross-thread dispatch is covered by
         *       owner-thread serialization;</li>
         *   <li>Publish the new runtime: the production timer flush target and the
         *       isContextDead verdict switch together, so a given event is from then on
         *       received only by the new generation (old closures are also caught by
         *       isContextDead reporting them dead);</li>
         *   <li>Activate the candidate's pending listeners: the new generation becomes the only
         *       receiver of new callbacks. Key conversion for pending registrations already
         *       happened in the candidate EVENT_PLAN stage, so only registrations that cannot
         *       throw remain here;</li>
         *   <li>Release the old module session (compiled module cache and virtual ESM URIs are
         *       cleared per type);</li>
         *   <li>Release the old environment in timer-before-Context ownership order
         *       (closeRuntimeResources: node runtime/timers, then Context, then streams). During
         *       this the old generation's global/shared guest values are invalidated by
         *       generation; non-guest values stay in the root-level store.</li>
         * </ol>
         */
        private void commitGeneration (long candidateGeneration, RuntimeEnvironment candidateEnvironment,
                List<ScriptContainer> candidateScripts, RuntimeEnvironment oldEnvironment) {
            var candidateEntries = candidateScripts.stream()
                    .map(script -> com.tkisor.nekojs.core.pack.PackScriptView.of(script, true)).toList();
            DefaultErrorTracker defaultTracker = defaultErrorTracker();
            ScriptBindingSchema.Snapshot oldSchema =
                    candidateEnvironment.moduleSession().snapshotBindingSchema(scriptType);
            java.util.Map<com.tkisor.nekojs.api.data.ScriptId, ScriptError> oldErrors =
                    defaultTracker == null ? java.util.Map.of() : defaultTracker.snapshotType(scriptType);
            List<com.tkisor.nekojs.api.event.EventBusJS.PendingListener> activation =
                    List.copyOf(this.pendingListeners);
            com.tkisor.nekojs.core.ScriptEventBridge.ListenerBatch listenerBatch = null;
            try {
                environmentFactory.publishEnvironmentBindings(
                        candidateEnvironment.context(), candidateEnvironment.moduleSession());
                if (defaultTracker != null) {
                    defaultTracker.publishCandidateErrors(scriptType, candidateEnvironment.context());
                }
                activateModuleViews(candidateEnvironment.context(), candidateEnvironment.moduleSession(),
                        candidateGeneration);
                // Prepare keeps old listener tokens installed while the remaining state commit
                // runs. Finish only after schema/errors/globals have all published successfully.
                listenerBatch = scriptEventBridge.prepareCandidateListeners(scriptType, activation);
                try {
                    candidateEnvironment.globals().publishJoint();
                } catch (com.tkisor.nekojs.core.state.GlobalStateException e) {
                    throw reloadFailure(candidateGeneration, ReloadPhase.STATE_PLAN, null, e.domain(), e);
                } catch (Throwable stateFailure) {
                        throw reloadFailure(candidateGeneration, ReloadPhase.STATE_PLAN, null,
                        "state-plan-unknown", stateFailure);
                }
                listenerBatch.finish();
                this.runtime = candidateEnvironment;
                this.scripts = candidateScripts;
                this.generation = candidateGeneration;
                this.executionEntries.clear();
                for (var entry : candidateEntries) this.executionEntries.put(entry.scriptId(), entry);
                this.contextKilled = false;
                this.candidateContext = null;
                this.candidateEnvironment = null;
                this.candidateKilled = false;
                this.activePackListeners.clear(); this.activePackListeners.addAll(this.pendingListeners);
                this.pendingListeners.clear();
                lifecycleGate.clearActiveFailed();
            } catch (Throwable failure) {
                Throwable rollbackFailure = null;
                if (listenerBatch != null) {
                    try {
                        listenerBatch.rollback();
                    } catch (Throwable cleanup) {
                        rollbackFailure = cleanup;
                    }
                }
                for (var pending : activation) {
                    try {
                        pending.deactivate();
                    } catch (Throwable cleanup) {
                        if (rollbackFailure == null) rollbackFailure = cleanup;
                        else rollbackFailure.addSuppressed(cleanup);
                    }
                }
                try {
                    candidateEnvironment.moduleSession().restoreBindingSchema(scriptType, oldSchema);
                } catch (Throwable cleanup) {
                    if (rollbackFailure == null) rollbackFailure = cleanup;
                    else rollbackFailure.addSuppressed(cleanup);
                }
                if (defaultTracker != null) {
                    try {
                        defaultTracker.restoreType(scriptType, oldErrors);
                        if (oldEnvironment.isEmpty()) {
                            defaultTracker.removeActiveModuleViews(scriptType);
                        } else {
                            activateModuleViews(oldEnvironment.context(), oldEnvironment.moduleSession(),
                                    this.generation);
                        }
                    } catch (Throwable cleanup) {
                        if (rollbackFailure == null) rollbackFailure = cleanup;
                        else rollbackFailure.addSuppressed(cleanup);
                    }
                }
                if (rollbackFailure != null) failure.addSuppressed(rollbackFailure);
                if (failure instanceof NekoReloadException reloadFailure) throw reloadFailure;
                throw reloadFailure(candidateGeneration, ReloadPhase.COMMIT, null,
                        "candidate-commit", failure);
            }
            // (4) Release the old environment in ownership order: timer, Context, streams, module session
            if (!oldEnvironment.isEmpty()) {
                closeRuntimeResources(oldEnvironment);
            }
        }

        /**
         * Discards a candidate generation: pending listeners are dropped outright (they never
         * reached the bus), the candidate global/shared write set is discarded (no top-level
         * set/delete/clear is published), and the candidate Context/timers/streams are closed in
         * timer, Context, streams order. The active runtime, scripts, listeners, timers and
         * generation number stay exactly as they were.
         */
        private void discardCandidate (RuntimeEnvironment candidateEnvironment) {
            RuntimeEnvironment candidate = candidateEnvironment != null
                    ? candidateEnvironment : this.candidateEnvironment;
            this.pendingListeners.clear();
            this.candidateContext = null;
            this.candidateEnvironment = null;
            this.candidateKilled = false;
            this.candidateKillScript = null;
            releaseAll(
                    () -> discardCandidateModuleViews(candidate == null ? null : candidate.context()),
                    () -> {
                        if (!runtime.isEmpty() && runtime.moduleSession() != null) {
                            activateModuleViews(runtime.context(), runtime.moduleSession(), this.generation);
                        }
                    },
                    () -> { if (candidate != null && candidate.globals() != null) candidate.globals().discard(); },
                    () -> { if (candidate != null && !candidate.isEmpty()) closeRuntimeResources(candidate); });
        }

        /**
         * Runs the candidate scripts one by one. The first script that triggers a candidate kill
         * is recorded and used as the source location of the failure result.
         */
        private void loadCandidateScripts (List<ScriptContainer> scriptsToLoad, Context context, NekoNodeRuntime nodeRuntime) {
            mixinCandidateSources = scriptsToLoad.stream().collect(java.util.stream.Collectors.toUnmodifiableMap(
                    script -> String.valueOf(script.id), script -> script.path));
            if (!prepareScriptsForLoad(scriptsToLoad, context)) {
                return;
            }
            for (ScriptContainer script : scriptsToLoad) {
                // Cancellation point between scripts: once close is requested, no new candidate
                // script work starts.
                if (lifecycleGate.isCloseRequested() || lifecycleGate.isClosed()) {
                    throw reloadFailure(this.generation + 1, ReloadPhase.EXECUTION,
                            sourceOf(script), "close-preempted",
                            new IllegalStateException(scriptType.name()
                                    + " candidate execution preempted by close before script "
                                    + script.id));
                }
                if (script.lastError != null) {
                    throw reloadFailure(this.generation + 1, ReloadPhase.PREPARATION,
                            sourceOf(script), "script-preload", script.lastError);
                }
                if (!script.shouldRun()) {
                    continue;
                }
                boolean killedBefore = this.candidateKilled;
                scriptExecutor.executeEntry(context, script, nodeRuntime,
                        this.candidateEnvironment.moduleSession());
                if (!killedBefore && this.candidateKilled && this.candidateKillScript == null) {
                    this.candidateKillScript = script;
                }
                if (!this.candidateKilled && script.lastError != null) {
                    throw reloadFailure(this.generation + 1, ReloadPhase.EXECUTION,
                            sourceOf(script), isCloseInterruption(script.lastError)
                                    ? "close-preempted" : "script-execution", script.lastError);
                }
            }
        }

        /** Classifies close's nonterminal Graal interrupt without marking active resources as killed. */
        private boolean isCloseInterruption(Throwable failure) {
            if (!lifecycleGate.isCloseRequested() && !lifecycleGate.isClosed()) {
                return false;
            }
            for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
                if (cause instanceof PolyglotException interruption && interruption.isInterrupted()) {
                    return true;
                }
            }
            return false;
        }

        private static String sourceOf (ScriptContainer script) {
            if (script == null) return null;
            try {
                return script.type.name + "/" + ScriptTypeEnv.scriptsDir(script.type)
                        .relativize(script.path).toString().replace('\\', '/');
            } catch (Exception ignored) {
                return script.path.toString();
            }
        }

        /**
         * Finds a script in this candidate batch by its registered script id (the text form of
         * {@code ScriptContextRegistry.currentScriptIdOf} equal to {@code ScriptContainer.id}),
         * to use as the source location of a failure in a candidate pending listener. Returns
         * null when the script is not found (for example it was removed), so the failure result
         * leaves the location empty instead of making one up.
         */
        private static String sourceOfScriptId (List<ScriptContainer> candidateScripts, String scriptId) {
            if (candidateScripts == null || scriptId == null || scriptId.isBlank()) return null;
            for (ScriptContainer script : candidateScripts) {
                if (scriptId.equals(String.valueOf(script.id))) {
                    return sourceOf(script);
                }
            }
            return null;
        }

        private NekoReloadException reloadFailure (long generation, ReloadPhase phase, String sourceLocation,
                String domain, Throwable cause) {
            return new NekoReloadException(new ReloadFailureReport(
                    scriptType, generation, phase, sourceLocation,
                    "ScriptManager[" + scriptType.name + "]", domain, cause));
        }

        /**
         * Structured failure for a gate rejection. Reentrancy or a request from inside a
         * callback, closing, and already closed each get their own domain. It does not recurse
         * and does not wait on itself.
         */
        private NekoReloadException reloadRejected (ScriptLifecycleGate.Decision decision, String action) {
            return reloadFailure(this.generation, ReloadPhase.PREPARATION, null,
                    action + "-rejected:" + decision.name(),
                    new IllegalStateException("ScriptManager[" + scriptType.name + "] " + action
                            + " rejected (" + decision.name() + "): " + rejectReason(decision)));
        }

        private static String rejectReason (ScriptLifecycleGate.Decision decision) {
            return switch (decision) {
                case REJECTED_REENTRANT -> "reentrant lifecycle or managed-callback request; recursion refused";
                case REJECTED_CLOSING -> "close in progress; close is prioritized over not-started work";
                case REJECTED_CLOSED -> "manager is closed";
                default -> "unexpected decision " + decision;
            };
        }

        public synchronized List<ScriptContainer> reloadScriptFile (String filePath) throws IOException {
            // Reload scheduling gate: a single-file reload and a whole-batch reload share one
            // mutual exclusion point, so a type still has a single sequence.
            ScriptLifecycleGate.Decision decision =
                    lifecycleGate.tryEnter(ScriptLifecycleGate.Operation.RELOAD);
            if (decision != ScriptLifecycleGate.Decision.EXECUTED) {
                throw new IOException("ScriptManager[" + scriptType.name + "] reload file " + filePath
                        + " rejected (" + decision.name() + "): " + rejectReason(decision));
            }
            try {
                return doReloadScriptFile(filePath);
            } finally {
                lifecycleGate.exit(ScriptLifecycleGate.Operation.RELOAD);
            }
        }

        private List<ScriptContainer> doReloadScriptFile (String filePath) throws IOException {
            discoverScripts();
            Path target = resolveScriptPath(filePath);

            if (scriptType == ScriptType.STARTUP) {
                // STARTUP registrations are irreversible/non-transactional and ScriptEvents
                // definitions are recreated by a full load (resetEnvironment + discoverScripts
                // + loadScripts + ScriptEvents.post). Re-posting only the affected listeners
                // would wipe unaffected custom-event listener tokens, so a targeted STARTUP
                // reload degrades to a full STARTUP reload.
                List<ScriptContainer> matched = scripts.stream()
                        .filter(script -> script.path.normalize().toAbsolutePath().equals(target))
                        .toList();
                if (matched.isEmpty()) {
                    throw new IOException("No loaded STARTUP entry matches " + displayScriptPath(target)
                            + ". Reload the whole STARTUP environment first if this file has not been loaded yet.");
                }
                com.tkisor.nekojs.script.ScriptTypeEnv.logger(scriptType).info("正在重载 STARTUP 脚本文件 {}：STARTUP 注册不可逆，退化为完整 STARTUP 重载 — startup file reload falls back to full reload", displayScriptPath(target));
                // Already inside the RELOAD gate: run the body directly instead of passing the
                // gate again, since a nested RELOAD would be rejected.
                doReloadScripts();
                List<ScriptContainer> reloadedMatches = scripts.stream()
                        .filter(script -> script.path.normalize().toAbsolutePath().equals(target))
                        .toList();
                return reloadedMatches.isEmpty() ? matched : reloadedMatches;
            }

            List<ScriptContainer> targets = reloadTargets(target);
            if (targets.isEmpty()) {
                throw new IOException("No loaded entry depends on " + displayScriptPath(target) + ". Reload the whole " + scriptType.name() + " environment first if this dependency has not been loaded yet.");
            }
            com.tkisor.nekojs.script.ScriptTypeEnv.logger(scriptType).info("正在重载 {} 脚本文件 {}，受影响入口 {} 个... — reloading one script file", scriptType.name(), displayScriptPath(target), targets.size());

            currentModuleSession().invalidate(target);
            Context ctx = getOrCreateContext();
            String modulePath = "./" + paths.root().relativize(target).toString().replace('\\', '/');

            // A single-file reload always takes the "invalidate the affected modules, then re-run
            // the affected entries" path. A ModuleSliceRelinker shortcut once tried to relink
            // without re-running entries, but Graal caches a module instance per virtual module
            // URI, so import bindings cannot see the new exports unless the entry runs again, and
            // its revision lookup was wrong (a new revision queried against the old record), which
            // made it a silent no-op that still returned success=true. The shortcut is gone.
            //
            // Invalidate and re-run must happen as a pair: the try/finally runs cleanupScriptEntry
            // (idempotent) for entries that were not re-run when something fails midway (for
            // example the invalidating eval throws or the Context rebuild fails), so nothing stays
            // half invalidated with the module tree dropped but old listeners and timers still
            // running. Recovery needs another reload.
            int rerunCount = 0;
            try {
                synchronized (ctx) {
                    for (ScriptContainer script : targets) {
                        String entryPath = "./" + paths.root().relativize(script.path).toString().replace('\\', '/');
                        ctx.eval("js", "globalThis.__nekoScriptLoader.invalidateModuleTree").execute(entryPath);
                    }
                    ctx.eval("js", "globalThis.__nekoScriptLoader.invalidateAffectedModules").execute(modulePath);
                }

                while (rerunCount < targets.size()) {
                    reloadEntryScript(getOrCreateContext(), targets.get(rerunCount));
                    rerunCount++;
                }
            } finally {
                for (int i = rerunCount; i < targets.size(); i++) {
                    cleanupScriptEntry(targets.get(i));
                }
            }

            com.tkisor.nekojs.script.ScriptTypeEnv.logger(scriptType).info("{} 脚本文件 {} 重载完毕 — script file reload finished", scriptType.name(), displayScriptPath(target));
            return targets;
        }

        public Optional<ScriptContainer> resolveScriptFile (String filePath) throws IOException {
            discoverScripts();
            Path target = resolveScriptPath(filePath);
            return scripts.stream()
                    .filter(script -> script.path.normalize().toAbsolutePath().equals(target))
                    .findFirst();
        }

        private List<ScriptContainer> reloadTargets (Path target){
            Optional<ScriptContainer> directEntry = scripts.stream()
                    .filter(script -> script.path.normalize().toAbsolutePath().equals(target))
                    .findFirst();
            Context ctx = getOrCreateContext();
            String modulePath = "./" + paths.root().relativize(target).toString().replace('\\', '/');
            List<String> affectedIds = new ArrayList<>();
            synchronized (ctx) {
                Value affected = ctx.eval("js", "globalThis.__nekoScriptLoader.affectedEntries").execute(modulePath);
                if (affected.hasArrayElements()) {
                    for (long i = 0; i < affected.getArraySize(); i++) {
                        affectedIds.add(affected.getArrayElement(i).asString());
                    }
                }
            }
            // Every affected entry recorded in the dependency graph must be re-run, including the
            // target file when it is an entry itself: Graal caches a module instance per virtual
            // module URI, so re-running only the target module does not let the import bindings of
            // already loaded entries see the new exports.
            List<ScriptContainer> affectedEntries = scripts.stream()
                    .filter(script -> affectedIds.contains(paths.root().relativize(script.path).toString().replace('\\', '/')))
                    .toList();
            if (!affectedEntries.isEmpty()) {
                return affectedEntries;
            }
            // The file was never loaded (no dependency graph record) but it is a discovered
            // script: re-run that entry directly.
            return directEntry.map(List::of).orElseGet(List::of);
        }

        private void reloadEntryScript (Context ctx, ScriptContainer script){
            cleanupScriptEntry(script);
            script.preload();
            reportPreloadFailure(script);
            if (script.shouldRun()) {
                scriptExecutor.executeEntry(ctx, script, this.runtime.nodeRuntime(),
                        this.runtime.moduleSession());
            }
            recordExecution(script);
        }

        private void cleanupScriptEntry (ScriptContainer script){
            executionEntries.remove(script.id.toString());
            activePackListeners.removeIf(listener -> listener.scriptId().equals(script.id.toString()));
            scriptEventBridge.clearListeners(scriptType, script.id.toString());
            NekoNodeRuntime nodeRuntime = this.runtime.nodeRuntime();
            if (nodeRuntime != null) {
                nodeRuntime.timers().cancelScript(script.id.toString());
            }
            errorTracker.clear(script.id);
            errorTracker.clearByScriptPath(scriptType, paths.root().relativize(script.path).toString().replace('\\', '/'));
        }

        private void fullReloadCleanup () {
            executionEntries.clear();
            activePackListeners.clear();
            releaseAll(() -> scriptEventBridge.clearListeners(scriptType),
                    () -> errorTracker.clearByType(scriptType),
                    () -> { if (!runtime.isEmpty() && packInstance == null) runtime.moduleSession().clear(scriptType); });
        }

        private DefaultErrorTracker defaultErrorTracker() {
            return errorTracker instanceof DefaultErrorTracker defaultTracker ? defaultTracker : null;
        }

        private void activateModuleViews(Context context, NekoModulePipelineCache moduleSession, long generation) {
            DefaultErrorTracker defaultTracker = defaultErrorTracker();
            if (defaultTracker != null) {
                defaultTracker.activateModuleViews(scriptType, context, moduleSession, generation);
            }
        }

        private void activateCandidateModuleViews(Context context, NekoModulePipelineCache moduleSession,
                                                  long candidateGeneration) {
            DefaultErrorTracker defaultTracker = defaultErrorTracker();
            if (defaultTracker != null) {
                defaultTracker.activateCandidateModuleViews(scriptType, context, moduleSession, candidateGeneration);
            }
        }

        private void discardCandidateModuleViews(Context context) {
            DefaultErrorTracker defaultTracker = defaultErrorTracker();
            if (defaultTracker != null) {
                defaultTracker.discardCandidateModuleViews(context);
            }
        }

        private NekoModulePipelineCache currentModuleSession() {
            if (runtime.isEmpty() || runtime.moduleSession() == null) {
                throw new IllegalStateException("ScriptManager[" + scriptType.name + "] has no active module session");
            }
            return runtime.moduleSession();
        }

        // ---- Path resolution ----

        private Path resolveScriptPath (String filePath) throws IOException {
            if (ScriptTypeEnv.scriptsDir(scriptType) == null) {
                throw new IOException("Script type has no script directory: " + scriptType.name());
            }
            if (filePath == null || filePath.isBlank()) {
                throw new IOException("Script file path is empty.");
            }
            String normalizedText = filePath.replace('\\', '/');
            String rootPrefix = scriptType.name + "/";
            if (normalizedText.startsWith(rootPrefix)) {
                normalizedText = normalizedText.substring(rootPrefix.length());
            }
            // Also accept the physical directory form (nekojs/<type>_scripts/...): the command's
            // file argument is resolved against the scripts dir, so a user-typed root-relative
            // path must not double the directory segment.
            String scriptsDirPrefix = ScriptTypeEnv.scriptsDir(scriptType).getFileName() + "/";
            if (normalizedText.startsWith(scriptsDirPrefix)) {
                normalizedText = normalizedText.substring(scriptsDirPrefix.length());
            }
            Path relative = Path.of(normalizedText).normalize();
            if (relative.isAbsolute() || relative.startsWith("..")) {
                throw new IOException("Invalid script file path: " + filePath);
            }
            Path target = ScriptTypeEnv.scriptsDir(scriptType).resolve(relative).normalize().toAbsolutePath();
            Path root = ScriptTypeEnv.scriptsDir(scriptType).normalize().toAbsolutePath();
            if (!target.startsWith(root)) {
                throw new IOException("Script file is outside " + scriptType.name() + " scripts: " + filePath);
            }
            if (!Files.isRegularFile(target) || !ScriptFilePolicy.legacyRuntime().isSupportedScriptFile(target)) {
                throw new IOException("Unsupported or missing script file: " + filePath);
            }
            return target;
        }

        private String displayScriptPath (Path path){
            return scriptType.name + "/" + ScriptTypeEnv.scriptsDir(scriptType).relativize(path).toString().replace('\\', '/');
        }

        // ---- Test scripts ----

        // synchronized: runTestScripts goes through reloadScriptsTransactional, which creates a
        // candidate Context. It shares the instance lock with loadScripts / reloadScripts /
        // close, so a command thread triggering a TEST run concurrently cannot leave the loser's
        // candidate Context leaked (Context plus timer dispatch thread).
        public synchronized void runTestScripts () {
            if (scriptType != ScriptType.TEST) {
                throw new IllegalStateException("runTestScripts() can only be called on TEST ScriptManager");
            }
            // A TEST run goes through the transactional reload, so it uses the same scheduling gate
            // as every other lifecycle. The TEST owner is the test runner or the platform owner
            // thread the command runs on.
            ScriptLifecycleGate.Decision decision =
                    lifecycleGate.tryEnter(ScriptLifecycleGate.Operation.RELOAD);
            if (decision != ScriptLifecycleGate.Decision.EXECUTED) {
                throw reloadRejected(decision, "test-run");
            }
            try {
                doRunTestScripts();
            } finally {
                lifecycleGate.exit(ScriptLifecycleGate.Operation.RELOAD);
            }
        }

        private void doRunTestScripts () {
            com.tkisor.nekojs.script.ScriptTypeEnv.logger(scriptType).info("正在运行 TEST 脚本... — running test scripts");

            // TEST also goes through the transactional reload: on failure it keeps the previous
            // TEST Context instead of destroying it and then trying to load.
            reloadScriptsTransactional();
            flushTestTimers();

            com.tkisor.nekojs.script.ScriptTypeEnv.logger(scriptType).info("TEST 脚本运行完毕 — test scripts finished");
        }

        private void flushTestTimers () {
            // One snapshot read plus one lock object: the old code re-read the volatile context
            // each round and used it as the lock, so a concurrent reload could make one round lock
            // A and the next lock B, or even NPE. The snapshot keeps the whole loop on one Context.
            RuntimeEnvironment env = this.runtime;
            NekoNodeRuntime nodeRuntime = env.nodeRuntime();
            Context context = env.context();
            if (nodeRuntime == null || context == null) return;
            // Limit of 1000 rounds (about 1s): enough for the usual asynchronous assertions after
            // await setTimeout(...). A runaway interval also stops here, so /nekojs test never
            // hangs.
            for (int i = 0; i < 1000 && nodeRuntime.hasPendingTimers(); i++) {
                synchronized (context) {
                    nodeRuntime.flushReadyTimers();
                }
                try {
                    Thread.sleep(1L);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
            synchronized (context) {
                nodeRuntime.flushReadyTimers();
            }
        }

        // ---- Environment reset / close ----

        private void resetEnvironment () {
            RuntimeEnvironment oldEnvironment = this.runtime;
            try { releaseAll(this::fullReloadCleanup, this::closeBindings, () -> closeRuntimeResources(oldEnvironment)); }
            finally { this.runtime = RuntimeEnvironment.EMPTY; }
        }

        private void closeRuntimeResources (RuntimeEnvironment environment){
            NekoNodeRuntime oldRuntime = environment.nodeRuntime();
            Context oldContext = environment.context();
            // Every owned release runs even if a previous one fails. Context teardown precedes
            // stream flushing, and generation cleanup never closes the root-owned stores.
            releaseAll(
                    () -> { if (environment.globals() != null) environment.globals().close(); },
                    () -> { if (oldRuntime != null) oldRuntime.close(); },
                    () -> { if (oldContext != null) environmentFactory.discardEnvironmentBindings(oldContext, environment.moduleSession()); },
                    () -> { if (oldContext != null && defaultErrorTracker() != null) defaultErrorTracker().removeModuleViews(oldContext); },
                    () -> { if (oldContext != null) { ScriptContextRegistry.unbind(oldContext); CONTEXT_TO_MANAGER.remove(oldContext); } },
                    () -> { if (oldContext != null) closeOwnedContext(oldContext); },
                    () -> closeStreamQuietly(environment.outStream()),
                    () -> closeStreamQuietly(environment.errStream()),
                    () -> { if (environment.moduleSession() != null) environment.moduleSession().closeSession(); });
        }

        private static void closeOwnedContext(Context context) {
            synchronized (context) {
                try { context.close(); }
                catch (PolyglotException failure) {
                    // Graal rethrows the cancellation cause when closing an already terminated
                    // Context. This terminal notification is not an unperformed resource release.
                    if (!failure.isCancelled() && !ScriptExecutor.isContextTerminated(failure)) throw failure;
                }
            }
        }

        private void closeBindings() {
            if (packInstance != null) return;
            java.util.Set<com.tkisor.nekojs.api.data.Binding> bindings = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
            bindings.addAll(pluginRuntime.bindings(scriptType).values());
            pluginRuntime.moduleBindings(scriptType).forEach(export -> bindings.add(export.binding()));
            releaseAll(bindings.stream().<Runnable>map(binding -> () -> binding.close(scriptType)).toArray(Runnable[]::new));
        }

        private void releaseAll(Runnable... releases) {
            Throwable first = null;
            for (Runnable release : releases) {
                try { release.run(); }
                catch (Throwable failure) {
                    com.tkisor.nekojs.script.ScriptTypeEnv.logger(scriptType).error(
                            "[NEKO-1017] Script resource release failed; remaining resources continue closing: type={}", scriptType.name, failure);
                    if (first == null) first = failure;
                    else if (first != failure) first.addSuppressed(failure);
                }
            }
            if (first instanceof Error error) throw error;
            if (first instanceof RuntimeException exception) throw exception;
            if (first != null) throw new IllegalStateException("[NEKO-1017] Script resource release failed", first);
        }

        private static void closeStreamQuietly (LoggerStream stream){
            if (stream == null) return;
            try {
                stream.close();
            } catch (Throwable ignored) { // A flush failure must not interrupt teardown.
            }
        }

        // Close wins: set the volatile close flag <i>before</i> taking the lock, so a new
        // lifecycle that acquires the lock afterwards is rejected by the gate. Then try to
        // interrupt the synchronous evaluation of the in-flight candidate as best we can (the
        // in-flight reload's owner thread throws on the interrupt and takes the candidate discard
        // or failure path instead of committing a partial result; the time close waits on the
        // instance lock for it to exit is bounded by cancellation points). Finally take the lock
        // for a deterministic teardown: cancel and close the candidate first (candidate work that
        // has not started is already blocked by the flag), then close the active session and the
        // root resources. Idempotent: a repeated close only sets the flag and cleans nothing
        // else, and does not throw.
        @Override
        public void close () {
            lifecycleGate.requestClose();
            interruptCandidateBestEffort();
            synchronized (this) {
                if (lifecycleGate.isClosed()) return;
                ScriptLifecycleGate.Decision decision =
                        lifecycleGate.tryEnter(ScriptLifecycleGate.Operation.CLOSE);
                if (decision != ScriptLifecycleGate.Decision.EXECUTED) {
                    // A nested close from the same thread inside a lifecycle body: the flag is
                    // already set (the in-flight operation will fail at its cancellation point), so
                    // teardown is not inlined here. The caller can close again once that operation
                    // ends.
                    com.tkisor.nekojs.script.ScriptTypeEnv.logger(scriptType).warn(
                            "{} close deferred: another lifecycle operation is running on this thread; "
                                    + "it will fail at its cancellation point", scriptType.name);
                    return;
                }
                try {
                    releaseAll(() -> discardCandidate(this.candidateEnvironment), this::fullReloadCleanup,
                            this::closeBindings, () -> closeRuntimeResources(this.runtime));
                } finally {
                    this.runtime = RuntimeEnvironment.EMPTY;
                    lifecycleGate.markClosed();
                    lifecycleGate.exit(ScriptLifecycleGate.Operation.CLOSE);
                }
            }
        }

        /**
         * Tries to interrupt the synchronous evaluation of the in-flight candidate to speed up
         * close preemption. The interrupt only makes the evaluation throw; the actual discard and
         * failure attribution still happen on the owner thread's reload path. A failed interrupt
         * (no in-flight candidate, Context already closed) is ignored silently; correctness rests
         * on the pre-commit check and the cancellation points between scripts.
         */
        private void interruptCandidateBestEffort () {
            Context candidate = this.candidateContext;
            if (candidate == null) {
                return;
            }
            try {
                candidate.interrupt(Duration.ofSeconds(5));
            } catch (Throwable ignored) {
            }
        }

        // ---- Queries ----

        public boolean hasScripts () {
            return scripts != null && !scripts.isEmpty();
        }

        private ScriptEventRegistrar getScriptEventRegistrar () {
            return scriptEventBridge.scriptEventRegistrar();
        }

        public void flushReadyNodeTimers () {
            // Snapshot read: context and nodeRuntime must come from the same paired publication,
            // and the whole block uses one lock. The old code read the two fields separately and
            // locked on whichever context it had just read, so a concurrent reload could flush the
            // new runtime's timers while holding the old Context's lock (or the reverse), which
            // Graal rejects immediately as Multi threaded access.
            RuntimeEnvironment env = this.runtime;
            Context context = env.context();
            NekoNodeRuntime nodeRuntime = env.nodeRuntime();
            if (context != null && nodeRuntime != null) {
                synchronized (context) {
                    nodeRuntime.flushReadyTimers();
                }
            }
        }

        // ---- Context identity management (delegated to ScriptContextRegistry) ----

        /**
         * Returns the script type that owns the given context.
         */
        public static ScriptType getTypeFromContext (Context context){
            return ScriptContextRegistry.scriptTypeOf(context);
        }

        public static String switchCurrentScriptId (Context context, String scriptId){
            return ScriptContextRegistry.switchCurrentScriptId(context, scriptId);
        }

        public static void restoreCurrentScriptId (Context context, String scriptId){
            ScriptContextRegistry.restoreCurrentScriptId(context, scriptId);
        }

        public static String getCurrentScriptId (Context context){
            return ScriptContextRegistry.currentScriptIdOf(context);
        }
    }
