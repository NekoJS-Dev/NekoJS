package com.tkisor.nekojs.core.error;

import com.tkisor.nekojs.core.fs.NekoJSPaths;
import com.tkisor.nekojs.script.ScriptContainer;
import com.tkisor.nekojs.script.ScriptType;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.client.resources.language.I18n;
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
        String pathStr = "null_script";
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
                } else if (source.getURI() != null) {
                    pathStr = source.getURI().toString().replace(NekoJSPaths.ROOT.toUri().toString(), "").replace('\\', '/');
                } else {
                    pathStr = source.getName();
                }
            }
        }

        ScriptType targetType = ScriptType.SERVER;
        String lowerPath = pathStr.toLowerCase();
        for (ScriptType type : ScriptType.all()) {
            if (lowerPath.contains(type.name + "_scripts")) {
                targetType = type;
                break;
            }
        }

        targetType.logger().error("[{}]: {}", pathStr, e.getMessage(), e);

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

    public static Collection<ScriptError> getAllErrors() {
        return ERRORS.values();
    }

    /**
     * 生成带有可点击链接的错误报告组件
     */
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

    public static Component getSuccessComponent() {
        return Component.translatable("nekojs.error.tracker.success");
    }
}