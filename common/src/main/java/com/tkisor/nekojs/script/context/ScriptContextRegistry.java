package com.tkisor.nekojs.script.context;

import com.tkisor.nekojs.script.ScriptType;
import graal.graalvm.polyglot.Context;

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * 窄 {@link Context} 身份注册表：提供 {@code scriptTypeOf} / {@code currentScriptIdOf} /
 * {@code switchCurrentScriptId} 等窄能力，不暴露 {@link com.tkisor.nekojs.script.ScriptManager}。
 *
 * <p>静态 framework seam，供 api 层 callback（{@code EventBusJS}）和 core 层 callback
 * （{@code NekoNodeTimers}）使用。
 *
 * <ul>
 *   <li>不实现 {@code managerOf(Context)}。</li>
 *   <li>不返回 {@code NekoCoreContext} / {@code NekoRuntimeRoot} / {@code NekoJS} / {@code ScriptManager}。</li>
 *   <li>不提供泛型 lookup。</li>
 *   <li>{@link #unbind(Context)} 必须在 {@code ScriptManager.close()} / reset 路径调用。</li>
 * </ul>
 */
public final class ScriptContextRegistry {
    private static final Map<Context, ScriptType> contextToScriptType = Collections.synchronizedMap(new WeakHashMap<>());
    private static final Map<Context, String> contextToScriptId = Collections.synchronizedMap(new WeakHashMap<>());

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
            context.getBindings("js").putMember("__nekoCurrentScriptId", null);
        } else {
            contextToScriptId.put(context, scriptId);
            context.getBindings("js").putMember("__nekoCurrentScriptId", scriptId);
        }
    }
}
