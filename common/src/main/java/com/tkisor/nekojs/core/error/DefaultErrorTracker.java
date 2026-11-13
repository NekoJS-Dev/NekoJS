package com.tkisor.nekojs.core.error;

import com.tkisor.nekojs.api.data.ScriptId;
import com.tkisor.nekojs.core.config.SandboxConfig;
import com.tkisor.nekojs.core.fs.NekoJSPaths;
import com.tkisor.nekojs.core.module.NekoEsmVirtualModuleRegistry;
import com.tkisor.nekojs.core.module.NekoModulePipelineCache;
import com.tkisor.nekojs.core.module.NekoVirtualModuleView;
import com.tkisor.nekojs.script.ScriptContainer;
import com.tkisor.nekojs.api.ScriptType;
import com.tkisor.nekojs.core.fs.ScriptPathProvider;
import graal.graalvm.polyglot.PolyglotException;
import graal.graalvm.polyglot.Source;
import graal.graalvm.polyglot.SourceSection;
import graal.graalvm.polyglot.Context;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * Default {@link ErrorTracker} implementation: it keeps script error state in instance fields.
 *
 * <p>The constructor accepts {@link NekoJSPaths} (for script path relativization/source lookup) and
 * {@link SandboxConfig} (for concise log toggling). The source location formatting helpers are
 * instance methods so {@link ScriptError} can reuse them.
 */
public final class DefaultErrorTracker implements ErrorTracker {
    private static final Set<String> HOST_FRAME_BLACKLIST = Set.of(
            "com.oracle.truffle",
            "org.graalvm",
            "com.tkisor.nekojs.core.error.DefaultErrorTracker",
            "com.tkisor.nekojs.script.ScriptExecutor",
            "com.tkisor.nekojs.script.ScriptManager"
    );
    /**
     * Upper bound on runtime callback errors (the {@code rt/} prefix). For non-PolyglotException
     * errors the dedup key includes {@code hash(class+message)}, so a callback error whose message
     * changes over time (a timestamp or random value in the text) adds a new record for every new
     * message and would let {@link #errors} grow without bound. This mirrors the 4096 cap pattern
     * of {@link SourceMapRegistry#CACHE_HARD_CAP}: once the limit is exceeded, the other runtime
     * callback errors are cleared and the most recent one is kept. A script's own errors are
     * stored by scriptId and are naturally bounded, so the cap does not affect them.
     */
    private static final int MAX_RUNTIME_CALLBACK_ERRORS = 4096;
    private final Map<ScriptId, ScriptError> errors = new ConcurrentHashMap<>();
    private final Map<Context, Map<ScriptId, ScriptError>> candidateErrors = new ConcurrentHashMap<>();
    private final Set<Context> candidateContexts = ConcurrentHashMap.newKeySet();
    private final NekoJSPaths paths;
    private final SandboxConfig config;
    private final ModuleViews defaultModuleViews;
    private final Map<ScriptType, ModuleViews> activeModuleViews = new ConcurrentHashMap<>();
    private final Map<Context, ModuleViews> contextModuleViews = new ConcurrentHashMap<>();

    /** One environment's source-map/virtual-module view together with the generation it belongs to. */
    record ModuleViews(NekoSourceMapView sourceMaps, NekoVirtualModuleView virtualModules,
                       long generation, boolean candidate) {}

    public DefaultErrorTracker(NekoJSPaths paths, SandboxConfig config) {
        this(paths, config, new SourceMapRegistry(paths.root()), new NekoEsmVirtualModuleRegistry(paths.root()));
    }

    public DefaultErrorTracker(NekoJSPaths paths, SandboxConfig config, SourceMapRegistry sourceMaps,
                               NekoEsmVirtualModuleRegistry virtualModules) {
        this.paths = paths;
        this.config = config;
        this.defaultModuleViews = new ModuleViews(sourceMaps, virtualModules, -1L, false);
    }

    public NekoJSPaths paths() {
        return paths;
    }

    public SandboxConfig config() {
        return config;
    }

    NekoSourceMapView sourceMaps() {
        return defaultModuleViews.sourceMaps();
    }

