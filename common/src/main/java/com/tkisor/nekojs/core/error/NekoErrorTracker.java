package com.tkisor.nekojs.core.error;

import com.tkisor.nekojs.NekoJS;
import com.tkisor.nekojs.core.fs.ClassFilter;
import com.tkisor.nekojs.core.fs.NekoJSPaths;
import com.tkisor.nekojs.core.module.esm.NekoEsmVirtualModuleRegistry;
import com.tkisor.nekojs.script.ScriptContainer;
import com.tkisor.nekojs.script.ScriptType;
import com.tkisor.nekojs.api.data.ScriptId;
import graal.graalvm.polyglot.PolyglotException;
import graal.graalvm.polyglot.Source;
import graal.graalvm.polyglot.SourceSection;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

public class NekoErrorTracker {
    private static final Map<ScriptId, ScriptError> ERRORS = new ConcurrentHashMap<>();

    private static final Set<String> HOST_FRAME_BLACKLIST = new CopyOnWriteArraySet<>(List.of(
            "org.graalvm.",
            "com.oracle.truffle.",
            "jdk.internal.reflect.",
            "net.neoforged.bus.",
            "com.tkisor.nekojs.utils.event.",
            "com.tkisor.nekojs.api.event.EventBus"
    ));

    public static ScriptError record(ScriptContainer script, Throwable error) {
        clear(script.id);
        clearByScriptPath(script.type, NekoJSPaths.ROOT.relativize(script.path).toString().replace('\\', '/'));
        ScriptError scriptError = new ScriptError(script, error);
        ERRORS.put(script.id, scriptError);
        return scriptError;
    }

    public static void recordEventError(ScriptType currentType, PolyglotException e) {
        recordCallbackError(currentType, "event", e);
    }

    public static void recordCallbackError(ScriptType currentType, String callbackKind, Throwable throwable) {
        String pathStr = callbackKind == null || callbackKind.isBlank() ? "Unknown" : callbackKind;

        if (throwable instanceof PolyglotException polyglotException) {
            SourceSection loc = getBestSourceLocation(polyglotException);
            if (loc != null) {
                Source source = loc.getSource();
                if (source != null) {
                    pathStr = extractRelativePath(source);
                }
            }
        } else {
            pathStr = pathStr + "/" + Integer.toHexString(Objects.hash(throwable.getClass().getName(), throwable.getMessage()));
        }

        String eventPath = pathStr;
        ScriptId runtimeId = eventErrorId(currentType, eventPath);
        ScriptError scriptError = ERRORS.compute(runtimeId, (ignored, previous) -> {
            ScriptError next = new ScriptError(currentType, runtimeId, eventPath, throwable);
            if (previous != null && sameEventError(previous, next)) {
                next.setOccurrenceCount(previous.getOccurrenceCount() + 1);
            }
            return next;
        });

        String detail = scriptError.getLogDetailText(ClassFilter.conciseScriptErrorLogs);
        String kind = callbackKind == null || callbackKind.isBlank() ? "callback" : callbackKind;
        if (currentType != null) {
            currentType.logger().error("Script {} callback exception:\n{}", kind, detail);
        }
        NekoJS.LOGGER.error("Script {} callback exception:\n{}", kind, detail);
    }

