package com.tkisor.nekojs.core.error;

import com.tkisor.nekojs.api.event.ScriptErrorReporter;
import com.tkisor.nekojs.core.config.SandboxConfig;
import com.tkisor.nekojs.core.fs.NekoJSPaths;
import com.tkisor.nekojs.script.ScriptContainer;
import com.tkisor.nekojs.script.ScriptType;
import com.tkisor.nekojs.api.data.ScriptId;
import graal.graalvm.polyglot.PolyglotException;
import graal.graalvm.polyglot.Source;
import graal.graalvm.polyglot.SourceSection;

import java.util.Collection;

/**
 * Legacy static facade over a bootstrap-bound {@link DefaultErrorTracker} instance.
 *
 * <p>所有状态/格式化方法委托给 {@link #INSTANCE}，该实例由 bootstrap 通过
 * {@link #bindLegacy(DefaultErrorTracker)} 绑定（命名带 legacy，避免误认为长期 API）。
 * class-load 时先用默认配置创建一个过渡实例并绑定 {@link ScriptErrorReporter}，保证
 * bootstrap 完成 前 的早期 callback 不会 NPE；bootstrap 加载真实配置后用
 * {@link #bindLegacy(DefaultErrorTracker)} 替换为正式实例（此阶段尚无脚本执行，无错误丢失）。
 *
 * <p>本 facade 在 Phase 6 删除；新业务代码注入 {@link ErrorTracker} 实例，不走本类 static 方法。
 */
public class NekoErrorTracker {
    private static volatile DefaultErrorTracker INSTANCE = new DefaultErrorTracker(NekoJSPaths.legacy(), SandboxConfig.defaultConfig());

    static {
        ScriptErrorReporter.set(INSTANCE::recordCallbackError);
    }

    private NekoErrorTracker() {}

    public static void bindLegacy(DefaultErrorTracker tracker) {
        if (tracker == null) {
            return;
        }
        INSTANCE = tracker;
        ScriptErrorReporter.set(tracker::recordCallbackError);
    }

    public static DefaultErrorTracker legacyInstance() {
        return INSTANCE;
    }

    public static ScriptError record(ScriptContainer script, Throwable error) {
        return INSTANCE.record(script, error);
    }

    public static void recordEventError(ScriptType currentType, PolyglotException e) {
        INSTANCE.recordEventError(currentType, e);
    }

    public static void recordCallbackError(ScriptType currentType, String callbackKind, Throwable throwable) {
        INSTANCE.recordCallbackError(currentType, callbackKind, throwable);
    }

    public static void clear(ScriptId scriptId) {
        INSTANCE.clear(scriptId);
    }

    public static ScriptError get(ScriptId scriptId) {
        return INSTANCE.get(scriptId);
    }

    public static void clearByScriptPath(ScriptType type, String relativePath) {
        INSTANCE.clearByScriptPath(type, relativePath);
    }

    public static void clearAll() {
        INSTANCE.clearAll();
    }

    public static void clearByType(ScriptType type) {
        INSTANCE.clearByType(type);
    }

    public static boolean hasErrors() {
        return INSTANCE.hasErrors();
    }

    public static int getErrorCount() {
        return INSTANCE.getErrorCount();
    }

    public static Collection<ScriptError> getAllErrors() {
        return INSTANCE.getAllErrors();
    }

    public static SourceSection getBestSourceLocation(PolyglotException e) {
        return INSTANCE.getBestSourceLocation(e);
    }

    public static int getRealCodeLine(String pathStr, int mappedLine) {
        return INSTANCE.getRealCodeLine(pathStr, mappedLine);
    }

    public static String getMappedStackTrace(PolyglotException e) {
        return INSTANCE.getMappedStackTrace(e);
    }

    public static String extractRelativePath(Source source) {
        return INSTANCE.extractRelativePath(source);
    }
}