    NekoVirtualModuleView virtualModules() {
        return defaultModuleViews.virtualModules();
    }

    ModuleViews moduleViews(ScriptType type) {
        ModuleViews active = type == null ? null : activeModuleViews.get(type);
        return active != null ? active : defaultModuleViews;
    }

    ModuleViews moduleViews(Context context, ScriptType type) {
        if (context != null) {
            ModuleViews views = contextModuleViews.get(context);
            if (views != null) return views;
        }
        return moduleViews(type);
    }

    /** Publish one type's active generation views without affecting any other type. */
    public void activateModuleViews(ScriptType type, NekoModulePipelineCache moduleSession) {
        activateModuleViews(type, null, moduleSession, -1L);
    }

    /** Restore the default view when a failed commit had no previous active module session. */
    public void removeActiveModuleViews(ScriptType type) {
        if (type != null) activeModuleViews.remove(type);
    }

    /** Publish an active view and bind it to the Context which owns that generation. */
    public void activateModuleViews(ScriptType type, Context context, NekoModulePipelineCache moduleSession) {
        activateModuleViews(type, context, moduleSession, -1L);
    }

    /**
     * Publishes the active view with the generation it belongs to. Errors freeze that generation
     * at record time, so a later generation switch never rewrites recorded history.
     */
    public void activateModuleViews(ScriptType type, Context context, NekoModulePipelineCache moduleSession,
                                    long generation) {
        if (type == null || moduleSession == null) return;
        activeModuleViews.put(type, new ModuleViews(moduleSession.sourceMapView(),
                moduleSession.virtualModuleView(), generation, false));
        if (context != null) {
            candidateContexts.remove(context);
            candidateErrors.remove(context);
            // Distinct instance on purpose: the Context-bound view is captured by ScriptError at
            // record time and must not alias the mutable type fallback slot.
            contextModuleViews.put(context, new ModuleViews(moduleSession.sourceMapView(),
                    moduleSession.virtualModuleView(), generation, false));
        }
    }

    /** Bind a candidate view only to its Context; active type fallback remains untouched. */
    public void activateCandidateModuleViews(ScriptType type, Context context,
                                              NekoModulePipelineCache moduleSession) {
        activateCandidateModuleViews(type, context, moduleSession, -1L);
    }

    /** Binds the candidate view to its candidate generation; staged errors freeze that generation and
     * the candidate flag. */
    public void activateCandidateModuleViews(ScriptType type, Context context,
                                              NekoModulePipelineCache moduleSession, long candidateGeneration) {
        if (type == null || context == null || moduleSession == null) return;
        candidateContexts.add(context);
        contextModuleViews.put(context, new ModuleViews(moduleSession.sourceMapView(),
                moduleSession.virtualModuleView(), candidateGeneration, true));
    }

    public void discardCandidateModuleViews(Context context) {
        if (context != null) {
            contextModuleViews.remove(context);
            candidateContexts.remove(context);
            candidateErrors.remove(context);
        }
    }

    public void removeModuleViews(Context context) {
        if (context != null) {
            contextModuleViews.remove(context);
            candidateContexts.remove(context);
            candidateErrors.remove(context);
        }
    }

    /** Publish staged candidate errors only after that generation has committed. */
    public void publishCandidateErrors(ScriptType type, Context context) {
        if (type == null || context == null) return;
        Map<ScriptId, ScriptError> staged = candidateErrors.remove(context);
        candidateContexts.remove(context);
        errors.entrySet().removeIf(entry -> entry.getValue().getScriptType() == type);
        if (staged != null) {
            errors.putAll(staged);
        }
    }

    /** Save one script type's errors before candidate execution can replace same-id entries. */
    public Map<ScriptId, ScriptError> snapshotType(ScriptType type) {
        Map<ScriptId, ScriptError> snapshot = new java.util.HashMap<>();
        errors.forEach((id, error) -> {
            if (error.getScriptType() == type) {
                snapshot.put(id, error);
            }
        });
        return snapshot;
    }

    /** Restore the pre-candidate error view after a failed generation. */
    public void restoreType(ScriptType type, Map<ScriptId, ScriptError> snapshot) {
        if (snapshot == null) return;
        errors.entrySet().removeIf(entry -> entry.getValue().getScriptType() == type);
        errors.putAll(snapshot);
    }

