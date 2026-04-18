package com.tkisor.nekojs.core.error;

import com.tkisor.nekojs.core.fs.NekoJSPaths;
import com.tkisor.nekojs.script.ScriptContainer;
import com.tkisor.nekojs.script.ScriptType;
import graal.graalvm.polyglot.PolyglotException;
import graal.graalvm.polyglot.Source;
import graal.graalvm.polyglot.SourceSection;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class NekoErrorTracker {
    private static final Map<Identifier, ScriptError> ERRORS = new ConcurrentHashMap<>();

    private static final String[] HOST_FRAME_BLACKLIST = {
            "org.graalvm.",
            "com.oracle.truffle.",
            "jdk.internal.reflect.",
            "net.neoforged.bus.",
            "com.tkisor.nekojs.utils.event.",
            "com.tkisor.nekojs.api.event.EventBus",
    };

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

        String cleanTrace = getMappedStackTrace(e);
        currentType.logger().error("脚本事件触发异常:\n{}", cleanTrace);

        String uniqueHashInput = currentType.name() + "_" + pathStr + "_" + mappedLine + "_" + e.getMessage();
        String safeHash = Integer.toHexString(uniqueHashInput.hashCode());
        Identifier runtimeId = Identifier.fromNamespaceAndPath("nekojs", "rt_" + safeHash);

        if (ERRORS.containsKey(runtimeId)) {
            ERRORS.get(runtimeId).incrementOccurrence();
        } else {
            ERRORS.put(runtimeId, new ScriptError(currentType, runtimeId, pathStr, e));
        }
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

    /**
     * 核心新增：智能修正行号，遇到注释或空行自动向下找
     */
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

    public static void clear(Identifier scriptId) { ERRORS.remove(scriptId); }
    public static void clearAll() { ERRORS.clear(); }
    public static void clearByType(ScriptType type) {
        if (type == null) return;
        ERRORS.entrySet().removeIf(entry -> {
            ScriptError error = entry.getValue();
            return error.getScriptType() == type;
        });
    }
    public static boolean hasErrors() { return !ERRORS.isEmpty(); }
    public static Collection<ScriptError> getAllErrors() { return ERRORS.values(); }
    public static Component getErrorComponent() {
        int errorCount = ERRORS.size();
        MutableComponent main = Component.translatable("nekojs.error.tracker.warning", errorCount);
        MutableComponent link = Component.translatable("nekojs.error.tracker.open_list")
                .withStyle(style -> style
                        .withHoverEvent(new HoverEvent.ShowText(Component.translatable("nekojs.error.tracker.hover_hint")))
                        .withClickEvent(new ClickEvent.RunCommand("/nekojs view_all_errors"))
                );
        return Component.empty().append(main).append("\n").append(link);
    }
    public static Component getSuccessComponent() { return Component.translatable("nekojs.error.tracker.success"); }
}