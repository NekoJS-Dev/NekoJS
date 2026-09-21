package com.tkisor.nekojs.client.render;

import com.tkisor.nekojs.api.ScriptType;
import com.tkisor.nekojs.api.event.ScriptErrorReporter;
import com.tkisor.nekojs.core.state.CandidateStatePlan;
import com.tkisor.nekojs.script.ScriptContextRegistry;
import com.tkisor.nekojs.script.ScriptManager;
import graal.graalvm.polyglot.Context;
import graal.graalvm.polyglot.Value;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 脚本侧 HUD / 世界渲染器注册表：{@code ClientEvents.hudRender}/{@code worldRender}
 * 注册的回调按 id 记账（owner scriptId + layer + priority + Graal Value），
 * 平台渲染钩子（{@link ClientRenderEvents}）每帧按 (layer, priority) 排序分发。
 *
 * <p>generation 语义（票 26 AC4）：候选 generation 的脚本执行期注册<b>不</b>进生产表，
 * 而是收进该候选的 {@link Candidate} 批次（inert：渲染钩子看不到）；commit 点由
 * {@link Candidate#publish()} 整批换装，候选失败/取消时旧 active 的渲染器继续服务。
 * 初始加载、击杀重建与单文件 reload 走非事务路径：由新 generation Context 的首个注册
 * 换装整表，同一 Context 的后续注册只覆盖同 id 条目（见 {@link #beginActiveGeneration}）。
 *
 * <p>容错与清理语义（对齐 {@code EventBusJS} 的监听器执行路径）：
 * <ul>
 *   <li>单个回调抛错经 {@link ScriptErrorReporter} 记录，不中断同帧其它渲染器；</li>
 *   <li>回调 Value 所属 Graal Context 已死亡（语句上限关闭等）时跳过分发并移除条目
 *       （自清理，泄漏有界到下一次事件）；</li>
 *   <li>换装点只有两个：候选 commit 点、新 active Context 的首个注册。没有「脚本加载前
 *       整表清空」的独立清理点（那会连旧 active 一起清掉，见票 26 AC4）。</li>
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

    /**
     * 候选 generation 的挂起批次：key = 构建中的候选 Context。批次只在候选构建期存在，
     * commit 点或下一轮候选的首个注册时移除（见 {@link Candidate#publish()}）。
     */
    private static final Map<Context, Candidate> CANDIDATE_BATCHES = new ConcurrentHashMap<>();

    /** 生产表当前所属的 generation Context（非事务路径的换装判据）。 */
    private static volatile Context activeContext;

    /** 候选批次的领域标识（收集器与候选计划共用，进入候选联合边界的 domain 归因）。 */
    static final String CANDIDATE_DOMAIN = "client-render-registration";

    private ClientRenderRegistry() {
    }

    /** 注册（或按 id 替换）一个 HUD 渲染器；回调形态 {@code (ctx, graphics) => void}。 */
    public static void registerHud(String id, String scriptId, Context context, HudLayer layer, int priority, Value callback) {
        register(Kind.HUD, id, scriptId, context, layer.ordinal(), priority, callback);
    }

    /** 注册（或按 id 替换）一个世界渲染器；回调形态 {@code (ctx) => void}。 */
    public static void registerWorld(String id, String scriptId, Context context, WorldLayer layer, int priority, Value callback) {
        register(Kind.WORLD, id, scriptId, context, layer.ordinal(), priority, callback);
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

    /** 清空全部 HUD / 世界渲染器，并释放 generation 身份（下一次注册即新 generation）。 */
    public static void clearAll() {
        HUD_RENDERERS.clear();
        WORLD_RENDERERS.clear();
        activeContext = null;
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

    // ---- 注册路由：候选批次 vs 生产表 ----

    private enum Kind {
        HUD, WORLD
    }

    private static Map<String, Entry> live(Kind kind) {
        return kind == Kind.HUD ? HUD_RENDERERS : WORLD_RENDERERS;
    }

    /**
     * 路由一次脚本注册：来源 Context 属于构建中的候选 generation 时收进候选批次并返回；
     * 否则认领/复用当前 active generation 后写入生产表。
     */
    private static void register(Kind kind, String id, String scriptId, Context context,
                                 int layer, int priority, Value callback) {
        Entry entry = new Entry(scriptId, context, layer, priority, callback);
        if (!collectIntoCandidate(kind, id, entry, context)) {
            beginActiveGeneration(context);
            live(kind).put(id, entry);
        }
    }

    /**
     * 候选收集：{@code context} 是构建中的候选 Context 时，把条目放进该候选的 inert 批次
     * （生产表不动，渲染钩子看不到），批次随候选挂在 {@code CandidateStatePlan} 联合边界上，
     * commit 点整批发布、失败/取消随候选丢弃。返回 {@code false} 表示不是候选注册。
     */
    private static boolean collectIntoCandidate(Kind kind, String id, Entry entry, Context context) {
        if (context == null) {
            return false;
        }
        Candidate batch = CANDIDATE_BATCHES.get(context);
        if (batch == null) {
            Candidate fresh = new Candidate(context);
            if (!ScriptManager.registerCandidatePlan(context, fresh)) {
                // 非候选 Context（初始加载 / 击杀重建 / 单文件 reload）：生产表路径
                return false;
            }
            // 上一轮候选（失败或取消）的批次以已死 Context 为 key，随本轮首个注册丢弃。
            // ponytail: 单候选假设——CLIENT reload 由 ScriptManager 实例锁与 ClientReloadExecutor 串行化。
            // 计划已进候选联合边界：本批次的 preflight/publish 由联合边界驱动。
            fresh.registered = true;
            CANDIDATE_BATCHES.values().removeIf(other -> other != fresh);
            CANDIDATE_BATCHES.put(context, fresh);
            batch = fresh;
        }
        batch.add(kind, id, entry);
        return true;
    }

    /**
     * 领域收集器入口（DOMAIN_PLAN 阶段）：把本候选的批次挂上候选的联合预检/发布边界。
     *
     * <p>注册<b>无条件</b>发生，空批次也要注册——「新 generation 不再注册任何渲染器」正是
     * 旧渲染器退役的唯一路径，只有空批次也走到 commit 点，生产表才会被换成空表
     * （{@link com.tkisor.nekojs.core.lifecycle.CandidateDomainCollector} 的空计划契约）。
     * 首次注册时已在候选执行期注册过同一批次实例的，这里不重复注册。
     */
    static void registerCandidateBatch(Context context,
            com.tkisor.nekojs.core.lifecycle.CandidateDomainCollector.Handle handle) {
        if (context == null) {
            return;
        }
        Candidate existing = CANDIDATE_BATCHES.get(context);
        Candidate batch = existing != null ? existing : new Candidate(context);
        CANDIDATE_BATCHES.put(context, batch);
        // 与执行期首个注册同款剪枝：地图里最多留当前候选一个批次。失败候选（尤其本代
        // 无注册、只经本方法建批次的空批次）不会走到 publish() 的 remove，若这里不剪，
        // 它会以已销毁的 Context 为 key 永久滞留。
        // ponytail: 单候选假设——CLIENT reload 由 ScriptManager 实例锁与 ClientReloadExecutor 串行化。
        Candidate current = batch;
        CANDIDATE_BATCHES.values().removeIf(other -> other != current);
        if (!current.registered) {
            // 先置位再注册：registerPlan 抛出时整批候选已经失败，不再重试同一批次。
            current.registered = true;
            handle.registerPlan(current);
        }
    }

    /**
     * 认领生产表所属的 generation：首个来自新 Context 的注册换装整表。初始加载与击杀重建
     * 各有一个新 Context（脚本以同一批来源重跑）；单文件 reload 复用当前 active Context，
     * 因此不会清掉同代其它渲染器。候选 commit 由 {@link Candidate#publish()} 设置同一身份。
     *
     * <p>换装与渲染分发都在客户端 owner thread（F3+T 资源 reload 与
     * {@code ClientReloadExecutor} 都转投 Render 线程），故 clear + put 之间无并发读。
     */
    private static void beginActiveGeneration(Context context) {
        if (context == null || context.equals(activeContext)) {
            return;
        }
        HUD_RENDERERS.clear();
        WORLD_RENDERERS.clear();
        activeContext = context;
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

    /**
     * 一个候选 generation 收集到的渲染器注册（inert：只按 id 记账，不触碰生产表）。
     *
     * <p>只挂在候选的 {@code CandidateStatePlan} 联合边界上：preflight 阶段无可拒绝条件
     * （payload 校验在 {@code RenderRegistrationBusJS#execute} 的收集点完成，非法 id/layer/
     * callback 当场失败），故它只为 commit 点服务；失败或取消时随候选一起丢弃，旧 active
     * 渲染器不受影响（票 26 AC4）。
     */
    private static final class Candidate implements CandidateStatePlan {

        private final Context context;

        /** 是否已挂上候选的联合边界（执行期首个注册或 DOMAIN_PLAN 收集二选一，不重复）。 */
        private boolean registered;
        private final Map<String, Entry> hud = new LinkedHashMap<>();
        private final Map<String, Entry> world = new LinkedHashMap<>();

        Candidate(Context context) {
            this.context = context;
        }

        void add(Kind kind, String id, Entry entry) {
            (kind == Kind.HUD ? hud : world).put(id, entry);
        }

        @Override
        public String domain() {
            return CANDIDATE_DOMAIN;
        }

        /** 本批无候选期可拒绝条件（见类注释）：只为参与 commit 点换装。 */
        @Override
        public void preflight() {
        }

        /**
         * commit 点（owner thread）：整批换装生产表。旧 generation 中本批未声明的 id 随
         * clear 一并退役，不再有陈旧渲染器；契约要求 publish 不抛出（两个批量操作都不抛）。
         */
        @Override
        public void publish() {
            HUD_RENDERERS.clear();
            HUD_RENDERERS.putAll(hud);
            WORLD_RENDERERS.clear();
            WORLD_RENDERERS.putAll(world);
            activeContext = context;
            CANDIDATE_BATCHES.remove(context, this);
        }
    }
}