    @Override
    public ScriptError record(ScriptContainer script, Throwable error) {
        return record(null, script, error);
    }

    @Override
    public ScriptError record(Context context, ScriptContainer script, Throwable error) {
        clear(context, script.id);
        clearByScriptPath(context, script.type, relativeScriptPath(script.path));
        ScriptError scriptError = ScriptError.create(context, script, error, this);
        errorStore(context).put(script.id, scriptError);
        return scriptError;
    }

    /** Maps display paths without allowing world-pack locations outside root to hide the original failure. */
    private String relativeScriptPath(Path scriptPath) {
        try {
            return paths.root().relativize(scriptPath).toString().replace('\\', '/');
        } catch (IllegalArgumentException outsideRoot) {
            return scriptPath.toString().replace('\\', '/');
        }
    }

    public void recordEventError(ScriptType currentType, PolyglotException e) {
        recordCallbackError(null, currentType, "event", e);
    }

    @Override
    public void recordCallbackError(ScriptType currentType, String callbackKind, Throwable throwable) {
        recordCallbackError(null, currentType, callbackKind, throwable);
    }

    @Override
    public void recordCallbackError(Context context, ScriptType currentType, String callbackKind, Throwable throwable) {
        ModuleViews views = moduleViews(context, currentType);
        Map<ScriptId, ScriptError> errorStore = errorStore(context);
        String pathStr = callbackKind == null || callbackKind.isBlank() ? "Unknown" : callbackKind;

        if (throwable instanceof PolyglotException polyglotException) {
            SourceSection loc = getBestSourceLocation(polyglotException);
            if (loc != null) {
                Source source = loc.getSource();
                if (source != null) {
                    pathStr = extractRelativePath(currentType, source, views.virtualModules());
                }
            }
        } else {
            pathStr = pathStr + "/" + Integer.toHexString(Objects.hash(throwable.getClass().getName(), throwable.getMessage()));
        }

        String eventPath = pathStr;
        ScriptId runtimeId = eventErrorId(currentType, eventPath);
        // Deduplicate first, comparing only the lightweight signature and never reading a source file:
        // a hit on the same error only increments its count, so a high-frequency callback (such as a
        // 20Hz tick loop) does not rebuild the ScriptError and re-read the source file on every error.
        boolean[] created = new boolean[1];
        long[] previousCountHolder = new long[1];
        ScriptError scriptError = errorStore.compute(runtimeId, (ignored, previous) -> {
            if (previous != null && sameEventError(previous, throwable)) {
                previousCountHolder[0] = previous.getOccurrenceCount();
                created[0] = false;
                previous.incrementOccurrence();
                return previous;
            }
            created[0] = true;
            previousCountHolder[0] = 0L;
            return ScriptError.create(context, currentType, runtimeId, eventPath, throwable, this, callbackKind);
        });

        // Cap check, which only does work once the limit is exceeded so the normal bounded path costs
        // nothing: ConcurrentHashMap.size() is O(1), and over the limit the other runtime callback
        // errors are cleared and the current one is kept, so callback errors with continuously
        // changing messages cannot grow without bound. A script's own errors (scriptId without the
        // rt/ prefix) are not affected.
        if (errorStore.size() > MAX_RUNTIME_CALLBACK_ERRORS) {
            errorStore.entrySet().removeIf(entry -> isRuntimeCallbackError(entry.getKey()) && !entry.getKey().equals(runtimeId));
        }

        if (shouldLogOccurrence(created[0], previousCountHolder[0], scriptError.getOccurrenceCount())) {
            // The only console output point: it writes through CollapsingAppender into the per-type log file
            // and mirrors to the main console. The log line consumes the same frozen record, so the
            // describe() line carries phase/owner/generation next to the detail text and logs, panel
            // and report present the same core fields.
            String detail = scriptError.getLogDetailText(config.conciseScriptErrorLogs())
                    + "\n" + scriptError.diagnostic().describe();
            String kind = callbackKind == null || callbackKind.isBlank() ? "callback" : callbackKind;
            // Do not write NekoJS.LOGGER directly, so the same callback error is not printed twice.
            // High-frequency repeated errors are throttled by milestone: a new error is always
            // recorded, and from then on it is recorded only when the count crosses 1→2, 4→5, 24→25,
            // …, 99→100, 199→200, so a 20Hz tick callback cannot flood the log.
            com.tkisor.nekojs.script.ScriptTypeEnv.logger(currentType).error("Script {} callback exception:\n{}", kind, detail);
        }
    }

