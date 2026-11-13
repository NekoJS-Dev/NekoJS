package com.tkisor.nekojs.probe;

import com.tkisor.nekojs.api.catalog.NekoScriptCatalogSnapshot;
import com.tkisor.nekojs.authoring.AuthoringPaths;

import com.tkisor.nekojs.core.fs.NekoJSPaths;
import com.tkisor.nekojs.probe.events.ProbeOverrides;
import com.tkisor.nekojs.probe.ir.TypeDecl;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.ExecutorService;

/**
 * Shared context passed to {@link ProbeBackend#generate(ProbeContext)}.
 *
 * <p>{@link ProbeCoordinator} builds it after the shared collection pass and passes one copy to
 * each selected backend; only {@link #languageId()} and {@link #languageDir()} differ.
 *
 * <p>{@code ir()} ({@code List<TypeDecl>}) will expose the language-neutral declaration IR. It is
 * not available yet.
 */
public interface ProbeContext {

    /**
     * Catalog snapshot: events, bindings, adapters, recipe namespaces, registry types, managed
     * APIs and more.
     */
    NekoScriptCatalogSnapshot snapshot();

    /**
     * Classes found by the shared reflection closure, already filtered by the {@link ProbeConfig}
     * package rules. The list still holds {@code Class<?>} entries.
     */
    List<Class<?>> collectedClasses();

    /** Loaded probe configuration. */
    ProbeConfig config();

    /** The shared paths instance. */
    NekoJSPaths paths();

    /** Captured AI destinations, independent of the declaration output directory. */
    default AuthoringPaths authoringPaths() {
        return standaloneAuthoringPaths(paths());
    }

    /** Resolves defaults for standalone callers without consulting runtime configuration. */
    static AuthoringPaths standaloneAuthoringPaths(NekoJSPaths paths) {
        try {
            return AuthoringPaths.resolve(paths.gameDir(), AuthoringPaths.DEFAULT_DIRECTORY);
        } catch (IOException failure) {
            throw new UncheckedIOException("[NEKO-1019] Probe authoring destinations could not be resolved.", failure);
        }
    }

    /** Language id that the current backend targets. */
    String languageId();

    /** Output directory of the current backend, i.e. {@link ProbeBackend#outputDir}. */
    Path languageDir();

    /**
     * Shared type declaration IR. {@link ProbeCoordinator} builds it and fills it in after the
     * events fire when any selected backend has {@link ProbeBackend#requiresIr} set to true, or
     * when {@code probe.modify_type}/{@code probe.assign_type} has listeners; it already contains
     * the script edits and type redirections. It is {@code null} otherwise (TS then takes the old
     * path) and is immutable.
     */
    List<TypeDecl> ir();

    /**
     * Result collected from {@code probe.add_global} and {@code probe.snippets}. Never null; it is
     * empty when neither event has listeners.
     */
    ProbeOverrides overrides();

    /**
     * Shared thread pool for this probe run. {@link ProbeCoordinator} creates it, and the IR
     * reflection build and the parallel rendering inside each backend share it; one run uses one
     * pool. Backends are scheduled serially by the coordinator, so the pool does not promise
     * parallelism between backends. Tests that construct {@code ProbeContext.Of} directly get
     * {@code null} and the backend creates and closes its own pool. Defaults to {@code null}.
     */
    default ExecutorService sharedPool() {
        return null;
    }

    /** Simple immutable implementation. */
    record Of(
            NekoScriptCatalogSnapshot snapshot,
            List<Class<?>> collectedClasses,
            ProbeConfig config,
            NekoJSPaths paths,
            String languageId,
            Path languageDir,
            List<TypeDecl> ir,
            ProbeOverrides overrides,
            ExecutorService sharedPool,
            AuthoringPaths authoringPaths
    ) implements ProbeContext {
        public Of {
            java.util.Objects.requireNonNull(snapshot, "snapshot");
            java.util.Objects.requireNonNull(config, "config");
            java.util.Objects.requireNonNull(paths, "paths");
            java.util.Objects.requireNonNull(languageId, "languageId");
            java.util.Objects.requireNonNull(languageDir, "languageDir");
            collectedClasses = List.copyOf(collectedClasses == null ? List.of() : collectedClasses);
            ir = ir == null ? null : List.copyOf(ir);
            overrides = overrides == null ? ProbeOverrides.empty() : overrides;
        }

        /** Standalone render-only callers need not create a game directory until publication. */
        @Override public AuthoringPaths authoringPaths() {
            return authoringPaths == null ? standaloneAuthoringPaths(paths) : authoringPaths;
        }

        /** Compatibility constructor for callers without root-owned authoring destinations. */
        public Of(NekoScriptCatalogSnapshot snapshot,
                  List<Class<?>> collectedClasses,
                  ProbeConfig config,
                  NekoJSPaths paths,
                  String languageId,
                  Path languageDir,
                  List<TypeDecl> ir,
                  ProbeOverrides overrides,
                  ExecutorService sharedPool) {
            this(snapshot, collectedClasses, config, paths, languageId, languageDir, ir, overrides, sharedPool,
                    null);
        }

        /** Convenience constructor taking 8 arguments; leaves sharedPool null for older callers. */
        public Of(NekoScriptCatalogSnapshot snapshot,
                  List<Class<?>> collectedClasses,
                  ProbeConfig config,
                  NekoJSPaths paths,
                  String languageId,
                  Path languageDir,
                  List<TypeDecl> ir,
                  ProbeOverrides overrides) {
            this(snapshot, collectedClasses, config, paths, languageId, languageDir, ir, overrides, null);
        }

        /** Convenience constructor taking 7 arguments; uses empty overrides. */
        public Of(NekoScriptCatalogSnapshot snapshot,
                  List<Class<?>> collectedClasses,
                  ProbeConfig config,
                  NekoJSPaths paths,
                  String languageId,
                  Path languageDir,
                  List<TypeDecl> ir) {
            this(snapshot, collectedClasses, config, paths, languageId, languageDir, ir, ProbeOverrides.empty(), null);
        }

        /**
         * Convenience constructor taking 6 arguments; leaves ir null, uses empty overrides and
         * keeps older callers working.
         */
        public Of(NekoScriptCatalogSnapshot snapshot,
                  List<Class<?>> collectedClasses,
                  ProbeConfig config,
                  NekoJSPaths paths,
                  String languageId,
                  Path languageDir) {
            this(snapshot, collectedClasses, config, paths, languageId, languageDir, null, ProbeOverrides.empty(), null);
        }
    }
}
