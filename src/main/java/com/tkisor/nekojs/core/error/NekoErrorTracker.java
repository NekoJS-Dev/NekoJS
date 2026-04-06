package com.tkisor.nekojs.core.error;

import com.tkisor.nekojs.core.fs.NekoJSPaths;
import com.tkisor.nekojs.script.ScriptContainer;
import com.tkisor.nekojs.script.ScriptType;
import net.minecraft.resources.Identifier;
import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.Source;

import java.nio.file.Path;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class NekoErrorTracker {
    private static final Map<Identifier, ScriptError> ERRORS = new ConcurrentHashMap<>();

    public static void record(ScriptContainer script, Throwable error) {
        ERRORS.put(script.id, new ScriptError(script, error));
    }

    public static void recordEventError(PolyglotException e) {
        String pathStr = "未知脚本";
        int line = -1;

        if (e.getSourceLocation() != null) {
            line = e.getSourceLocation().getStartLine();
            Source source = e.getSourceLocation().getSource();

            if (source != null) {
                if (source.getPath() != null) {
                    try {
                        pathStr = NekoJSPaths.ROOT.relativize(Path.of(source.getPath())).toString().replace('\\', '/');
                    } catch (Exception ex) {
                        pathStr = source.getPath().replace('\\', '/');
                    }
                }
                else if (source.getURI() != null) {
                    pathStr = source.getURI().toString().replace(NekoJSPaths.ROOT.toUri().toString(), "").replace('\\', '/');
                }
                else {
                    pathStr = source.getName();
                }
            }
        }

        // 根据路径动态推断环境并打印到对应 log 文件
        ScriptType targetType = ScriptType.COMMON;
        String lowerPath = pathStr.toLowerCase();
        for (ScriptType type : ScriptType.values()) {
            if (lowerPath.contains(type.name + "_scripts")) {
                targetType = type;
                break;
            }
        }

        targetType.logger().error("事件监听器执行异常于 [{}]: {}", pathStr, e.getMessage(), e);

        String uniqueHashInput = pathStr + "_" + line + "_" + e.getMessage();
        String safeHash = Integer.toHexString(uniqueHashInput.hashCode());
        Identifier runtimeId = Identifier.fromNamespaceAndPath("nekojs", "rt_" + safeHash);

        if (ERRORS.containsKey(runtimeId)) {
            ERRORS.get(runtimeId).incrementOccurrence();
        } else {
            ERRORS.put(runtimeId, new ScriptError(runtimeId, pathStr, e));
        }
    }

    public static void clear(Identifier scriptId) {
        ERRORS.remove(scriptId);
    }

    public static void clearAll() {
        ERRORS.clear();
    }

    public static boolean hasErrors() {
        return !ERRORS.isEmpty();
    }

    public static ScriptError getError(Identifier scriptId) {
        return ERRORS.get(scriptId);
    }

    public static Collection<ScriptError> getAllErrors() {
        return ERRORS.values();
    }
}