    public static SourceSection getBestSourceLocation(PolyglotException e) {
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

    public static int getRealCodeLine(String pathStr, int mappedLine) {
        if (mappedLine <= 0) return mappedLine;
        try {
            Path sourcePath = NekoJSPaths.ROOT.resolve(pathStr);
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
        } catch (Exception ignored) {}
        return mappedLine;
    }

    public static String getMappedStackTrace(PolyglotException e) {
        StringBuilder sb = new StringBuilder();
        sb.append(e.getMessage()).append("\n");

        for (PolyglotException.StackFrame frame : e.getPolyglotStackTrace()) {
            if (frame.isGuestFrame()) {
                SourceSection loc = frame.getSourceLocation();
                if (loc != null && loc.getSource() != null) {
                    String pathStr = extractRelativePath(loc.getSource());
                    int rawLine = loc.getStartLine();
                    int rawColumn = loc.getStartColumn();

                    SourceMapRegistry.OriginalPosition pos = SourceMapRegistry.getMappedPosition(pathStr, rawLine, rawColumn);
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

    public static String extractRelativePath(Source source) {
        if (source.getPath() != null) {
            String pathText = source.getPath();
            String scriptDisplayPath = extractScriptDisplayPath(pathText);
            if (scriptDisplayPath != null) {
                return scriptDisplayPath;
            }
            String virtualDisplayPath = NekoEsmVirtualModuleRegistry.displayPath(pathText);
            if (virtualDisplayPath != null) {
                return virtualDisplayPath;
            }
            try {
                Path path = Path.of(pathText);
                virtualDisplayPath = NekoEsmVirtualModuleRegistry.displayPath(path);
                if (virtualDisplayPath != null) {
                    return virtualDisplayPath;
                }
                return NekoJSPaths.ROOT.relativize(path).toString().replace('\\', '/');
            } catch (Exception ex) {
                return pathText.replace('\\', '/');
            }
        } else if (source.getURI() != null) {
            String uriText = source.getURI().toString();
            String scriptDisplayPath = extractScriptDisplayPath(uriText);
            if (scriptDisplayPath != null) {
                return scriptDisplayPath;
            }
            try {
                Path path = Path.of(source.getURI());
                String virtualDisplayPath = NekoEsmVirtualModuleRegistry.displayPath(path);
                if (virtualDisplayPath != null) {
                    return virtualDisplayPath;
                }
            } catch (Exception ignored) {
            }
            String virtualDisplayPath = NekoEsmVirtualModuleRegistry.displayPath(uriText);
            if (virtualDisplayPath != null) {
                return virtualDisplayPath;
            }
            return uriText.replace(NekoJSPaths.ROOT.toUri().toString(), "").replace('\\', '/');
        } else {
            String scriptDisplayPath = extractScriptDisplayPath(source.getName());
            return scriptDisplayPath != null ? scriptDisplayPath : source.getName();
        }
    }

    private static String extractScriptDisplayPath(String pathText) {
        if (pathText == null || pathText.isBlank()) {
            return null;
        }
        String normalized = pathText.replace('\\', '/');
        for (ScriptType type : ScriptType.all()) {
            String marker = type.name + "_scripts/";
            int index = normalized.indexOf(marker);
            if (index >= 0) {
                return normalized.substring(index);
            }
        }
        return null;
    }

    private static ScriptId eventErrorId(ScriptType type, String pathStr) {
        return new ScriptId("nekojs", "rt/" + type.name() + "/" + pathStr.replace(':', '_'));
    }

    private static boolean sameEventError(ScriptError previous, ScriptError next) {
        return Objects.equals(previous.getErrorMessage(), next.getErrorMessage())
                && previous.getLineNumber() == next.getLineNumber()
                && previous.getColumnNumber() == next.getColumnNumber();
    }

    public static void clear(ScriptId scriptId) { ERRORS.remove(scriptId); }
    public static ScriptError get(ScriptId scriptId) { return ERRORS.get(scriptId); }
    public static void clearByScriptPath(ScriptType type, String relativePath) {
        if (type == null || relativePath == null) return;
        ERRORS.entrySet().removeIf(entry -> {
            ScriptError error = entry.getValue();
            return error.getScriptType() == type && relativePath.equals(error.getDisplayPath());
        });
    }
    public static void clearAll() { ERRORS.clear(); }
    public static void clearByType(ScriptType type) {
        if (type == null) return;
        ERRORS.entrySet().removeIf(entry -> entry.getValue().getScriptType() == type);
    }

    public static boolean hasErrors() { return !ERRORS.isEmpty(); }
    public static int getErrorCount() { return ERRORS.size(); }
    public static Collection<ScriptError> getAllErrors() { return ERRORS.values(); }
}