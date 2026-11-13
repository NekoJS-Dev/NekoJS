package com.tkisor.nekojs.probe;

import com.tkisor.nekojs.NekoJS;
import com.tkisor.nekojs.core.fs.NekoJSPaths;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Generates probe output for one language. Each backend renders the shared catalog plus the
 * collected classes into <b>one</b> target language, for example {@code "typescript"} to
 * {@code .d.ts} or {@code "python"} to {@code .pyi}.
 *
 * <p>Several backends share the reflection collection and IR that {@link ProbeCoordinator} runs
 * once. Each backend manages its own output directory ({@link #outputDir}) and they do not
 * interfere.
 *
 * <p><b>Implementation contract</b>: an implementation only provides {@link #render}, which
 * renders the output into memory (path relative to the output directory to UTF-8 text) and
 * <b>never touches the disk</b>. Writing files, cleaning stale files and checking paths stay
 * inside the output directory belong to the default {@link #generate}, which goes through
 * {@link ProbeOutputCommitter}. A third-party backend that needs full control can still override
 * {@code generate}, but that is discouraged: the commit semantics (backup/restore/leftover
 * cleanup) are easy to get wrong.
 *
 * <p>Implementations must be thread-safe, idempotent and must not block the main thread for long.
 */
public interface ProbeBackend {

    /** Target language id, such as {@code "typescript"} / {@code "python"}. Should match the
     * language id registered with {@code ScriptCompilerRegistry}. */
    String languageId();

    /** Backend name, unique within its language, such as {@code "builtin"}; used to select a
     * backend from commands and in conflict reports. */
    String name();

    /** Default priority when several backends serve the same language (higher wins; default 0). */
    default int priority() {
        return 0;
    }

    /**
     * Whether the backend needs the shared type declaration IR ({@code List<TypeDecl>}). The
     * built-in TypeScript and Python backends both return true, because the IR is the only render
     * source and {@link ProbeCoordinator} builds it through one reflection pass. A third-party
     * backend with its own source of class declarations may return false. Defaults to false.
     */
    default boolean requiresIr() {
        return false;
    }

    /**
     * Contributes and merges editor configuration fragments into each script directory after
     * generation (TypeScript to {@code paths} of jsconfig, Python to {@code extraPaths} of
     * pyrightconfig). {@link ProbeCoordinator} calls it once, after all backends have generated
     * and the external base configuration (WorkspaceGenerator) has been written. Empty by default.
     *
     * <p>An implementation should contribute through the {@code merge*} methods of
     * {@code contributor} and compute relative paths from its own
     * {@link ProbeContext#languageDir()} (the real output directory), so the editor configuration
     * always points at the newest output.
     */
    default void contributeEditorConfig(EditorConfigContributor contributor, ProbeContext ctx) {
    }

    /**
     * Deletes the editor configuration files this backend manages (used by
     * {@code /nekojs probe reset_config}). It deletes only files the backend owns entirely
     * (jsconfig/pyrightconfig/snippets) and leaves files shared with the user such as
     * {@code .vscode/settings.json} alone; the contribution keys there are corrected idempotently
     * by the next contribute. After the deletion,
     * {@code WorkspaceGenerator.createWorkspaceConfigs()} rebuilds the factory base configuration
     * and {@link #contributeEditorConfig} merges it into the full configuration. Empty by default.
     */
    default void resetEditorConfig(NekoJSPaths paths) {
    }

    /**
     * Output directory of this backend: prefers {@code [languages.<languageId>].outputDir} and
     * falls back to {@code <baseDir>/<languageId>}. A backend may override it entirely to use a
     * different layout.
     */
    default Path outputDir(NekoJSPaths paths, ProbeConfig config) {
        String dir = config.language(languageId())
                .flatMap(l -> Optional.ofNullable(l.outputDir()))
                .orElse(languageId());
        return paths.gameDir().resolve(config.baseDir()).resolve(dir);
    }

    /**
     * Renders the output into memory: returns a map from path relative to the output directory
     * ({@code '/'} separated, e.g. {@code "@package/net/minecraft/index.d.ts"}) to UTF-8 text.
     * Implementations must not touch the disk; writing files and committing them atomically
     * belong to the default {@link #generate}.
     *
     * <p>Throws when a precondition is not met (such as a missing shared IR); the default
     * generate turns that into a failure result and the previous output stays unchanged.
     */
    Map<String, String> render(ProbeContext ctx);

    /**
     * {@link #render} plus writing to disk (the default implementation): render into memory,
     * check the relative paths, then sync each file in place through
     * {@link ProbeOutputCommitter#commitInPlace}. A failed render touches no disk. When an
     * external process holds a file locked, the result is a failure that names that file.
     *
     * @return the generation result (file count, duration, output directory, warnings)
     */
    default GenerateResult generate(ProbeContext ctx) {
        long start = System.currentTimeMillis();
        Path outputDir = ctx.languageDir();

        Map<String, String> files;
        try {
            files = render(ctx);
        } catch (Exception e) {
            NekoJS.LOGGER.error("Probe [{}] render failed (no disk changes made)", languageId(), e);
            return GenerateResult.failure(outputDir, GenerateResult.messageOf(e));
        }
        if (files == null) {
            return GenerateResult.failure(outputDir, "render returned no files");
        }
        String illegal = ProbeOutputCommitter.firstIllegalRelativePath(files.keySet());
        if (illegal != null) {
            NekoJS.LOGGER.error("Probe [{}] produced an illegal output path: {}", languageId(), illegal);
            return GenerateResult.failure(outputDir, "illegal output path: " + illegal);
        }

        try {
            ProbeOutputCommitter.CommitReport report = ProbeOutputCommitter.commitInPlace(outputDir, files);
            long duration = System.currentTimeMillis() - start;
            NekoJS.LOGGER.info("Probe [{}] generated {} files in {}ms ({} written, {} unchanged, {} stale removed)",
                    languageId(), files.size(), duration,
                    report.written(), report.unchanged(), report.deleted());
            return GenerateResult.withWarnings(
                    GenerateResult.success(files.size(), duration, outputDir), report.warnings());
        } catch (Exception e) {
            NekoJS.LOGGER.error("Probe [{}] write failed (output may be partially updated)", languageId(), e);
            return GenerateResult.failure(outputDir, GenerateResult.messageOf(e));
        }
    }

    /**
     * Result of a generation. {@code outputDir} lets the command layer and logs locate the output
     * directly. {@code warnings} carries "generated but partially degraded" information, such as
     * an event listener that threw or an editor-config contribution that failed; without it these
     * only reach the log and callers cannot see them.
     */
    record GenerateResult(
            boolean success,
            int filesGenerated,
            long durationMs,
            String message,
            Path outputDir,
            List<String> warnings
    ) {
        public GenerateResult {
            warnings = warnings == null ? List.of() : List.copyOf(warnings);
        }

        /** Compatible with the old 4-argument call sites (outputDir = null, warnings = empty). */
        public GenerateResult(boolean success, int filesGenerated, long durationMs, String message) {
            this(success, filesGenerated, durationMs, message, null, List.of());
        }

        public static GenerateResult success(int filesGenerated, long durationMs) {
            return new GenerateResult(true, filesGenerated, durationMs, "OK", null, List.of());
        }

        public static GenerateResult success(int filesGenerated, long durationMs, Path outputDir) {
            return new GenerateResult(true, filesGenerated, durationMs, "OK", outputDir, List.of());
        }

        public static GenerateResult failure(String message) {
            return new GenerateResult(false, 0, 0, message, null, List.of());
        }

        /** A failure result that carries the output directory. The default generate path uses it, so when
         *  rendering or submitting fails the user still knows where the output went. */
        public static GenerateResult failure(Path outputDir, String message) {
            return new GenerateResult(false, 0, 0, message, outputDir, List.of());
        }

        /** Turns a throwable into a failure result. When the message is null or blank it falls back to
         *  {@code toString()}, so the result never says "backend failed: null". */
        public static GenerateResult failureOf(Throwable t) {
            return failure(messageOf(t));
        }

        static String messageOf(Throwable t) {
            String m = t.getMessage();
            return m == null || m.isBlank() ? t.toString() : m;
        }

        /** Returns a copy of {@code base} with {@code extra} appended to its warnings. Returns {@code base}
         *  unchanged when there is nothing to add. */
        public static GenerateResult withWarnings(GenerateResult base, List<String> extra) {
            if (extra == null || extra.isEmpty()) return base;
            List<String> merged = new ArrayList<>(base.warnings());
            merged.addAll(extra);
            return new GenerateResult(base.success(), base.filesGenerated(), base.durationMs(), base.message(), base.outputDir(), merged);
        }
    }
}
