package com.tkisor.nekojs.core.error;

import com.tkisor.nekojs.api.data.ScriptId;
import com.tkisor.nekojs.script.ScriptContainer;
import com.tkisor.nekojs.script.ScriptType;
import graal.graalvm.polyglot.PolyglotException;

import java.util.Collection;

/**
 * 脚本错误状态记录能力。
 *
 * <p>边界：只记录脚本入口执行、事件/timer callback、node:test、module evaluation 等脚本相关错误。
 * Java bootstrap、平台 setup、插件发现等普通工程错误继续走 logger，不进入 {@code ErrorTracker}。
 *
 * <p>普通业务类需要错误记录能力时注入此接口；不要注入完整 {@code NekoCoreContext}。
 */
public interface ErrorTracker {
    ScriptError record(ScriptContainer script, Throwable error);

    void recordCallbackError(ScriptType type, String callbackKind, Throwable error);

    void recordEventError(ScriptType type, PolyglotException error);

    void clear(ScriptId id);

    void clearByScriptPath(ScriptType type, String relativePath);

    void clearByType(ScriptType type);

    Collection<ScriptError> getAllErrors();

    boolean hasErrors();

    int getErrorCount();
}