    @Override
    public void clear(ScriptId scriptId) {
        errors.remove(scriptId);
    }

    @Override
    public void clear(Context context, ScriptId scriptId) {
        errorStore(context).remove(scriptId);
    }

    public ScriptError get(ScriptId scriptId) {
        return errors.get(scriptId);
    }

    @Override
    public void clearByScriptPath(ScriptType type, String relativePath) {
        clearByScriptPath(null, type, relativePath);
    }

    @Override
    public void clearByScriptPath(Context context, ScriptType type, String relativePath) {
        if (type == null || relativePath == null) return;
        errorStore(context).entrySet().removeIf(entry -> {
            ScriptError error = entry.getValue();
            return error.getScriptType() == type && relativePath.equals(error.getDisplayPath());
        });
    }

    public void clearAll() {
        errors.clear();
        candidateErrors.clear();
    }

    @Override
    public void clearByType(ScriptType type) {
        if (type == null) return;
        errors.entrySet().removeIf(entry -> entry.getValue().getScriptType() == type);
    }

    public boolean hasErrors() {
        return !errors.isEmpty();
    }

    public int getErrorCount() {
        return errors.size();
    }

    @Override
    public Collection<ScriptError> getAllErrors() {
        return List.copyOf(errors.values());
    }

    /**
     * Read-only snapshot of the frozen records of the current public error set. Log history and
     * the dashboard packet are derived from these records instead of re-deriving error fields;
     * candidate errors stay out of this snapshot until the commit point publishes them.
     */
    public List<ScriptDiagnosticRecord> diagnostics() {
        return errors.values().stream().map(ScriptError::diagnostic).toList();
    }

    private Map<ScriptId, ScriptError> errorStore(Context context) {
        if (context != null && candidateContexts.contains(context)) {
            return candidateErrors.computeIfAbsent(context, ignored -> new ConcurrentHashMap<>());
        }
        return errors;
    }

    private ScriptId eventErrorId(ScriptType type, String pathStr) {
        return new ScriptId("nekojs", "rt/" + type.name() + "/" + pathStr.replace(':', '_'));
    }

    /** Runtime callback error ids start with {@code rt/}; a script's own error id is a
     * {@code <type>_scripts/...} relative path. */
    private static boolean isRuntimeCallbackError(ScriptId id) {
        return id != null && id.path().startsWith("rt/");
    }

    private boolean sameEventError(ScriptError previous, Throwable throwable) {
            ScriptError.ErrorSignature signature = ScriptError.parseSignature(this, throwable, previous.getScript(),
                    previous.getScriptType(), previous.capturedSourceMaps(), previous.capturedVirtualModules());
        return Objects.equals(previous.getErrorMessage(), signature.errorMessage)
                && previous.getLineNumber() == signature.lineNumber
                && previous.getColumnNumber() == signature.columnNumber;
    }

    /**
     * Whether this occurrence of a callback error needs a log line. A newly created error
     * ({@code created}) is always recorded once; a repeated error is recorded only when its
     * occurrence count crosses a milestone. Milestones are 1, 2, 5, 10, 25, 50, 100, and after
     * 100 every doubling (100, 200, 400, ...).
     *
     * @param created       whether this occurrence created a new error record instead of hitting an
     *                      existing one
     * @param previousCount count before this occurrence (0 when the error was just created)
     * @param newCount      frequency after this occurrence
     */
    static boolean shouldLogOccurrence(boolean created, long previousCount, long newCount) {
        if (created) {
            return true;
        }
        if (newCount <= previousCount) {
            return false;
        }
        return nextMilestoneAfter(previousCount) <= newCount;
    }

