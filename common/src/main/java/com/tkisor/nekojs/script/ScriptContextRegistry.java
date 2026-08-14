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

    /**
     * 按 Context 缓存 js 全局 bindings，避免每次 setCurrentScriptId 都重新解析
     * {@code context.getBindings("js")}。
     *
     * <p>注意：{@link Value} 会强引用其所属 {@link Context}，因此本表条目构成
     * 「value 引用 key」的自引用，必须依赖 {@link #unbind(Context)} 在 close/reset
     * 路径清除（与既有约定一致），否则对应 Context 无法被 GC。
     */
    private static final Map<Context, Value> contextToBindings = Collections.synchronizedMap(new WeakHashMap<>());

    /**
     * 记录「已实际写入 JS 全局 {@code __nekoCurrentScriptId} 的最后一个 id」，
     * 值未变化时跳过重复的跨 interop 写。仅在遥测开启期间维护；遥测关闭期间
     * 主动清除，保证遥测重新启用后第一次写入一定落地（不残留旧值）。
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
     * 把当前 script id 同步到 JS 全局 {@code __nekoCurrentScriptId}。
     *
     * <p>该全局唯一的读者是 JavaClassLoadTelemetry 的 JS 侧钩子（见
     * {@code ScriptEnvironmentFactory#installJavaClassLoadTelemetry}，遥测关闭时根本不安装）。
     * 而本方法处于每次监听器分发 / 恢复的热路径，遥测关闭时写全局纯属浪费，
     * 故先用 {@link JavaClassLoadTelemetry#isEnabled()}（仅 volatile 读）短路。
     *
     * <p>注意 {@code contextToScriptId} 镜像不受此影响——EventBusJS / NekoNodeTimers /
     * RecipeJsonBuilder 等非遥测读者始终读取最新值。Context 创建后仅本类写该全局
     * （ScriptEnvironmentFactory 只在 create() 初始化时写 null），因此去重缓存
     * 「遥测持续开启期间 == 全局实际值」成立。
     */
    private static void publishScriptIdGlobal(Context context, String scriptId) {
        if (!JavaClassLoadTelemetry.isEnabled()) {
            // 同时作废去重缓存，避免遥测重新启用后跳过本应落地的写入
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
