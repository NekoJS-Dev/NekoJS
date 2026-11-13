package com.tkisor.nekojs.probe;

import com.tkisor.nekojs.NekoJS;
import com.tkisor.nekojs.authoring.AuthoringPaths;

import com.tkisor.nekojs.api.catalog.NekoScriptCatalog;
import com.tkisor.nekojs.api.catalog.NekoScriptCatalogSnapshot;
import com.tkisor.nekojs.api.plugin.NekoRuntimeAccess;
import com.tkisor.nekojs.core.fs.NekoJSPaths;
import com.tkisor.nekojs.api.surface.ApiTypeRef;
import com.tkisor.nekojs.probe.events.ProbeEvents;
import com.tkisor.nekojs.probe.events.ProbeOverrides;
import com.tkisor.nekojs.probe.ir.TypeDecl;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Probe coordinator: Orchestrates the **workflow** of a probe run—configuration → shared collection → shared IR → override events →
 * external side effects → per-backend rendering (serial) → diagnostic summary. Specific responsibilities are handled by package-internal components:
 * <ul>
 *   <li>{@link ProbeConfigService}: probe.toml reading/caching (mtime + size timestamp) / enabled persistence</li>
 *   <li>{@link ProbeClassCollector}: Seed BFS class collection (stateless)</li>
 *   <li>{@link ProbeIrBuilder}: TypeReflector reflection + assign/modify events (stateless)</li>
 *   <li>{@link ProbeOutputCommitter}: staging/backup directory exchange (called individually by backends)</li>
 * </ul>
 *
 * <p>Supports instantiation against custom {@link NekoJSPaths} since C3 (passing paths and {@link ProbeExternalArtifacts} to the constructor),
 * facilitating isolated testing using temporary directories. Instance methods include {@link #readConfig()}/{@link #reloadConfigCache()}/
 * {@link #applyEnabled(boolean)}/{@link #isProbeEnabled()}/{@link #runProbe()}.
 * Java does not allow static and instance methods with the same signature to coexist, so the static compatibility layer retains the
 * same signatures as older versions: {@code config()/reloadConfig()/setEnabled(boolean)/isEnabled()/run(snapshot, backends)}.
 * These delegate to the lazy singleton {@code defaultCoordinator()}; all existing static call sites (command layer and tests) remain compilable and runnable without changes.
 */
public final class ProbeCoordinator {

    /* ================= Instance State ================= */
    private final NekoJSPaths paths;
    private final ProbeExternalArtifacts externalArtifacts;
    private final ProbeConfigService configService;
    /**
     * runProbe mutex: Only one run is allowed at a time for the same coordinator instance (fail-fast: subsequent calls during
     * occupancy return a failure result immediately instead of queuing). Besides output directory writing, backend instances
     * (registry singletons) carry mutable rendering caches that are cleared every round; concurrent runs would corrupt each other.
     * In user command scenarios, "prompting immediately that it is already running" is preferred over silent queuing.
     */
    private final AtomicBoolean running = new AtomicBoolean();

    /** Constructs with the given game directory path; external side effects use {@link ProbeExternalArtifacts#DEFAULT}. */
    public ProbeCoordinator(NekoJSPaths paths) {
        this(paths, ProbeExternalArtifacts.DEFAULT);
    }

    /** Constructs with the given game directory path and external side effect implementation (use {@link ProbeExternalArtifacts#NONE} in tests to isolate disk writes). */
    public ProbeCoordinator(NekoJSPaths paths, ProbeExternalArtifacts artifacts) {
        this.paths = Objects.requireNonNull(paths, "paths");
        this.externalArtifacts = Objects.requireNonNull(artifacts, "artifacts");
        this.configService = new ProbeConfigService(paths);
    }

    /** Reads probe configuration (delegates to {@link ProbeConfigService}: automatic re-read based on mtime+size timestamp caching). */
    public ProbeConfig readConfig() {
        return configService.readConfig();
    }

    /** Discards the configuration cache, forcing the next {@link #readConfig()} to re-read from disk (used by {@code /nekojs probe reload} for forced refresh). */
    public void reloadConfigCache() {
        configService.reloadConfigCache();
    }

    /** Writes the {@code enabled} status to probe.toml and reloads the cache (used by {@code /nekojs probe enable|disable} commands). */
    public void applyEnabled(boolean enabled) {
        configService.applyEnabled(enabled);
    }

    /** Returns the current probe master switch ({@code == readConfig().enabled()}). */
    public boolean isProbeEnabled() {
        return configService.isProbeEnabled();
    }

    /* ================= Static Compatibility Layer (Old static call sites remain unchanged) ================= */
    private static volatile ProbeCoordinator DEFAULT;
    private static final Object AUTHORING_PATHS_LOCK = new Object();
    private static AuthoringPathsRegistration authoringPathsRegistration;

    /** Binds one runtime root; an old registration cannot unbind a newer root. */
    public static AutoCloseable bindAuthoringPaths(AuthoringPaths paths) {
        AuthoringPathsRegistration registration = new AuthoringPathsRegistration(Objects.requireNonNull(paths, "paths"));
        synchronized (AUTHORING_PATHS_LOCK) {
            authoringPathsRegistration = registration;
        }
        return () -> {
            synchronized (AUTHORING_PATHS_LOCK) {
                if (authoringPathsRegistration == registration) authoringPathsRegistration = null;
            }
        };
    }

    private record AuthoringPathsRegistration(AuthoringPaths paths) {}

    private AuthoringPaths captureAuthoringPaths() throws IOException {
        synchronized (AUTHORING_PATHS_LOCK) {
            if (authoringPathsRegistration != null
                    && authoringPathsRegistration.paths().gameDirectory().equals(paths.gameDir().toRealPath())) {
                return authoringPathsRegistration.paths();
            }
        }
        return AuthoringPaths.resolve(paths.gameDir(), AuthoringPaths.DEFAULT_DIRECTORY);
    }

    /** Lazily creates the default coordinator bound to the global {@link NekoJSPaths#get()}. */
    private static ProbeCoordinator defaultCoordinator() {
        ProbeCoordinator d = DEFAULT;
        if (d == null) {
            d = new ProbeCoordinator(NekoJSPaths.get());
            DEFAULT = d;
        }
        return d;
    }

    /** Static facade: Delegates to the global default coordinator to read configuration. */
    public static ProbeConfig config() {
        return defaultCoordinator().readConfig();
    }

    /**
     * Ensures {@code <gamedir>/nekojs/config/probe.toml} exists: Automatically creates it with default values if missing
     * (using the autosave semantics of {@link ProbeConfigLoader}, symmetric to the startup generation of {@code engine.toml}).
     * Called by {@code WorkspaceGenerator.setupWorkspace()} at startup; otherwise, users would only see engine.toml in
     * {@code nekojs/config/} initially, with probe.toml appearing only after the first probe command runs.
     * Reading failures (e.g., file corruption) are handled by the loader, which falls back to default configuration; no exception is thrown here.
     */
    public static void ensureConfigFile() {
        defaultCoordinator().readConfig();
    }

    /** Static facade: Delegates to the global default coordinator to discard configuration cache. */
    public static void reloadConfig() {
        defaultCoordinator().reloadConfigCache();
    }

    /** Static facade: Delegates to the global default coordinator to persist {@code enabled} and reload. */
    public static void setEnabled(boolean enabled) {
        defaultCoordinator().applyEnabled(enabled);
    }

    /** Static facade: Delegates to the global default coordinator to reset editor configuration ({@code /nekojs probe reset_config}). */
    public static int resetEditorConfigs() {
        return defaultCoordinator().resetEditorConfigsInternal();
    }

    /**
     * Resets all editor configuration files managed by backends: Deletes files owned by each backend individually
     * (jsconfig/pyrightconfig/snippets, without touching shared {@code .vscode/settings.json}),
     * followed by {@code WorkspaceGenerator.createWorkspaceConfigs()} rebuilding the base factory configuration.
     * Configuration fragments (paths/include, etc.) are merged again by subsequent probe runs.
     *
     * @return Number of backends that successfully performed the reset
     */
    int resetEditorConfigsInternal() {
        int reset = 0;
        for (ProbeBackend backend : ProbeBackendRegistry.get().allBackends()) {
            try {
                backend.resetEditorConfig(paths);
                reset++;
            } catch (Exception e) {
                NekoJS.LOGGER.error("Probe backend {}:{} editor-config reset failed",
                        backend.languageId(), backend.name(), e);
            }
        }
        com.tkisor.nekojs.script.WorkspaceGenerator.createWorkspaceConfigs();
        return reset;
    }

    /** Static facade: Master probe switch for the global default coordinator. */
    public static boolean isEnabled() {
        return defaultCoordinator().isProbeEnabled();
    }

    /**
     * Shared class collection (delegated to {@link ProbeClassCollector#collect}): BFS closure starting from event/binding seeds,
     * filtered by {@link ProbeConfig} packages, returning a deterministic set sorted by fully qualified name.
     */
    public static LinkedHashSet<Class<?>> collectClasses(NekoScriptCatalogSnapshot snapshot, ProbeConfig cfg) {
        return ProbeClassCollector.collect(snapshot, cfg);
    }

    /**
     * Runs one probe over the selected backends: shared collection, rendering for each backend,
     * then one external side effect. This is the instance form of {@code run(...)}: all paths come
     * from this instance's {@link #paths} and external side effects use
     * {@link #externalArtifacts}.
     *
     * <p>One coordinator instance runs one probe at a time and fails fast: a call made while a run
     * is active returns {@code "probe already running"} immediately instead of queuing. A backend
     * whose outputDir is already used by an earlier backend in the same run is skipped and
     * reported as a failure, because in-place synchronization would delete the first runner's
     * artifacts as stale files. Result warnings also carry degradation of events and editor
     * configuration.
     *
     * @param snapshot current catalog snapshot
     * @param backends backends parsed from the command for this probe, already resolved by
     *                 priority and name
     * @return generation result per backend, in input order
     */
    public List<ProbeBackend.GenerateResult> runProbe(NekoScriptCatalogSnapshot snapshot, List<ProbeBackend> backends) {
        if (backends.isEmpty()) {
            return List.of(ProbeBackend.GenerateResult.failure("no probe backend selected"));
        }
        if (!running.compareAndSet(false, true)) {
            return backends.stream()
                    .map(b -> ProbeBackend.GenerateResult.failure("probe already running"))
                    .toList();
        }
        try {
            return runProbeLocked(snapshot, backends);
        } finally {
            running.set(false);
        }
    }

    private List<ProbeBackend.GenerateResult> runProbeLocked(NekoScriptCatalogSnapshot snapshot, List<ProbeBackend> backends) {
        ProbeConfig cfg = readConfig();
        if (!cfg.enabled()) {
            return backends.stream()
                    .map(b -> ProbeBackend.GenerateResult.failure("probe disabled in probe.toml"))
                    .toList();
        }
        // mode=NONE: Disable class scanning entirely (return early before shared collection and path resolution, do not perform any disk writes)
        if (cfg.scan().mode() == ProbeConfig.ScanConfig.ScanMode.NONE) {
            return backends.stream()
                    .map(b -> ProbeBackend.GenerateResult.failure("probe disabled (scan mode=NONE in probe.toml)"))
                    .toList();
        }

        final AuthoringPaths authoringPaths;
        try {
            authoringPaths = captureAuthoringPaths();
        } catch (IOException failure) {
            NekoJS.LOGGER.error("[NEKO-1019] Probe authoring destinations could not be resolved; generation was skipped", failure);
            return backends.stream().map(backend -> ProbeBackend.GenerateResult.failureOf(failure)).toList();
        }

        List<String> runWarnings = new ArrayList<>();
        LinkedHashSet<Class<?>> collected = ProbeClassCollector.collect(snapshot, cfg, runWarnings);
        NekoJSPaths paths = this.paths;

        // Shared IR: Built once when there are listeners for modify_type/assign_type, or when a backend requires IR (TS and Python built-in
        // backends declare requiresIr=true, making IR the sole rendering source).
        boolean needIr = ProbeEvents.MODIFY_TYPE.hasListeners()
                || ProbeEvents.ASSIGN_TYPE.hasListeners()
                || backends.stream().anyMatch(ProbeBackend::requiresIr);
        // Collection, reflection and event warnings are shared by all selected backends.
        Map<String, ApiTypeRef> assignMap = ProbeEvents.ASSIGN_TYPE.hasListeners()
                ? ProbeIrBuilder.fireAssignType(runWarnings)
                : Map.of();

        // Single shared thread pool: This is the only pool used for the entire probe run (IR reflection construction + parallel rendering within each backend)
        // (backends are scheduled serially by this class—the thread pool does not guarantee parallelism between backends)
        ExecutorService sharedPool = Executors.newFixedThreadPool(
                Math.max(1, Math.min(Runtime.getRuntime().availableProcessors(), 8)));
        try {
            List<TypeDecl> sharedIr = needIr
                    ? ProbeIrBuilder.buildAndMutateIr(collected, assignMap, sharedPool, runWarnings)
                    : null;

            // add_global / snippets: Collected once per probe run (triggered only if there are respective listeners)
            ProbeOverrides overrides = ProbeIrBuilder.fireOverrides(runWarnings);

            // External base configuration (e.g., WorkspaceGenerator writing jsconfig) is executed first, to be merged by backend's contributeEditorConfig
            try {
                Path baseDir = paths.gameDir().resolve(cfg.baseDir());
                externalArtifacts.generate(baseDir, authoringPaths);
            } catch (Exception e) {
                NekoJS.LOGGER.error("Probe external artifacts failed", e);
            }

            EditorConfigContributor editorConfigs = new FileEditorConfigContributor();
            List<ProbeBackend.GenerateResult> results = new ArrayList<>(backends.size());
            // Output directory deduplication: When two backends write to the same directory, stale file cleanup by the second runner will delete the artifacts of the first runner
            // (the second runner overwrites the first). Only the first selected backend runs for each directory, others are skipped with a warning—
            // Coexistence of multiple backends for the same language is valid, but sharing an output directory is not (use outputDir configuration to change directories).
            Set<Path> seenOutputDirs = new HashSet<>();
            for (ProbeBackend backend : backends) {
                Path langDir = backend.outputDir(paths, cfg).normalize();
                if (!seenOutputDirs.add(langDir)) {
                    NekoJS.LOGGER.warn("Probe backend {}:{} skipped: output directory {} is also targeted by another selected backend in this run",
                            backend.languageId(), backend.name(), langDir);
                    results.add(ProbeBackend.GenerateResult.failure(
                            "skipped: duplicate output directory " + langDir + " (already generated by another selected backend)"));
                    continue;
                }
                ProbeContext ctx = new ProbeContext.Of(
                        snapshot,
                        List.copyOf(collected),
                        cfg,
                        paths,
                        backend.languageId(),
                        langDir,
                        sharedIr,
                        overrides,
                        sharedPool,
                        authoringPaths);
                ProbeBackend.GenerateResult result;
                try {
                    result = backend.generate(ctx);
                } catch (Exception e) {
                    NekoJS.LOGGER.error("Probe backend {}:{} failed", backend.languageId(), backend.name(), e);
                    result = ProbeBackend.GenerateResult.failureOf(e);
                }
                // After a successful generation, contribute this backend's fragments to the editor
                // config (paths/extraPaths, merged idempotently, pointing at the real output
                // directory). A failure only degrades it (the artifacts are already committed)
                // but is added to this backend's warnings so the command layer sees it.
                List<String> backendWarnings = new ArrayList<>();
                if (result.success()) {
                    try {
                        backend.contributeEditorConfig(editorConfigs, ctx);
                    } catch (Exception e) {
                        NekoJS.LOGGER.debug("Probe backend {}:{} editor-config contribution failed",
                                backend.languageId(), backend.name(), e);
                        backendWarnings.add("editor-config contribution failed for " + backend.languageId() + ":"
                                + backend.name() + " (" + e + ")");
                    }
                }
                if (!runWarnings.isEmpty()) {
                    backendWarnings.addAll(0, runWarnings);
                }
                results.add(ProbeBackend.GenerateResult.withWarnings(result, backendWarnings));
            }

            return results;
        } finally {
            sharedPool.shutdown();
        }
    }

    /**
     * Static facade: delegates one probe to the global default coordinator. Existing command-layer
     * call sites keep working unchanged.
     */
    public static List<ProbeBackend.GenerateResult> run(NekoScriptCatalogSnapshot snapshot, List<ProbeBackend> backends) {
        return defaultCoordinator().runProbe(snapshot, backends);
    }

    /**
     * Startup probe, enabled when {@code probe.toml runAtStartup = true} (default false, opt-in).
     * Each platform calls it once on ServerStarted: it runs the default TS backend and writes a
     * summary of the result to the log. Any failure is only logged and never affects server
     * startup. Skipped when {@code enabled = false} or {@code scan.mode = NONE}.
     *
     * @return whether a probe actually ran (config enabled and a backend available)
     */
    public static boolean runStartupProbeIfConfigured() {
        ProbeConfig cfg = config();
        if (!cfg.enabled() || !cfg.runAtStartup()) return false;
        if (cfg.scan().mode() == ProbeConfig.ScanConfig.ScanMode.NONE) return false;
        List<ProbeBackend> backends = ProbeBackendSelector.defaultTypescript();
        if (backends.isEmpty()) {
            NekoJS.LOGGER.warn("Startup probe skipped: no typescript backend registered");
            return false;
        }
        try {
            var snapshot = NekoScriptCatalog.snapshot(NekoRuntimeAccess.get());
            for (ProbeBackend.GenerateResult r : defaultCoordinator().runProbe(snapshot, backends)) {
                if (r.success()) {
                    NekoJS.LOGGER.info("Startup probe: {} files in {}ms{}",
                            r.filesGenerated(), r.durationMs(), r.outputDir() == null ? "" : " -> " + r.outputDir());
                } else {
                    NekoJS.LOGGER.warn("Startup probe failed: {}", r.message());
                }
                for (String w : r.warnings()) {
                    NekoJS.LOGGER.warn("Startup probe warning: {}", w);
                }
            }
        } catch (Throwable t) {
            NekoJS.LOGGER.error("Startup probe crashed (server start continues)", t);
        }
        return true;
    }

    /** Number of registered backends; for diagnostics. */
    public static int registeredBackendCount() {
        return ProbeBackendRegistry.get().registrars().size();
    }

    /** Collects classes only and renders nothing; for diagnostics and tests. */
    public static Set<Class<?>> collectOnly(NekoScriptCatalogSnapshot snapshot) {
        return collectClasses(snapshot, config());
    }
}
