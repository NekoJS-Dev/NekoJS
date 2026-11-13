package com.tkisor.nekojs.core.error;

import com.tkisor.nekojs.api.data.ScriptId;
import com.tkisor.nekojs.script.ScriptContainer;
import com.tkisor.nekojs.api.ScriptType;
import graal.graalvm.polyglot.PolyglotException;
import graal.graalvm.polyglot.Context;

import java.util.Collection;

/**
 * Records script error state.
 *
 * <p>Scope: only script-related errors are recorded — script entry execution, event and timer
 * callbacks, node:test, and module evaluation. Ordinary engineering failures such as
 * Java bootstrap, platform setup and plugin discovery stay on the logger and never reach
 * {@code ErrorTracker}.
 *
 * <p>Ordinary business classes that need error recording inject this interface, not the whole
 * {@code NekoCoreContext}.
 */
public interface ErrorTracker {
    ScriptError record(ScriptContainer script, Throwable error);

    default ScriptError record(Context context, ScriptContainer script, Throwable error) {
        return record(script, error);
    }

    void recordCallbackError(ScriptType type, String callbackKind, Throwable error);

    default void recordCallbackError(Context context, ScriptType type, String callbackKind, Throwable error) {
        recordCallbackError(type, callbackKind, error);
    }

    void recordEventError(ScriptType type, PolyglotException error);

    default void recordEventError(Context context, ScriptType type, PolyglotException error) {
        recordEventError(type, error);
    }

    void clear(ScriptId id);

    default void clear(Context context, ScriptId id) {
        clear(id);
    }

    void clearByScriptPath(ScriptType type, String relativePath);

    default void clearByScriptPath(Context context, ScriptType type, String relativePath) {
        clearByScriptPath(type, relativePath);
    }

    void clearByType(ScriptType type);

    Collection<ScriptError> getAllErrors();

    boolean hasErrors();

    int getErrorCount();
}