    private static long nextMilestoneAfter(long count) {
        if (count < 1) return 1;
        if (count < 2) return 2;
        if (count < 5) return 5;
        if (count < 10) return 10;
        if (count < 25) return 25;
        if (count < 50) return 50;
        if (count < 100) return 100;
        long milestone = 100;
        while (milestone <= count) {
            if (milestone > Long.MAX_VALUE / 2) {
                return Long.MAX_VALUE;
            }
            milestone <<= 1;
        }
        return milestone;
    }

    /* ================= Source location formatting helpers (instance methods, using injected paths) ================= */

    public SourceSection getBestSourceLocation(PolyglotException e) {
        if (e.getSourceLocation() != null) {
            return e.getSourceLocation();
        }
        for (PolyglotException.StackFrame frame : e.getPolyglotStackTrace()) {
            if (frame.isGuestFrame() && frame.getSourceLocation() != null) {
                return frame.getSourceLocation();
            }
        }
        return null;
    }

    public int getRealCodeLine(String pathStr, int mappedLine) {
        if (mappedLine <= 0 || isVirtualPath(pathStr)) return mappedLine;
        try {
            Path sourcePath = paths.root().resolve(pathStr);
            if (Files.exists(sourcePath)) {
                List<String> allLines = Files.readAllLines(sourcePath);
                int lineIndex = mappedLine - 1;
                while (lineIndex >= 0 && lineIndex < allLines.size()) {
                    String line = allLines.get(lineIndex).trim();
                    if (line.startsWith("//") || line.startsWith("/*") || line.startsWith("*") || line.isEmpty()) {
                        lineIndex++;
                    } else {
                        return lineIndex + 1;
                    }
                }
            }
        } catch (Exception ignored) {
            // file read error → return approximate mapped line
            com.tkisor.nekojs.NekoJS.LOGGER.debug("DefaultErrorTracker: failed to read source file for real code line lookup: " + pathStr, ignored);
        }
        return mappedLine;
    }

    public String getMappedStackTrace(PolyglotException e) {
        ModuleViews views = moduleViews(null);
        return getMappedStackTrace(e, null, views.sourceMaps(), views.virtualModules());
    }

    String getMappedStackTrace(PolyglotException e, ScriptType type,
                               NekoSourceMapView sourceMapView, NekoVirtualModuleView virtualModuleView) {
        StringBuilder sb = new StringBuilder();
        sb.append(e.getMessage()).append("\n");

        for (PolyglotException.StackFrame frame : e.getPolyglotStackTrace()) {
            if (frame.isGuestFrame()) {
                SourceSection loc = frame.getSourceLocation();
                if (loc != null && loc.getSource() != null) {
                    String pathStr = extractRelativePath(type, loc.getSource(), virtualModuleView);
                    int rawLine = loc.getStartLine();
                    int rawColumn = loc.getStartColumn();

                    SourceMapRegistry.OriginalPosition pos = sourceMapView.getMappedPosition(pathStr, rawLine, rawColumn);
                    String mappedPath = pos.path != null && !pos.path.isBlank() ? pos.path : pathStr;
                    int realLine = getRealCodeLine(mappedPath, pos.line);
                    String rootName = frame.getRootName();

                    if (pos.name != null && !pos.name.isEmpty()) {
                        rootName = pos.name;
                    } else if (rootName == null || rootName.isEmpty() || rootName.equals(":program")) {
                        rootName = "<anonymous>";
                    }

                    sb.append("    at ").append(rootName)
                            .append(" (").append(mappedPath).append(":").append(realLine).append(")\n");
                } else {
                    String rootName = frame.getRootName() != null && !frame.getRootName().isEmpty() ? frame.getRootName() : "<anonymous>";
                    sb.append("    at ").append(rootName).append(" (Unknown Source)\n");
                }
            } else if (frame.isHostFrame()) {
                String hostStr = frame.toHostFrame().toString();

                boolean isNoise = false;
                for (String blacklisted : HOST_FRAME_BLACKLIST) {
                    if (hostStr.contains(blacklisted)) {
                        isNoise = true;
                        break;
                    }
                }

                if (!isNoise) {
                    sb.append("    at [Java] ").append(hostStr).append("\n");
                }
            }
        }
        return sb.toString();
    }

