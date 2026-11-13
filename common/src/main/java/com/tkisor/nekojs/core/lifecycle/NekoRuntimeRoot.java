package com.tkisor.nekojs.core.lifecycle;

import com.tkisor.nekojs.api.plugin.IPluginRuntime;
import com.tkisor.nekojs.core.NekoSandboxFactory;
import com.tkisor.nekojs.core.ScriptEventBridge;
import com.tkisor.nekojs.core.NekoCoreContext;
import com.tkisor.nekojs.core.error.ErrorTracker;
import com.tkisor.nekojs.core.error.ScriptDiagnosticRecord;
import com.tkisor.nekojs.core.error.ScriptError;
import com.tkisor.nekojs.core.fs.NekoJSPaths;
import com.tkisor.nekojs.core.module.NekoTrustContext;
import com.tkisor.nekojs.core.module.NekoRuntimeTrustContext;
import com.tkisor.nekojs.script.ScriptEnvironmentFactory;
import com.tkisor.nekojs.script.ScriptManager;
import com.tkisor.nekojs.api.ScriptType;
import com.tkisor.nekojs.script.prop.ScriptPropertyRegistry;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Platform composition root, held by the platform entry point (mod entry).
 *
 * <p>Holds the complete object graph, but exposes lifecycle APIs to the platform by default
 * instead of providing getters for the entire object graph. The public surface area prioritizes
 * imperative lifecycle APIs such as {@link #reload(ScriptType)},
 * {@link #reloadFile(ScriptType, Path)}, {@link #runTests()}, {@link #errors()}, and {@link #close()}.
 *
 * <ul>
 *   <li>Does not provide global {@code get()} or {@code current()} methods.</li>
 *   <li>Should only be held by the platform mod entry and within lifecycle boundaries
 *       like command, reload, or shutdown.</li>
 *   <li>Do not propagate the root to general business classes.</li>
 * </ul>
 *
 * <p>{@link #close()} order: script managers -> event bridge / listeners -> resources.
 */
public final class NekoRuntimeRoot implements AutoCloseable {

    private final NekoCoreContext core;
    private final IPluginRuntime pluginRuntime;
    private final ScriptEventBridge eventBridge;
    private final ScriptPropertyRegistry scriptProperties;
    private final ScriptEnvironmentFactory environmentFactory;
    private final Map<ScriptType, ScriptManager> scriptManagers;
    private final ResourceTracker resources;
    private boolean authoringInitialized;
    private volatile boolean authoringStopping;
    private volatile com.tkisor.nekojs.api.testing.TestRunReport latestTestRunReport;
    private volatile com.tkisor.nekojs.authoring.AuthoringRuntime authoringRuntime;
    private com.tkisor.nekojs.core.ui.BuiltinUiRuntime builtinUi;
    /**
     * Root-owned prepared module cache that owns the module cache and session lifetime. It is the
     * same instance the sandbox factory uses (module host and filesystem) and survives normal
     * reloads, server stops and world switches (partitioned and cleared by {@link ScriptType}). It is
     * completely cleared and released by {@link #closeSilently()}. It holds no static state; new,
     * independent roots and test runners start from an empty state and are invisible to each other.
     */
    private final com.tkisor.nekojs.core.module.NekoModulePipelineCache preparationCache;
    private final NekoTrustContext trustContext;
    private final com.tkisor.nekojs.core.pack.PackActivationCoordinator packs;
    private volatile boolean packClosePending;
    private boolean closeInProgress;
    private final java.util.Set<ScriptType> packTypes = java.util.concurrent.ConcurrentHashMap.newKeySet();
    /**
     * Root-owned managed global and shared state: a private backing store per {@link ScriptType} plus
     * an explicitly shared store (named shared). It survives normal reloads, server stops and world
     * switches and is released by {@link #closeSilently()}; closing a generation only invalidates the
     * guest values of that generation. It holds no static state; new, independent roots and test
     * runners start from an empty state and are invisible to each other.
     */
    private final com.tkisor.nekojs.core.state.GlobalStateStores globalState =
            new com.tkisor.nekojs.core.state.GlobalStateStores();
    /**
     * Root-owned registry of candidate domain collectors. The platform assembles the domain collectors
     * (for example the owner of Item/Block modification) and registers them into the root. The
     * DOMAIN_PLAN phase of a reload transaction consumes them through each {@link ScriptManager} (see
     * {@code CandidateDomainCollector}). Managers share the list by reference, so registrations made
     * after a manager is created are visible too; reads happen only inside the reload owner thread's
     * critical section. When the root closes, it closes each AutoCloseable collector individually, so
     * baseline restoration works and independent test roots do not pollute each other.
     */
    private final java.util.List<com.tkisor.nekojs.core.lifecycle.CandidateDomainCollector> domainCollectors =
            new java.util.concurrent.CopyOnWriteArrayList<>();

    /**
     * Production assembly entry point. It shares the same prepared cache instance with the sandbox
     * factory (see {@code NekoRuntimeAssembly}).
     */
    public NekoRuntimeRoot(
            NekoCoreContext core,
            IPluginRuntime pluginRuntime,
            ScriptEventBridge eventBridge,
            ScriptPropertyRegistry scriptProperties,
            NekoSandboxFactory sandboxFactory,
            com.tkisor.nekojs.core.module.NekoModulePipelineCache preparationCache
    ) {
        this(core, pluginRuntime, eventBridge, scriptProperties, sandboxFactory, preparationCache,
                NekoTrustContext.local());
    }

    public NekoRuntimeRoot(
            NekoCoreContext core,
            IPluginRuntime pluginRuntime,
            ScriptEventBridge eventBridge,
            ScriptPropertyRegistry scriptProperties,
            NekoSandboxFactory sandboxFactory,
            com.tkisor.nekojs.core.module.NekoModulePipelineCache preparationCache,
            NekoTrustContext trustContext
    ) {
        Objects.requireNonNull(sandboxFactory, "sandboxFactory");
        Objects.requireNonNull(preparationCache, "preparationCache");
        if (!preparationCache.isRootOwner()) {
            throw new IllegalArgumentException("NekoRuntimeRoot requires a root preparation cache, not a child session");
        }
        if (!sandboxFactory.usesPreparationCache(preparationCache)) {
            throw new IllegalArgumentException("NekoRuntimeRoot and NekoSandboxFactory must share the same preparation cache");
        }
        this.core = core;
        this.pluginRuntime = pluginRuntime;
        this.mixins = new com.tkisor.nekojs.core.mixin.MixinRuntime(trustContext, getClass().getClassLoader());
        this.eventBridge = mixins.wrap(eventBridge);
        this.scriptProperties = scriptProperties;
        this.environmentFactory = new ScriptEnvironmentFactory(this.eventBridge, pluginRuntime, sandboxFactory, globalState);
        this.scriptManagers = new EnumMap<>(ScriptType.class);
        this.resources = new ResourceTracker();
        this.preparationCache = preparationCache;
        this.trustContext = trustContext;
        this.packs = new com.tkisor.nekojs.core.pack.PackActivationCoordinator(this::createPackManager,
                this::scriptManagerOf, type -> com.tkisor.nekojs.core.ScriptLocator.discover(type, scriptProperties),
                this.eventBridge, pluginRuntime, domainCollectors);
    }

    private final com.tkisor.nekojs.core.mixin.MixinRuntime mixins;

    public com.tkisor.nekojs.core.mixin.MixinRuntime mixins() { return mixins; }

    /** Starts optional authoring access once; ordinary script reloads retain the same root-owned service. */
    public synchronized void initializeAuthoring() {
        if (authoringInitialized) return;
        authoringInitialized = true;
        try {
            var runtime = com.tkisor.nekojs.authoring.AuthoringRuntime.open(
                    com.tkisor.nekojs.core.fs.NekoJSPaths.get(), com.tkisor.nekojs.probe.ProbeCoordinator.config());
            runtime.installDiagnostics(() -> errors().records(), this::latestTestRunReport);
            authoringRuntime = runtime;
            resources.track("authoring", runtime);
        } catch (java.io.IOException | RuntimeException | LinkageError failure) {
            com.tkisor.nekojs.NekoJS.LOGGER.warn("[NEKO-1019] Authoring initialization failed; game startup continues", failure);
        }
    }

    /** Opens bundled diagnostic UI without joining a user CLIENT reload transaction. */
    public synchronized com.tkisor.nekojs.core.ui.BuiltinUiRuntime.Session openBuiltinUiSession(
            java.util.function.Consumer<Runnable> ownerQueue) {
        if (authoringStopping) throw new IllegalStateException("[NEKO-7015] Runtime shutdown rejected built-in UI creation");
        if (builtinUi == null) {
            builtinUi = resources.track("builtin-ui", environmentFactory.createBuiltinUiRuntime());
        }
        return builtinUi.open(ownerQueue);
    }

    /** Binds the platform live adapter only when optional authoring initialization succeeded. */
    public void installAuthoringControlHost(com.tkisor.nekojs.api.authoring.AuthoringControlHost host) {
        var runtime = authoringRuntime;
        if (runtime != null && !authoringStopping) runtime.installHost(host);
    }

    /** Invalidates owner-thread callbacks before script teardown starts. */
    public boolean isAuthoringControlActive() { return !authoringStopping; }

    /** Prepared cache for this root, for lifecycle diagnosis within the same package; reload/invalidation goes through each manager entry point. */
    com.tkisor.nekojs.core.module.NekoModulePipelineCache preparationCache() {
        return preparationCache;
    }

    int preparedModuleCountForDiagnostics() {
        return preparationCache.preparedEntryCount();
    }

    /** Pack activation owner injects verified remote sources and their protected cache root. */
    public void authorizeRemoteSources(java.util.Collection<NekoTrustContext.RemoteSource> sources,
                                       java.nio.file.Path remoteRoot) {
        if (!(trustContext instanceof NekoRuntimeTrustContext runtimeTrust)) {
            throw new IllegalStateException("Runtime was assembled without a mutable trust context");
        }
        runtimeTrust.authorizeRemoteSources(sources, remoteRoot);
    }

    /** Connection teardown revokes credentials while retaining protection for the cache root. */
    public void revokeRemoteSources(java.nio.file.Path remoteRoot) {
        try {
            packs.sourcesRevoked(com.tkisor.nekojs.core.pack.ScriptPackScope.SERVER_CACHE);
            globalState.releasePackScope("SERVER_CACHE");
            if (trustContext instanceof NekoRuntimeTrustContext runtimeTrust) {
                runtimeTrust.revokeRemoteSources(remoteRoot);
            }
        } finally { if (packClosePending && !packs.isOwnerOperation()) closeSilently(); }
    }

    /**
     * Managed global and shared state for this root. Java-side code can write through it and tests
     * can observe it.
     */
    public com.tkisor.nekojs.core.state.GlobalStateStores globalState() {
        return globalState;
    }

    /**
     * Registers a candidate domain collector. Call this once during platform assembly. The collector
     * holds domain-specific state, such as Item/Block modification baselines.
     * The root owns it and groups it by lifecycle; it is not a new runtime owner and does not add a
     * public Modification Runtime. Registering the same collector twice throws.
     */
    public void registerDomainCollector(com.tkisor.nekojs.core.lifecycle.CandidateDomainCollector collector) {
        if (collector == null) throw new NullPointerException("collector");
        if (domainCollectors.contains(collector)) {
            throw new IllegalStateException("domain collector '" + collector.domain() + "' already registered");
        }
        domainCollectors.add(collector);
    }

    /** Registered candidate domain collectors, as a read-only view for diagnosis and tests. */
    public java.util.List<com.tkisor.nekojs.core.lifecycle.CandidateDomainCollector> domainCollectors() {
        return java.util.List.copyOf(domainCollectors);
    }

    /** Find a collector by domain identifier (platform-side entry point: server startup collection, etc.; returns null if not found). */
    public com.tkisor.nekojs.core.lifecycle.CandidateDomainCollector domainCollector(String domain) {
        for (var collector : domainCollectors) {
            if (collector.domain().equals(domain)) return collector;
        }
        return null;
    }

    public ScriptManager scriptManagerOf(ScriptType type) {
        ScriptManager manager = scriptManagers.get(type);
        if (manager == null) {
            throw new IllegalStateException("No ScriptManager registered for " + type);
        }
        return manager;
    }

    public ScriptManager scriptManagerOrNull(ScriptType type) {
        return scriptManagers.get(type);
    }

    /** Reports type availability without creating a manager or exposing its implementation. */
    public boolean hasScriptType(ScriptType type) {
        return scriptManagers.containsKey(type);
    }

    /** Completed active entry outcomes; querying an absent type does not initialize it. */
    public com.tkisor.nekojs.core.pack.PackRuntimeView packRuntimeView(ScriptType type) {
        ScriptManager manager = scriptManagers.get(type);
        var previous = manager == null ? new com.tkisor.nekojs.core.pack.PackRuntimeView(type, 0, false, List.of())
                : manager.packRuntimeView();
        List<com.tkisor.nekojs.core.pack.PackScriptView> entries = new ArrayList<>(previous.entries());
        entries.addAll(packs.entryViews(type));
        return new com.tkisor.nekojs.core.pack.PackRuntimeView(type, previous.generation(), previous.activeFailed(), entries);
    }

    /** Plans the current sources without evaluating entries or altering the active generation. */
    public com.tkisor.nekojs.core.pack.PackExecutionPlan packExecutionPlan(ScriptType type) {
        return scriptManagerOf(type).packExecutionPlan();
    }

    public boolean isActiveFailed(ScriptType type) {
        ScriptManager manager = scriptManagers.get(type);
        return manager != null && manager.isActiveFailed();
    }

    public ScriptManager createScriptManager(ScriptType type) {
        ScriptManager[] owner = new ScriptManager[1];
        ScriptEventBridge partition = new ScriptEventBridge() {
            public boolean requiresTransactionalLoad(ScriptType scriptType) { return eventBridge.requiresTransactionalLoad(scriptType); }
            public void bindEvents(graal.graalvm.polyglot.Value bindings, ScriptType scriptType) { eventBridge.bindEvents(bindings, scriptType); }
            public void clearListeners(ScriptType scriptType) {
                if (!packTypes.contains(scriptType)) { eventBridge.clearListeners(scriptType); return; }
                var context = owner[0] == null ? null : owner[0].activeContextForPackOwner();
                if (context != null) eventBridge.preparePackListeners(scriptType, List.of(), java.util.Set.of(context)).finish();
            }
            public ListenerBatch prepareCandidateListeners(ScriptType scriptType,
                    List<com.tkisor.nekojs.api.event.EventBusJS.PendingListener> pending) {
                if (!packTypes.contains(scriptType)) return eventBridge.prepareCandidateListeners(scriptType, pending);
                var context = owner[0].activeContextForPackOwner();
                return eventBridge.preparePackListeners(scriptType, pending, context == null ? java.util.Set.of() : java.util.Set.of(context));
            }
            public void clearListeners(ScriptType scriptType, String scriptId) { eventBridge.clearListeners(scriptType, scriptId); }
            public void clearListenersByPrefix(ScriptType scriptType, String prefix) { eventBridge.clearListenersByPrefix(scriptType, prefix); }
            public com.tkisor.nekojs.api.event.ScriptEventRegistrar scriptEventRegistrar() { return eventBridge.scriptEventRegistrar(); }
        };
        ScriptManager manager = new ScriptManager(type, partition, pluginRuntime, scriptProperties, core.errorTracker(), NekoJSPaths.get(), core.sandboxConfig(), environmentFactory, domainCollectors, preparationCache);
        owner[0] = manager;
        manager.configurePackPartition(null, () -> {
            com.tkisor.nekojs.core.pack.ScriptPackRegistry.get().refreshLocalPacks();
            return com.tkisor.nekojs.core.ScriptLocator.discover(type, scriptProperties).stream()
                    .filter(script -> script.pack == null || script.pack.manifest().descriptor() == null).toList();
        });
        scriptManagers.put(type, manager);
        return manager;
    }

    private ScriptManager createPackManager(ScriptType type, com.tkisor.nekojs.core.pack.ScriptPack pack) {
        ScriptManager[] owner = new ScriptManager[1];
        ScriptEventBridge scoped = new ScriptEventBridge() {
            public void bindEvents(graal.graalvm.polyglot.Value bindings, ScriptType scriptType) {
                eventBridge.bindEvents(bindings, scriptType);
            }
            public void clearListeners(ScriptType scriptType) {
                var context = owner[0] == null ? null : owner[0].activeContextForPackOwner();
                if (context != null) eventBridge.preparePackListeners(scriptType, List.of(), java.util.Set.of(context)).finish();
            }
            public void clearListeners(ScriptType scriptType, String scriptId) { eventBridge.clearListeners(scriptType, scriptId); }
            public void clearListenersByPrefix(ScriptType scriptType, String prefix) { eventBridge.clearListenersByPrefix(scriptType, prefix); }
        };
        ScriptManager manager = new ScriptManager(type, scoped, pluginRuntime, scriptProperties,
                new com.tkisor.nekojs.core.error.DefaultErrorTracker(NekoJSPaths.get(), core.sandboxConfig()),
                NekoJSPaths.get(), core.sandboxConfig(), environmentFactory, List.of(), preparationCache);
        owner[0] = manager;
        manager.restrictPackModules(pack.root());
        manager.configurePackPartition(com.tkisor.nekojs.core.pack.ScriptPackRegistry.get().instanceKey(pack),
                () -> com.tkisor.nekojs.core.ScriptLocator.discover(type, scriptProperties).stream()
                        .filter(script -> script.pack != null && script.pack.selector().equals(pack.selector())).toList());
        return manager;
    }

    /** Initial platform evaluation follows the same source owner as reload. */
    public void load(ScriptType type) {
        try {
            if (packs.handles(type)) {
                var result = reloadPack(type, null);
                if (!result.success()) throw new IllegalStateException("[NEKO-1017] Package loading failed: " + result.failure());
                if (type == ScriptType.STARTUP) {
                    flushReadyNodeTimers(type);
                    com.tkisor.nekojs.api.event.ScriptEvents.post(eventBridge.scriptEventRegistrar());
                }
            } else scriptManagerOf(type).loadScripts();
        } finally { if (type == ScriptType.STARTUP) packs.sealStartup(); }
    }

    public com.tkisor.nekojs.core.pack.PackActivationCoordinator.Result reloadPack(ScriptType type, String selector) {
        packTypes.add(type);
        try { return packs.reload(type, selector); }
        finally { if (packClosePending) closeSilently(); }
    }

    public List<com.tkisor.nekojs.core.pack.PackActivationCoordinator.View> packActivations(ScriptType type) {
        return packs.views(type);
    }

    /** Author operations share source selection and never rewrite protected cache content. */
    public com.tkisor.nekojs.core.pack.PackToolResult packTool(String action, String selector,
            ScriptType type, String loader, String minecraft) {
        try {
            if (globalState.isClosed()) throw new IllegalStateException("[NEKO-1018] Package author operation rejected: runtime is closed");
            var registry = com.tkisor.nekojs.core.pack.ScriptPackRegistry.get(); registry.refreshLocalPacks();
            if (action.equals("create")) {
                Path created = com.tkisor.nekojs.core.pack.tool.PackTools.create(NekoJSPaths.get().packsRoot(), selector, List.of(type));
                registry.refreshLocalPacks();
                return new com.tkisor.nekojs.core.pack.PackToolResult(true, "GLOBAL:" + selector, created, "created");
            }
            var matches = registry.allPacks().stream().filter(pack -> pack.selector().equalsIgnoreCase(selector)
                    || !selector.contains(":") && pack.id().equals(selector)).toList();
            if (matches.size() != 1) return new com.tkisor.nekojs.core.pack.PackToolResult(false, selector, null, "source selector is missing or ambiguous");
            var pack = matches.getFirst();
            var content = com.tkisor.nekojs.core.pack.tool.PackTools.validate(pack.root());
            if (action.equals("validate")) return new com.tkisor.nekojs.core.pack.PackToolResult(true, pack.selector(), null, content.hash());
            Path exports = NekoJSPaths.get().verifyInsideNekoRootForCreate(NekoJSPaths.get().root().resolve("exports"));
            String name = pack.id() + "-" + pack.version();
            Path output = switch (action) {
                case "archive" -> com.tkisor.nekojs.core.pack.tool.PackTools.archive(pack.root(), exports.resolve(name + ".nekopack"));
                case "jar" -> com.tkisor.nekojs.core.pack.tool.PackTools.modJar(pack.root(),
                        exports.resolve(name + "-" + loader + "-" + minecraft + ".jar"), loader, minecraft);
                default -> throw new IllegalArgumentException("[NEKO-1018] Unknown author operation: " + action);
            };
            return new com.tkisor.nekojs.core.pack.PackToolResult(true, pack.selector(), output, "written");
        } catch (Exception failure) {
            return new com.tkisor.nekojs.core.pack.PackToolResult(false, selector, null, failure.getMessage());
        }
    }

    public void flushReadyNodeTimers(ScriptType type) {
        try {
            scriptManagerOf(type).flushReadyNodeTimers();
            packs.flushTimers(type);
        } finally { if (packClosePending && !packs.isOwnerOperation()) closeSilently(); }
    }

    public void clearWorldPackListeners(ScriptType type, List<com.tkisor.nekojs.core.pack.ScriptPack> removed) {
        try {
            packs.sourcesRevoked(com.tkisor.nekojs.core.pack.ScriptPackScope.WORLD);
            globalState.releasePackScope("WORLD");
            ScriptManager manager = scriptManagers.get(type);
            if (manager != null) manager.clearWorldPackListeners(removed);
        } finally { if (packClosePending && !packs.isOwnerOperation()) closeSilently(); }
    }

    /**
     * Reloads everything as a transaction. It switches through a single commit point only after every
     * stage of the candidate generation has passed.
     *
     * <p>On failure it throws {@link NekoReloadException}, and {@link NekoReloadException#report()}
     * carries a structured result: generation, phase, source location, owner and domain. The candidate
     * generation is closed on failure and the active generation stays available.
     */
    public ReloadResult reload(ScriptType type) {
        ScriptManager manager = scriptManagerOf(type);
        if (packs.handles(type)) {
            var result = reloadPack(type, null);
            if (!result.success()) throw new NekoReloadException(result.report() != null ? result.report()
                    : new ReloadFailureReport(type, manager.generationId(), ReloadPhase.PREPARATION, null,
                            "PackActivation[" + type.name + "]", "package-operation", new IllegalStateException(result.failure())));
            return type == ScriptType.STARTUP ? ReloadResult.successNonTransactional(type, manager.generationId(), ReloadPhase.STARTUP)
                    : ReloadResult.success(type, manager.generationId());
        }
        manager.reloadScripts();
        // The STARTUP reload path uses reset+load and is not transactional. Domain adapters have not proven
        // that the platform registrations it makes can be rolled back, so this is an explicit
        // restart/unsupported case (see the STARTUP branch warning in ScriptManager#reloadScripts). Results
        // mark it explicitly and do not claim candidate/commit transaction success.
        return type == ScriptType.STARTUP
                ? ReloadResult.successNonTransactional(type, manager.generationId(), ReloadPhase.STARTUP)
                : ReloadResult.success(type, manager.generationId());
    }

    public ReloadResult reloadFile(ScriptType type, Path file) {
        Objects.requireNonNull(file, "file");
        var source = com.tkisor.nekojs.core.pack.ScriptPackRegistry.get().allPacks().stream()
                .filter(pack -> pack.manifest().descriptor() != null && com.tkisor.nekojs.core.fs.NekoJSPaths.isInside(file, pack.root()))
                .findFirst();
        if (source.isPresent()) return ReloadResult.failure(type, 0, ReloadPhase.FILE, file.toString(),
                new IllegalStateException("[NEKO-1017] Versioned package files require package reload: " + source.get().selector()));
        ScriptManager manager = scriptManagers.get(type);
        if (manager == null) {
            return ReloadResult.failure(type, 0, ReloadPhase.PREPARATION, file.toString(),
                    new IllegalStateException("No ScriptManager registered for " + type));
        }
        // Only a full candidate reload may recover an isolated, terminated active runtime.
        if (manager.isActiveFailed()) {
            return ReloadResult.failure(type, manager.generationId(), ReloadPhase.PREPARATION, file.toString(),
                    null);
        }
        try {
            manager.reloadScriptFile(file.toString());
            if (type == ScriptType.TEST) {
                manager.flushReadyNodeTimers();
            }
            // A kill during this reload's evaluation does not propagate as an exception
            // (ScriptExecutor records it in the error panel), but it isolates the active
            // generation. The entry check above proved the active was healthy when the
            // reload started, so a failed active here means THIS reload killed it — the
            // result must report failure, not a completed reload over an isolated runtime.
            if (manager.isActiveFailed()) {
                return ReloadResult.failure(type, manager.generationId(), ReloadPhase.FILE, file.toString(),
                        new IllegalStateException(
                                "script evaluation was terminated and the active generation is isolated"));
            }
            if (type == ScriptType.STARTUP) {
                return new ReloadResult(type, true, null, manager.generationId(), ReloadPhase.STARTUP, file.toString());
            }
            return ReloadResult.successFile(type, manager.generationId(), file.toString());
        } catch (Exception e) {
            // Single-file reload uses the active environment rather than a candidate generation.
            return ReloadResult.failure(type, manager.generationId(), ReloadPhase.FILE, file.toString(), e);
        }
    }

    /** Latest finished TEST attempt, including a failed candidate; never reads a guest Context. */
    public com.tkisor.nekojs.api.testing.TestRunReport latestTestRunReport() {
        return latestTestRunReport;
    }

    public synchronized TestRunResult runTests() {
        boolean packageRun = packs.handles(ScriptType.TEST);
        ScriptManager testManager = scriptManagers.get(ScriptType.TEST);
        if (!packageRun && testManager == null) return TestRunResult.notConfigured();
        // The plugin binding is a host object shared across generations. Hold it before
        // execution so failed candidate cleanup cannot invalidate counter observation.
        com.tkisor.nekojs.bindings.static_access.TestJS assertions = testAssertions();
        int passedBefore = assertions == null ? 0 : assertions.passed();
        int failedBefore = assertions == null ? 0 : assertions.failed();
        boolean executionSucceeded = false;
        try {
            if (packageRun) {
                scriptManagerOf(ScriptType.TEST);
                reload(ScriptType.TEST);
                flushReadyNodeTimers(ScriptType.TEST);
            } else {
                testManager.runTestScripts();
            }
            executionSucceeded = true;
        } finally {
            latestTestRunReport = new com.tkisor.nekojs.api.testing.TestRunReport(true, executionSucceeded,
                    assertions == null ? 0 : assertions.passed() - passedBefore,
                    assertions == null ? 0 : assertions.failed() - failedBefore);
        }
        return new TestRunResult(true, true, latestTestRunReport);
    }

    private com.tkisor.nekojs.bindings.static_access.TestJS testAssertions() {
        var binding = pluginRuntime.bindings(ScriptType.TEST).get("Test");
        Object value = binding == null ? null : binding.value();
        return value instanceof com.tkisor.nekojs.bindings.static_access.TestJS assertions ? assertions : null;
    }

    public ErrorSnapshot errors() {
        List<ScriptError> errors = new ArrayList<>(core.errorTracker().getAllErrors());
        errors.addAll(packs.errors());
        return new ErrorSnapshot(errors, errors.size());
    }

    public ErrorTracker errorTracker() {
        return core.errorTracker();
    }

    @Override
    public void close() {
        closeSilently();
    }

    public void closeSilently() {
        authoringStopping = true;
        var authoring = authoringRuntime;
        if (authoring != null) authoring.stopControls();
        packs.requestClose();
        if (closeInProgress) return;
        if (ScriptLifecycleGate.isInCallback()) {
            if (!packClosePending) ScriptLifecycleGate.afterCallback(this::closeSilently);
            packClosePending = true;
            return;
        }
        if (packs.isOwnerOperation()) { packClosePending = true; return; }
        packClosePending = false;
        closeInProgress = true;
        try {
            Throwable first = null;
            try { mixins.close(); } catch (Throwable failure) { first = failure; }
            try { packs.close(); } catch (Throwable failure) {
                if (first == null) first = failure; else first.addSuppressed(failure);
            }
            for (ScriptType type : scriptManagers.keySet()) {
                ScriptManager manager = scriptManagers.get(type);
                try {
                    flushAndCloseManager(type, manager);
                } catch (Throwable t) {
                    if (first == null) first = t;
                    else first.addSuppressed(t);
                }
            }
            scriptManagers.clear();
            for (ScriptType type : ScriptType.values()) {
                try {
                    eventBridge.clearListeners(type);
                } catch (Throwable t) {
                    if (first == null) first = t;
                    else first.addSuppressed(t);
                }
            }
            try {
                resources.close();
            } catch (Throwable t) {
                if (first == null) first = t;
                else first.addSuppressed(t);
            }
            // The final root close releases the prepared module cache. Server stop, world switch and reload
            // leave it alone; each type's cleanup goes through its manager's fullReloadCleanup instead, so this
            // call releases every entry the owner holds.
            try {
                preparationCache.close();
            } catch (Throwable t) {
                if (first == null) first = t;
                else first.addSuppressed(t);
            }
            // The final root close releases the global/shared state domain. Server stop and world switch leave
            // it alone.
            try {
                globalState.closeAll();
            } catch (Throwable t) {
                if (first == null) first = t;
                else first.addSuppressed(t);
            }
            // Close each root-owned candidate domain collector separately. This restores Item/Block
            // modification baselines, so independent test roots each start clean and do not pollute one another.
            for (com.tkisor.nekojs.core.lifecycle.CandidateDomainCollector collector : domainCollectors) {
                if (!(collector instanceof AutoCloseable closeable)) continue;
                try {
                    closeable.close();
                } catch (Throwable t) {
                    if (first == null) first = t;
                    else first.addSuppressed(t);
                }
            }
            domainCollectors.clear();
            if (first != null) {
                if (first instanceof Error e) throw e;
                if (first instanceof RuntimeException re) throw re;
                throw new RuntimeException("[NEKO-1017] Runtime root teardown failed after releasing remaining resources", first);
            }
        } finally {
            closeInProgress = false;
        }
    }

    private void flushAndCloseManager(ScriptType type, ScriptManager manager) {
        try {
            manager.flushReadyNodeTimers();
        } catch (Throwable ignored) {
            com.tkisor.nekojs.NekoJS.LOGGER.warn("[NEKO-1017] Pending timer flush failed before runtime teardown; type={}", type.name(), ignored);
        }
        manager.close();
    }

    /**
     * Result of a reload.
     *
     * @param type           Script type that was reloaded
     * @param success        Whether the reload succeeded
     * @param error          Why it failed; null on success. When thrown through {@link NekoReloadException},
     *                       this field matches report.error()
     * @param generation     Generation sequence number after success, or the sequence number of the failed
     *                       candidate
     * @param phase          Stage the result came from: COMMIT for success; STARTUP for the
     *                       non-transactional startup reload, which needs an explicit restart; FILE for a
     *                       single-file reload
     * @param sourceLocation Location of the failed script ({@code server/foo.js} style); null for
     *                       stage-level failures and for success. Non-candidate paths such as
     *                       {@link #reloadFile} used to compute this location and drop it, so their
     *                       failures did not carry a source location
     */
    public record ReloadResult(ScriptType type, boolean success, Throwable error, long generation,
                               ReloadPhase phase, String sourceLocation) {
        public static ReloadResult success(ScriptType type, long generation) {
            return new ReloadResult(type, true, null, generation, ReloadPhase.COMMIT, null);
        }

        public static ReloadResult successFile(ScriptType type, long generation, String sourceLocation) {
            return new ReloadResult(type, true, null, generation, ReloadPhase.FILE, sourceLocation);
        }

        /**
         * Returns whether the result comes from a non-transactional path: reset+load for
         * {@link ReloadPhase#STARTUP}, single-file reload for {@link ReloadPhase#FILE}. Those paths do
         * <strong>not</strong> claim candidate + commit transaction success.
         *
         * <p>Domain adapters have not proven that the irreversible STARTUP platform registrations
         * (items/blocks/entities) can be rolled back, so a caller of this path must explicitly request a
         * loader restart to get a clean STARTUP state. The predicate gives external callers an explicit
         * decision point, so they do not mistake a non-transactional path for a committed transaction when
         * they only read {@link #success()}.
         */
        public boolean nonTransactional() {
            return phase == ReloadPhase.STARTUP || phase == ReloadPhase.FILE;
        }

        /** STARTUP non-transactional reload: caller should explicitly request a loader restart (irreversible platform registrations do not rollback). */
        public boolean requiresLoaderRestart() {
            return phase == ReloadPhase.STARTUP;
        }

        /** Explicit non-transactional result for STARTUP reset+load reload (does not claim candidate/commit transaction success). */
        public static ReloadResult successNonTransactional(ScriptType type, long generation, ReloadPhase phase) {
            return new ReloadResult(type, true, null, generation, phase, null);
        }

        /**
         * Failure result for non-candidate paths ({@link ReloadPhase#FILE}), carries the failed script location.
         *
         * <p>The original {@code success(type)} / {@code failure(type, error)} overloads have been removed
         * (Review A2): the former returned a contradictory {@code phase=COMMIT} + {@code generation=-1},
         * and neither had any callers, so they were dead code.
         */
        public static ReloadResult failure(ScriptType type, long generation, ReloadPhase phase,
                                           String sourceLocation, Throwable error) {
            return new ReloadResult(type, false, error, generation, phase, sourceLocation);
        }
    }

    public record TestRunResult(boolean isConfigured, boolean isCompleted,
                                com.tkisor.nekojs.api.testing.TestRunReport testRunReport) {
        public TestRunResult(boolean isConfigured, boolean isCompleted) {
            this(isConfigured, isCompleted, null);
        }

        public static TestRunResult completed() {
            return new TestRunResult(true, true);
        }

        public static TestRunResult notConfigured() {
            return new TestRunResult(false, false);
        }
    }

    public record ErrorSnapshot(Collection<ScriptError> errors, int count) {
        public ErrorSnapshot {
            errors = List.copyOf(errors == null ? List.of() : errors);
            count = errors.size();
        }

        public static ErrorSnapshot of(ErrorTracker tracker) {
            Collection<ScriptError> all = tracker.getAllErrors();
            return new ErrorSnapshot(all, all.size());
        }

        /**
         * Frozen diagnostic records for this snapshot, each delegating to the single frozen record of its
         * error. Reads only the public error set already recorded and creates no second source of facts.
         */
        public List<ScriptDiagnosticRecord> records() {
            return errors.stream().map(ScriptError::diagnostic).toList();
        }
    }
}
