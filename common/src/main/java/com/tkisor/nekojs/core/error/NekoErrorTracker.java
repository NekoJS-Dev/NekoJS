package com.tkisor.nekojs.core.error;

import com.tkisor.nekojs.NekoJSCommon;
import com.tkisor.nekojs.core.fs.NekoJSPaths;
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

    public static void record(ScriptContainer script, Throwable error) {
        ERRORS.put(script.id, new ScriptError(script, error));
    }

    public static void recordEventError(ScriptType currentType, PolyglotException e) {
        String pathStr = "Unknown";
        int mappedLine = -1;

        SourceSection loc = getBestSourceLocation(e);

        if (loc != null) {
            Source source = loc.getSource();
            if (source != null) {
                pathStr = extractRelativePath(source);
            }

            SourceMapRegistry.OriginalPosition pos = SourceMapRegistry.getMappedPosition(pathStr, loc.getStartLine(), loc.getStartColumn());
            mappedLine = getRealCodeLine(pathStr, pos.line);
        }

        String uniqueHashInput = currentType.name() + "_" + pathStr + "_" + mappedLine + "_" + e.getMessage();
        String safeHash = Integer.toHexString(uniqueHashInput.hashCode());
        ScriptId runtimeId = new ScriptId("nekojs", "rt_" + safeHash);

        ScriptError scriptError;
        if (ERRORS.containsKey(runtimeId)) {
            scriptError = ERRORS.get(runtimeId);
            scriptError.incrementOccurrence();
        } else {
            scriptError = new ScriptError(currentType, runtimeId, pathStr, e);
            ERRORS.put(runtimeId, scriptError);
        }

        String detail = scriptError.getFullDetailText();
        currentType.logger().error("Script event trigger exception:\n{}", detail);
        NekoJSCommon.LOGGER.error("[NekoJS] Script event trigger exception:\n{}", detail);
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
                    int realLine = getRealCodeLine(pathStr, pos.line);
                    String rootName = frame.getRootName();

                    if (pos.name != null && !pos.name.isEmpty()) {
                        rootName = pos.name;
                    } else if (rootName == null || rootName.isEmpty() || rootName.equals(":program")) {
                        rootName = "<anonymous>";
                    }

                    sb.append("    at ").append(rootName)
                            .append(" (").append(pathStr).append(":").append(realLine).append(")\n");
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

    private static String extractRelativePath(Source source) {
        if (source.getPath() != null) {
            try {
                return NekoJSPaths.ROOT.relativize(Path.of(source.getPath())).toString().replace('\\', '/');
            } catch (Exception ex) {
                return source.getPath().replace('\\', '/');
            }
        } else if (source.getURI() != null) {
            return source.getURI().toString().replace(NekoJSPaths.ROOT.toUri().toString(), "").replace('\\', '/');
        } else {
            return source.getName();
        }
    }

    public static void clear(ScriptId scriptId) { ERRORS.remove(scriptId); }
    public static void clearAll() { ERRORS.clear(); }
    public static void clearByType(ScriptType type) {
        if (type == null) return;
        ERRORS.entrySet().removeIf(entry -> entry.getValue().getScriptType() == type);
    }

    // 暴露核心数据状态，代替直接返回 Component
    public static boolean hasErrors() { return !ERRORS.isEmpty(); }
    public static int getErrorCount() { return ERRORS.size(); }
    public static Collection<ScriptError> getAllErrors() { return ERRORS.values(); }
}