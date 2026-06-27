package com.tkisor.nekojs.core.error;

import com.tkisor.nekojs.NekoJS;
import com.tkisor.nekojs.api.data.ScriptId;
import com.tkisor.nekojs.core.config.SandboxConfig;
import com.tkisor.nekojs.core.fs.NekoJSPaths;
import com.tkisor.nekojs.core.module.esm.NekoEsmVirtualModuleRegistry;
import com.tkisor.nekojs.script.ScriptContainer;
import com.tkisor.nekojs.script.ScriptType;
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

/**
 * 默认 {@link ErrorTracker} 实现：持有脚本错误状态为实例字段。
 *
 * <p>构造器接收 {@link NekoJSPaths}（脚本路径 relativize / source 查找）和
 * {@link SandboxConfig}（concise log 开关）。源码位置格式化 helper 为实例方法，
 * 供 {@link ScriptError} 复用。
 */
public final class DefaultErrorTracker implements ErrorTracker {
    private static final Set<String> HOST_FRAME_BLACKLIST = Set.of(
            "com.oracle.truffle",
            "org.graalvm",
            "com.tkisor.nekojs.core.error.DefaultErrorTracker",
            "com.tkisor.nekojs.script.ScriptExecutor",
            "com.tkisor.nekojs.script.ScriptManager"
    );
    private final Map<ScriptId, ScriptError> errors = new ConcurrentHashMap<>();
    private final NekoJSPaths paths;
    private final SandboxConfig config;

    public DefaultErrorTracker(NekoJSPaths paths, SandboxConfig config) {
        this.paths = paths;
        this.config = config;
    }

    public DefaultErrorTracker(SandboxConfig config) {
        this(NekoJSPaths.get(), config);
    }

    public NekoJSPaths paths() {
        return paths;
    }

    public SandboxConfig config() {
        return config;
    }

    @Override
    public ScriptError record(ScriptContainer script, Throwable error) {
        clear(script.id);
        clearByScriptPath(script.type, paths.root().relativize(script.path).toString().replace('\\', '/'));
        ScriptError scriptError = new ScriptError(script, error, this);
        errors.put(script.id, scriptError);
        return scriptError;
    }

    public void recordEventError(ScriptType currentType, PolyglotException e) {
        recordCallbackError(currentType, "event", e);
    }

    @Override
    public void recordCallbackError(ScriptType currentType, String callbackKind, Throwable throwable) {
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
        ScriptError scriptError = errors.compute(runtimeId, (ignored, previous) -> {
            ScriptError next = new ScriptError(currentType, runtimeId, eventPath, throwable, this);
            if (previous != null && sameEventError(previous, next)) {
                next.setOccurrenceCount(previous.getOccurrenceCount() + 1);
            }
            return next;
        });

        String detail = scriptError.getLogDetailText(config.conciseScriptErrorLogs());
        String kind = callbackKind == null || callbackKind.isBlank() ? "callback" : callbackKind;
        if (currentType != null) {
            currentType.logger().error("Script {} callback exception:\n{}", kind, detail);
        }
        NekoJS.LOGGER.error("Script {} callback exception:\n{}", kind, detail);
    }

    @Override
    public void clear(ScriptId scriptId) {
        errors.remove(scriptId);
    }

    public ScriptError get(ScriptId scriptId) {
        return errors.get(scriptId);
    }

    @Override
    public void clearByScriptPath(ScriptType type, String relativePath) {
        if (type == null || relativePath == null) return;
        errors.entrySet().removeIf(entry -> {
            ScriptError error = entry.getValue();
            return error.getScriptType() == type && relativePath.equals(error.getDisplayPath());
        });
    }

    public void clearAll() {
        errors.clear();
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
        return errors.values();
    }

    private ScriptId eventErrorId(ScriptType type, String pathStr) {
        return new ScriptId("nekojs", "rt/" + type.name() + "/" + pathStr.replace(':', '_'));
    }

    private static boolean sameEventError(ScriptError previous, ScriptError next) {
        return Objects.equals(previous.getErrorMessage(), next.getErrorMessage())
                && previous.getLineNumber() == next.getLineNumber()
                && previous.getColumnNumber() == next.getColumnNumber();
    }

    /* ================= 源码位置格式化 helper（实例方法，使用注入 paths） ================= */

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
        if (mappedLine <= 0) return mappedLine;
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
        } catch (Exception ignored) {} // file read error → return approximate mapped line
        return mappedLine;
    }

    public String getMappedStackTrace(PolyglotException e) {
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

    public String extractRelativePath(Source source) {
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
            try {
                Path path = Path.of(source.getURI());
                String virtualDisplayPath = NekoEsmVirtualModuleRegistry.displayPath(path);
                if (virtualDisplayPath != null) {
                    return virtualDisplayPath;
                }
            } catch (Exception ignored) { // URI parse failed → try alternate virtual path resolution
            }
            String virtualDisplayPath = NekoEsmVirtualModuleRegistry.displayPath(uriText);
            if (virtualDisplayPath != null) {
                return virtualDisplayPath;
            }
            return uriText.replace(paths.root().toUri().toString(), "").replace('\\', '/');
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
}
