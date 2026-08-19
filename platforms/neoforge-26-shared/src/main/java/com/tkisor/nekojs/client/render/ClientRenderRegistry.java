package com.tkisor.nekojs.client.render;

import com.tkisor.nekojs.api.ScriptType;
import com.tkisor.nekojs.api.event.ScriptErrorReporter;
import com.tkisor.nekojs.script.ScriptContextRegistry;
import com.tkisor.nekojs.script.ScriptManager;
import graal.graalvm.polyglot.Context;
import graal.graalvm.polyglot.Value;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 脚本侧 HUD / 世界渲染器注册表：{@code ClientEvents.hudRender}/{@code worldRender}
 * 注册的回调按 id 记账（owner scriptId + layer + priority + Graal Value），
 * 平台渲染钩子（{@link ClientRenderEvents}）每帧按 (layer, priority) 排序分发。
 *
 * <p>容错与清理语义（对齐 {@code EventBusJS} 的监听器执行路径）：
 * <ul>
 *   <li>单个回调抛错经 {@link ScriptErrorReporter} 记录，不中断同帧其它渲染器；</li>
 *   <li>回调 Value 所属 Graal Context 已死亡（语句上限关闭等）时跳过分发并移除条目
 *       （自清理，泄漏有界到下一次事件）；</li>
 *   <li>CLIENT 脚本 reload 前由 {@code ClientRenderPlugin#beforeScriptsLoaded} 整表清空。</li>
 * </ul>
 *
 * <p>本类不引用任何 Minecraft 客户端类型，渲染上下文对象由版本侧监听器构造后透传
 * （{@link HudRenderContextJS}/{@link WorldRenderContextJS}）。
 */
public final class ClientRenderRegistry {
    /** HUD 渲染层：BACKGROUND 在原版 HUD 之前（RenderGuiEvent.Pre），NORMAL/FOREGROUND 在其后。 */
    public enum HudLayer {
        BACKGROUND, NORMAL, FOREGROUND
    }

    /** 世界渲染层：EARLY → 半透明方块后，NORMAL → 天气后，LATE → 关卡渲染收尾。 */
    public enum WorldLayer {
        EARLY, NORMAL, LATE
    }

    /** id → 渲染器条目。注册（脚本加载线程）与快照迭代（渲染线程）并发，用 ConcurrentHashMap。 */
    private static final Map<String, Entry> HUD_RENDERERS = new ConcurrentHashMap<>();
    private static final Map<String, Entry> WORLD_RENDERERS = new ConcurrentHashMap<>();

    private ClientRenderRegistry() {
    }

    /** 注册（或按 id 替换）一个 HUD 渲染器；回调形态 {@code (ctx, graphics) => void}。 */
    public static void registerHud(String id, String scriptId, Context context, HudLayer layer, int priority, Value callback) {
        HUD_RENDERERS.put(id, new Entry(scriptId, context, layer.ordinal(), priority, callback));
    }

    /** 注册（或按 id 替换）一个世界渲染器；回调形态 {@code (ctx) => void}。 */
    public static void registerWorld(String id, String scriptId, Context context, WorldLayer layer, int priority, Value callback) {
        WORLD_RENDERERS.put(id, new Entry(scriptId, context, layer.ordinal(), priority, callback));
    }

    /** 按 id 移除 HUD 渲染器；存在且移除成功返回 {@code true}。 */
    public static boolean unregisterHud(String id) {
        return HUD_RENDERERS.remove(id) != null;
    }

    /** 按 id 移除世界渲染器；存在且移除成功返回 {@code true}。 */
    public static boolean unregisterWorld(String id) {
        return WORLD_RENDERERS.remove(id) != null;
    }

    /** 指定 HUD 层是否至少有一个存活渲染器（渲染事件每帧触发，无监听时零开销快路径）。 */
    public static boolean hasHud(HudLayer layer) {
        return hasListener(HUD_RENDERERS, layer.ordinal());
    }

    /** 指定世界层是否至少有一个存活渲染器。 */
    public static boolean hasWorld(WorldLayer layer) {
        return hasListener(WORLD_RENDERERS, layer.ordinal());
    }

    /** 清空全部 HUD / 世界渲染器（CLIENT 脚本 reload 清理用）。 */
    public static void clearAll() {
        HUD_RENDERERS.clear();
        WORLD_RENDERERS.clear();
    }

    /**
     * 分发指定 HUD 层的渲染器：按 priority 升序执行，回调参数为 {@code (ctx, graphics)}。
     * 单个回调抛错只记录不中断；Context 已死的条目跳过并移除。
     */
    public static void dispatchHud(HudLayer layer, Object ctx, Object graphics) {
        dispatch(HUD_RENDERERS, layer.ordinal(), "hudRender", ctx, graphics);
    }

    /** 分发指定世界层的渲染器：按 priority 升序执行，回调参数为 {@code (ctx)}。 */
    public static void dispatchWorld(WorldLayer layer, Object ctx) {
        dispatch(WORLD_RENDERERS, layer.ordinal(), "worldRender", ctx, null);
    }

    private static boolean hasListener(Map<String, Entry> map, int layerOrdinal) {
        for (Entry entry : map.values()) {
            if (entry.layer() == layerOrdinal && !ScriptManager.isContextDead(entry.context())) {
                return true;
            }
        }
        return false;
    }

    private static void dispatch(Map<String, Entry> map, int layerOrdinal, String kind, Object ctx, Object graphics) {
        List<Entry> ordered = null;
        for (Map.Entry<String, Entry> e : map.entrySet()) {
            Entry entry = e.getValue();
            if (entry.layer() != layerOrdinal) {
                continue;
            }
            if (ScriptManager.isContextDead(entry.context())) {
                // 自清理：死 Context 上的闭包不可能再执行，安全移除（仅当仍是同一 id 的条目）
                map.remove(e.getKey(), entry);
                continue;
            }
            if (ordered == null) {
                ordered = new ArrayList<>(4);
            }
            ordered.add(entry);
        }
        if (ordered == null) {
            return;
        }
        if (ordered.size() > 1) {
            ordered.sort(Comparator.comparingInt(Entry::priority));
        }
        for (Entry entry : ordered) {
            invokeEntry(entry, kind, ctx, graphics);
        }
    }

    private static void invokeEntry(Entry entry, String kind, Object ctx, Object graphics) {
        try {
            String previousScriptId = ScriptContextRegistry.switchCurrentScriptId(entry.context(), entry.scriptId());
            try {
                if (graphics != null) {
                    entry.callback().executeVoid(ctx, graphics);
                } else {
                    entry.callback().executeVoid(ctx);
                }
            } finally {
                ScriptContextRegistry.restoreCurrentScriptId(entry.context(), previousScriptId);
            }
        } catch (Throwable t) {
            if (t instanceof InterruptedException) Thread.currentThread().interrupt();
            if (t instanceof Error) throw (Error) t;
            ScriptManager.reportContextKilled(entry.context(), t);
            ScriptErrorReporter.recordCallbackError(
                    ScriptType.CLIENT,
                    "renderer kind=" + kind + " script=" + (entry.scriptId() == null ? "unknown" : entry.scriptId()),
                    t);
        }
    }

    private record Entry(String scriptId, Context context, int layer, int priority, Value callback) {
    }
}
