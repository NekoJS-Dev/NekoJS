package com.tkisor.nekojs.core.error;

import com.tkisor.nekojs.api.data.ScriptId;
import com.tkisor.nekojs.core.config.SandboxConfig;
import com.tkisor.nekojs.core.fs.NekoJSPaths;
import com.tkisor.nekojs.core.module.esm.NekoEsmVirtualModuleRegistry;
import com.tkisor.nekojs.script.ScriptContainer;
import com.tkisor.nekojs.api.ScriptType;
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
    /**
     * 运行时回调错误（rt/ 前缀）容量上限。非 PolyglotException 去重键含
     * {@code hash(class+message)}，消息随时间变化的回调错误（如带时间戳/随机数的报错）
     * 每个新消息都会新增记录，使 {@link #errors} 无界增长。镜像
     * {@link SourceMapRegistry#CACHE_HARD_CAP} 的 4096 上限模式：超过上限时清空其它
     * 运行时回调错误、保留最近一条；脚本自身错误按 scriptId 存储、天然有界，不受影响。
     */
    private static final int MAX_RUNTIME_CALLBACK_ERRORS = 4096;
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
        ScriptError scriptError = ScriptError.create(script, error, this);
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
        // 先去重（只比对轻量签名，不读取源码文件）：命中同一错误时仅递增频次，
        // 避免高频回调（如 20Hz tick 循环）每次错误都重建 ScriptError 并重读源码文件。
        boolean[] created = new boolean[1];
        long[] previousCountHolder = new long[1];
        ScriptError scriptError = errors.compute(runtimeId, (ignored, previous) -> {
            if (previous != null && sameEventError(previous, throwable)) {
                previousCountHolder[0] = previous.getOccurrenceCount();
                created[0] = false;
                previous.incrementOccurrence();
                return previous;
            }
            created[0] = true;
            previousCountHolder[0] = 0L;
            return ScriptError.create(currentType, runtimeId, eventPath, throwable, this);
        });

        // 容量上限（仅超限时触发一次过滤，正常有界路径零开销）：ConcurrentHashMap.size()
        // 为 O(1)，超限后清空其它运行时回调错误、保留当前这条，防止消息持续变化的
        // 回调错误无界增长。脚本自身错误（scriptId 非 rt/ 前缀）不受影响。
        if (errors.size() > MAX_RUNTIME_CALLBACK_ERRORS) {
            errors.entrySet().removeIf(entry -> isRuntimeCallbackError(entry.getKey()) && !entry.getKey().equals(runtimeId));
        }

        if (shouldLogOccurrence(created[0], previousCountHolder[0], scriptError.getOccurrenceCount())) {
            String detail = scriptError.getLogDetailText(config.conciseScriptErrorLogs());
            String kind = callbackKind == null || callbackKind.isBlank() ? "callback" : callbackKind;
            // 唯一的控制台输出点：经 CollapsingAppender 写入 per-type 日志文件并镜像到主控制台。
            // 不再直接写 NekoJS.LOGGER，避免同一条回调错误在控制台重复输出。
            // 高频重复错误按里程碑节流：新建时记录，此后仅在 1→2、4→5、24→25、…、99→100、
            // 199→200 等跨越里程碑时记录，避免 20Hz tick 回调刷屏。
            currentType.logger().error("Script {} callback exception:\n{}", kind, detail);
        }
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

    /** 运行时回调错误 id 以 {@code rt/} 开头；脚本自身错误 id 为 {@code <type>_scripts/...} 相对路径。 */
    private static boolean isRuntimeCallbackError(ScriptId id) {
        return id != null && id.path().startsWith("rt/");
    }

    private boolean sameEventError(ScriptError previous, Throwable throwable) {
        ScriptError.ErrorSignature signature = ScriptError.parseSignature(this, throwable, previous.getScript());
        return Objects.equals(previous.getErrorMessage(), signature.errorMessage)
                && previous.getLineNumber() == signature.lineNumber
                && previous.getColumnNumber() == signature.columnNumber;
    }

    /**
     * 判断本次回调错误出现是否需要写日志。新建错误（{@code created}）必须记录一次；
     * 重复错误仅在出现次数跨越里程碑时记录。里程碑为 1, 2, 5, 10, 25, 50, 100，
     * 之后按 100 的倍数翻倍（100, 200, 400, ...）。
     *
     * @param created       本次出现是否新建了错误记录（而非命中已有记录）
     * @param previousCount 本次出现前的频次（新建时为 0）
     * @param newCount      本次出现后的频次
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
            if ("file".equalsIgnoreCase(source.getURI().getScheme())) {
                try {
                    Path path = Path.of(source.getURI());
                    String virtualDisplayPath = NekoEsmVirtualModuleRegistry.displayPath(path);
                    if (virtualDisplayPath != null) {
                        return virtualDisplayPath;
                    }
                } catch (Exception ignored) {
                    // A malformed local URI can still fall through to virtual path resolution.
                }
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

    private static boolean isVirtualPath(String pathText) {
        if (pathText == null || pathText.isBlank()) return false;
        int colon = pathText.indexOf(':');
        if (colon <= 0) return false;
        String scheme = pathText.substring(0, colon);
        return !"file".equalsIgnoreCase(scheme) && !scheme.matches("[A-Za-z]");
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
