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
 * <p>实例对象，由 {@code ScriptEnvironmentFactory} 创建/持有/绑定（Phase 4B）。
 * 短期通过 {@link ScriptContextSeam} package-private static 代理窄方法，供 {@code EventBusJS}（api 层）、
 * {@code NekoNodeTimers}（core 层）等 callback 场景使用，不让 registry 自身变成 global singleton。
 *
 * <p>约束（见 {@code plan.md} Phase 4.1）：
 * <ul>
 *   <li>第一版不实现 {@code managerOf(Context)}。</li>
 *   <li>不返回 {@code NekoCoreContext} / {@code NekoRuntimeRoot} / {@code NekoJS} / {@code ScriptManager}。</li>
 *   <li>不提供泛型 lookup。</li>
 *   <li>{@link #unbind(Context)} 必须在 {@code ScriptEnvironment.close()} / {@code ScriptManager.close()} / reset 路径调用。</li>
 * </ul>
 */
public final class ScriptContextRegistry {
    private final Map<Context, ScriptType> contextToScriptType = Collections.synchronizedMap(new WeakHashMap<>());
    private final Map<Context, String> contextToScriptId = Collections.synchronizedMap(new WeakHashMap<>());

    public void bind(Context context, ScriptType scriptType) {
        if (context != null && scriptType != null) {
            contextToScriptType.put(context, scriptType);
        }
    }

    public void unbind(Context context) {
        if (context == null) return;
        contextToScriptType.remove(context);
        contextToScriptId.remove(context);
    }

    public ScriptType scriptTypeOf(Context context) {
        return contextToScriptType.get(context);
    }

    public String currentScriptIdOf(Context context) {
        return contextToScriptId.get(context);
    }

    public String switchCurrentScriptId(Context context, String scriptId) {
        String previous = contextToScriptId.get(context);
        setCurrentScriptId(context, scriptId);
        return previous;
    }

    public void restoreCurrentScriptId(Context context, String scriptId) {
        setCurrentScriptId(context, scriptId);
    }

    private void setCurrentScriptId(Context context, String scriptId) {
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
