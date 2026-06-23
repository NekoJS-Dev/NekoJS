package com.tkisor.nekojs.script.context;

import com.tkisor.nekojs.script.ScriptType;
import graal.graalvm.polyglot.Context;

/**
 * 专用 framework seam：代理 {@link ScriptContextRegistry} 的窄方法，供 api 层 callback
 * （{@code EventBusJS}）和 core 层 callback（{@code NekoNodeTimers}）使用。
 *
 * <p>短期为 package-private static，由 {@code ScriptEnvironmentFactory} 在 Context 创建后
 * 绑定 registry 实例（{@link #bind(ScriptContextRegistry)}）。不暴露 registry 自身、
 * {@link com.tkisor.nekojs.script.ScriptManager} 或宽对象图。
 *
 * <p>Phase 4B {@code ScriptEnvironmentFactory} 落地后，此 seam 绑定真实 registry；
 * Phase 6 删除 facade 时，api 层 callback 改为通过专用窄接口访问（或保留为永久 framework seam）。
 */
public final class ScriptContextSeam {
    private static volatile ScriptContextRegistry REGISTRY = new ScriptContextRegistry();

    private ScriptContextSeam() {}

    public static void bind(ScriptContextRegistry registry) {
        if (registry != null) {
            REGISTRY = registry;
        }
    }

    public static void bind(Context context, ScriptType scriptType) {
        REGISTRY.bind(context, scriptType);
    }

    public static ScriptType scriptTypeOf(Context context) {
        return REGISTRY.scriptTypeOf(context);
    }

    public static String currentScriptIdOf(Context context) {
        return REGISTRY.currentScriptIdOf(context);
    }

    public static String switchCurrentScriptId(Context context, String scriptId) {
        return REGISTRY.switchCurrentScriptId(context, scriptId);
    }

    public static void restoreCurrentScriptId(Context context, String scriptId) {
        REGISTRY.restoreCurrentScriptId(context, scriptId);
    }

    public static void unbind(Context context) {
        REGISTRY.unbind(context);
    }
}