    public String extractRelativePath(Source source) {
        ModuleViews views = moduleViews(null);
        return extractRelativePath(null, source, views.virtualModules());
    }

    String extractRelativePath(ScriptType type, Source source) {
        return extractRelativePath(type, source, moduleViews(type).virtualModules());
    }

    String extractRelativePath(ScriptType type, Source source, NekoVirtualModuleView virtualModuleView) {
        if (source.getPath() != null) {
            String pathText = source.getPath();
            String scriptDisplayPath = extractScriptDisplayPath(pathText);
            if (scriptDisplayPath != null) {
                return scriptDisplayPath;
            }
            String virtualDisplayPath = virtualModuleView.displayPath(pathText);
            if (virtualDisplayPath != null) {
                return virtualDisplayPath;
            }
            try {
                Path path = Path.of(pathText);
                virtualDisplayPath = virtualModuleView.displayPath(path);
                if (virtualDisplayPath != null) {
                    return virtualDisplayPath;
                }
                return paths.root().relativize(path).toString().replace('\\', '/');
            } catch (Exception ex) {
                return pathText.replace('\\', '/');
            }
        } else if (source.getURI() != null) {
            String uriText = source.getURI().toString();
            String scriptDisplayPath = extractScriptDisplayPath(uriText);
            if (scriptDisplayPath != null) {
                return scriptDisplayPath;
            }
            if ("file".equalsIgnoreCase(source.getURI().getScheme())) {
                try {
                    Path path = Path.of(source.getURI());
                    String virtualDisplayPath = virtualModuleView.displayPath(path);
                    if (virtualDisplayPath != null) {
                        return virtualDisplayPath;
                    }
                } catch (Exception ignored) {
                    // A malformed local URI can still fall through to virtual path resolution.
                }
            }
            String virtualDisplayPath = virtualModuleView.displayPath(uriText);
            if (virtualDisplayPath != null) {
                return virtualDisplayPath;
            }
            return uriText.replace(paths.root().toUri().toString(), "").replace('\\', '/');
        } else {
            String scriptDisplayPath = extractScriptDisplayPath(source.getName());
            return scriptDisplayPath != null ? scriptDisplayPath : source.getName();
        }
    }

    private static boolean isVirtualPath(String pathText) {
        if (pathText == null || pathText.isBlank()) return false;
        int colon = pathText.indexOf(':');
        if (colon <= 0) return false;
        String scheme = pathText.substring(0, colon);
        return !"file".equalsIgnoreCase(scheme) && !scheme.matches("[A-Za-z]");
    }

    private String extractScriptDisplayPath(String pathText) {
        if (pathText == null || pathText.isBlank()) {
            return null;
        }
        String normalized = pathText.replace('\\', '/');
        String textualAuthored = ScriptPathProvider.authoredPathText(
                normalized, paths.root().getFileSystem());
        if (textualAuthored != null) return textualAuthored;
        for (ScriptType type : ScriptType.all()) {
            String marker = "/" + type.scriptsDirectoryName() + "/";
            int markerStart = normalized.indexOf(marker);
            if (markerStart < 0) continue;
            String prefix = normalized.substring(0, markerStart);
            if (prefix.contains("/node_modules/")) continue;
            int packStart = Math.max(prefix.lastIndexOf("/packs/"),
                    Math.max(prefix.lastIndexOf("/nekojs_packs/"), prefix.lastIndexOf("/server_packs/")));
            if (packStart >= 0) {
                String authored = normalized.substring(packStart + 1);
                if (ScriptPathProvider.authoredPathText(authored, paths.root().getFileSystem()) != null) {
                    return authored;
                }
                continue;
            }
            return normalized.substring(markerStart + 1);
        }
        Path providerPath;
        try {
            providerPath = paths.root().getFileSystem().getPath(normalized);
        } catch (Exception ignored) {
            providerPath = null;
        }
        if (providerPath != null) {
            String authored = ScriptPathProvider.authoredPath(providerPath);
            if (ScriptPathProvider.typeOf(providerPath) != null) return authored;
        }
        return null;
    }
}
