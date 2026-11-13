package com.tkisor.nekojs.script;

import com.tkisor.nekojs.api.ScriptType;
import com.tkisor.nekojs.core.JavaClassLoadTelemetry;
import graal.graalvm.polyglot.Context;
import graal.graalvm.polyglot.Value;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.WeakHashMap;

/**
 * Narrow registry that maps a {@link Context} to a script: it offers {@code scriptTypeOf},
 * {@code currentScriptIdOf}, {@code switchCurrentScriptId} and nothing that exposes
 * {@link com.tkisor.nekojs.script.ScriptManager}.
 *
 * <p>Static entry point used by callbacks in the api layer ({@code EventBusJS}) and the core layer
 * ({@code NekoNodeTimers}).
 *
 * <ul>
 *   <li>Does not implement {@code managerOf(Context)}.</li>
 *   <li>Does not return {@code NekoCoreContext}, {@code NekoRuntimeRoot}, {@code NekoJS} or {@code ScriptManager}.</li>
 *   <li>Offers no generic lookup.</li>
 *   <li>{@link #unbind(Context)} must be called on the {@code ScriptManager.close()} and reset paths.</li>
 * </ul>
 */
public final class ScriptContextRegistry {
    private static final Map<Context, ScriptType> contextToScriptType = Collections.synchronizedMap(new WeakHashMap<>());
    private static final Map<Context, String> contextToScriptId = Collections.synchronizedMap(new WeakHashMap<>());

    /**
     * Caches the js global bindings per Context, so setCurrentScriptId does not resolve
     * {@code context.getBindings("js")} again every time.
     *
     * <p>A {@link Value} holds a strong reference to its {@link Context}, so each entry here refers
     * from the value back to its key. {@link #unbind(Context)} must remove the entry on the close
     * and reset paths, otherwise the Context never becomes eligible for GC.
     */
    private static final Map<Context, Value> contextToBindings = Collections.synchronizedMap(new WeakHashMap<>());

    /**
     * Last id actually written to the JS global {@code __nekoCurrentScriptId}. Skips a repeated
     * cross-interop write when the value has not changed. Maintained only while telemetry is on; it
     * is cleared while telemetry is off so the first write after telemetry comes back always lands
     * and no stale value remains.
     */
    private static final Map<Context, String> lastWrittenScriptId = Collections.synchronizedMap(new WeakHashMap<>());

    private ScriptContextRegistry() {}

    public static void bind(Context context, ScriptType scriptType) {
        if (context != null && scriptType != null) {
            contextToScriptType.put(context, scriptType);
        }
    }

    public static void unbind(Context context) {
        if (context == null) return;
        contextToScriptType.remove(context);
        contextToScriptId.remove(context);
        contextToBindings.remove(context);
        lastWrittenScriptId.remove(context);
    }

    public static ScriptType scriptTypeOf(Context context) {
        return contextToScriptType.get(context);
    }

    public static String currentScriptIdOf(Context context) {
        return contextToScriptId.get(context);
    }

    public static String switchCurrentScriptId(Context context, String scriptId) {
        String previous = contextToScriptId.get(context);
        setCurrentScriptId(context, scriptId);
        return previous;
    }

    public static void restoreCurrentScriptId(Context context, String scriptId) {
        setCurrentScriptId(context, scriptId);
    }

    private static void setCurrentScriptId(Context context, String scriptId) {
        if (context == null) return;
        if (scriptId == null || scriptId.isBlank()) {
            contextToScriptId.remove(context);
            publishScriptIdGlobal(context, null);
        } else {
            contextToScriptId.put(context, scriptId);
            publishScriptIdGlobal(context, scriptId);
        }
    }

    /**
     * Copies the current script id to the JS global {@code __nekoCurrentScriptId}.
     *
     * <p>The only reader of that global is the JS hook of JavaClassLoadTelemetry (see
     * {@code ScriptEnvironmentFactory#installJavaClassLoadTelemetry}; it is not installed at all
     * while telemetry is off). This method runs on the hot path of every listener dispatch and
     * resume, so writing the global with telemetry off is pure waste. It therefore short-circuits on
     * {@link JavaClassLoadTelemetry#isEnabled()}, which is a volatile read.
     *
     * <p>The {@code contextToScriptId} mirror is unaffected. Readers outside telemetry, such as
     * EventBusJS, NekoNodeTimers and RecipeJsonBuilder, always see the latest value. Only this class
     * writes the global after a Context is created (ScriptEnvironmentFactory writes null once during
     * create()), so while telemetry stays on the deduplication cache equals the actual global value.
     */
    private static void publishScriptIdGlobal(Context context, String scriptId) {
        if (!JavaClassLoadTelemetry.isEnabled()) {
            // Drop the deduplication entry too, so a write after telemetry comes back is not skipped
            lastWrittenScriptId.remove(context);
            return;
        }
        if (Objects.equals(lastWrittenScriptId.get(context), scriptId)) {
            return;
        }
        bindingsOf(context).putMember("__nekoCurrentScriptId", scriptId);
        lastWrittenScriptId.put(context, scriptId);
    }

    private static Value bindingsOf(Context context) {
        Value bindings = contextToBindings.get(context);
        if (bindings == null) {
            bindings = context.getBindings("js");
            contextToBindings.put(context, bindings);
        }
        return bindings;
    }
}
