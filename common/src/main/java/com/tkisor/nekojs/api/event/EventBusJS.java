package com.tkisor.nekojs.api.event;

import com.tkisor.nekojs.probe.backend.typescript.TypeScriptProbeBackend;
import com.tkisor.nekojs.NekoJS;
import com.tkisor.nekojs.api.ScriptType;
import com.tkisor.nekojs.script.ScriptContextRegistry;
import com.tkisor.nekojs.script.ScriptManager;
import com.tkisor.nekojs.api.event.CancellableEventBus;
import com.tkisor.nekojs.api.event.EventBus;
import com.tkisor.nekojs.api.event.EventListenerToken;
import com.tkisor.nekojs.api.event.DispatchCancellableEventBus;
import com.tkisor.nekojs.api.event.DispatchEventBus;
import com.tkisor.nekojs.api.event.DispatchKey;
import com.tkisor.nekojs.eventbus.CommonPriority;
import graal.graalvm.polyglot.Context;
import graal.graalvm.polyglot.Value;
import graal.graalvm.polyglot.proxy.ProxyExecutable;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Predicate;

/**
 * JS 侧事件总线句柄：包装 {@link EventBus} 并实现 {@link ProxyExecutable}，
 * 脚本通过直接调用总线对象（形如 {@code ServerEvents.tickPre(...)}）注册监听器。
 *
 * <p>监听器以 Graal {@link Value} 透传保存，在 post 事件的线程上执行（通常为游戏主线程）。
 * 每个监听器 token 按注册脚本的 {@link ScriptType} 与 scriptId 记账，供脚本 reload 时
 * 按类型或按脚本反注册。可取消总线的监听器返回 {@code true} 表示取消事件；dispatch
 * 总线按 key 定向分发。
 *
 * @author ZZZank
 */
public class EventBusJS<EVENT, KEY> implements ProxyExecutable {
    private static Predicate<Class<?>> externalCancellabilityPredicate = c -> false;

    /**
     * 设置外部可取消性判定，供 {@link #of(Class)} 工厂决定是否默认创建可取消总线。
     * 传 {@code null} 等价于「一律不可取消」。
     */
    public static void setExternalCancellabilityPredicate(Predicate<Class<?>> predicate) {
        externalCancellabilityPredicate = predicate == null ? c -> false : predicate;
    }

    /** 创建事件总线，可取消性由 {@link #eventCancellability(Class)} 判定。 */
    public static <E, K> EventBusJS<E, K> of(Class<E> eventType) {
        return of(eventType, eventCancellability(eventType));
    }

    /** 创建事件总线，显式指定是否可取消（不支持按 key 分发）。 */
    public static <E, K> EventBusJS<E, K> of(Class<E> eventType, boolean cancellable) {
        return of(eventType, cancellable, null);
    }

    /**
     * 创建事件总线，显式指定是否可取消与可选的分发 key 描述。
     * 提供 {@code dispatchKey} 时返回可按 key 定向分发的总线，post 时须携带 key。
     */
    public static <E, K> EventBusJS<E, K> of(
        Class<E> eventType,
        boolean cancellable,
        @Nullable DispatchKey<E, K> dispatchKey
    ) {
        EventBus<E> bus;
        if (cancellable) {
            bus = dispatchKey != null
                ? DispatchCancellableEventBus.create(eventType, dispatchKey)
                : CancellableEventBus.create(eventType);
        } else {
            bus = dispatchKey != null
                ? DispatchEventBus.create(eventType, dispatchKey)
                : EventBus.create(eventType);
        }
        return new EventBusJS<>(bus);
    }

    /** 查询事件类当前的可取消性（未设置外部 predicate 时一律不可取消）。 */
    public static boolean eventCancellability(Class<?> c) {
        return externalCancellabilityPredicate.test(c);
    }

    private final EventBus<EVENT> bus;
    /**
     * 按 ScriptType 分桶的 JS 侧监听器镜像。注册（脚本加载线程）与 {@link #hasListeners()}
     * 迭代（probe 等）可能并发，必须用并发 Map；内层 List 用 CopyOnWriteArrayList
     * （读多写少，见 {@link #execute}）。此前的非同步 EnumMap 存在并发写丢失 / 迭代
     * 期间结构修改的问题。
     */
    private final Map<ScriptType, List<ScriptEventListenerToken<EVENT>>> tokensByType;
    /**
     * Script type this bus was registered for. Used ONLY as an immutability guard
     * (see {@link #scriptType(ScriptType)}) — it is intentionally NOT used for
     * listener filtering or isolation. Listener tokens are bucketed by the
     * registering script's ScriptType in {@link #tokensByType}, not by this field.
     * (DEAD-5: do not repurpose this for filtering without auditing the bucketing.)
     */
    private ScriptType scriptType;
    private String groupName;
    private String eventName;

    /** 以底层 bus 构建句柄；{@code bus} 不可为 {@code null}。 */
    public EventBusJS(EventBus<EVENT> bus) {
        this.bus = Objects.requireNonNull(bus);
        this.tokensByType = new ConcurrentHashMap<>();
    }

    /** 底层总线是否可取消（监听器返回 {@code true} 即取消事件）。 */
    public boolean canCancel() {
        return bus instanceof CancellableEventBus<?>;
    }

    /** 底层总线是否支持按 key 定向分发（即 {@link #post(Object, Object)} 可用）。 */
    public boolean canDispatch() {
        return bus instanceof DispatchEventBus<?, ?>;
    }

    /**
     * 是否有至少一个监听器已注册。供 probe 等高开销事件发射器在无监听器时跳过
     * 事件对象构建与 IR 反射（见 {@code TypeScriptProbeBackend} 的「仅有监听器时构建 IR」策略）。
     *
     * <p>{@link #tokensByType} 是 JS 侧注册的镜像；为防绕过 {@link #execute} 的直接 Java 注册
     * （如测试、bridge 代码）失同步，再兜底检查底层 bus 是否为空。
     */
    public boolean hasListeners() {
        for (List<ScriptEventListenerToken<EVENT>> list : tokensByType.values()) {
            if (!list.isEmpty()) return true;
        }
        return bus instanceof com.tkisor.nekojs.eventbus.EventBusBase<?, ?> base && !base.isEmpty();
    }

    /** 底层 Java 侧总线，供平台 bridge 直接注册/反注册 Java 监听器（绕过 JS 记账）。 */
    public EventBus<EVENT> bus() {
        return bus;
    }

    /** 本总线承载的事件类型。 */
    public Class<EVENT> eventType() {
        return bus.eventType();
    }

    /** 设置组名/事件名元数据；由 {@link EventGroup#add} 在注册时调用，脚本侧不应手动改动。 */
    public void metadata(String groupName, String eventName) {
        this.groupName = groupName;
        this.eventName = eventName;
    }

    /** 事件组名（如 "PlayerEvents"），由 {@link EventGroup#add} 在注册时设置。 */
    public String groupName() {
        return groupName;
    }

    /** 事件名（如 "chat"），由 {@link EventGroup#add} 在注册时设置。 */
    public String eventName() {
        return eventName;
    }

    /** 注册时绑定的 {@link ScriptType}；未绑定前为 {@code null}。仅作不可变守卫，不用于监听器过滤。 */
    public ScriptType scriptType() {
        return scriptType;
    }

    /**
     * 绑定 {@link ScriptType}；对已绑定的总线绑定不同值会抛 {@link IllegalStateException}
     * （同一总线不得跨 ScriptType 复用）。见 {@link #scriptType} 字段注释：该值不参与监听器分桶。
     */
    public void scriptType(ScriptType scriptType) {
        if (this.scriptType != null && this.scriptType != scriptType) {
            throw new IllegalStateException("Event bus script type is already " + this.scriptType + ": " + bus.eventType().getName());
        }
        this.scriptType = Objects.requireNonNull(scriptType, "scriptType");
    }

    /** 反注册指定 {@link ScriptType} 下已注册的全部 JS 监听器（脚本 reload 清理用）。 */
    public void clearTokens(ScriptType type) {
        List<ScriptEventListenerToken<EVENT>> tokens = tokensByType.remove(type);
        if (tokens == null) return;
        for (var token : tokens) {
            bus.unregister(token.token());
        }
    }

    /** 反注册指定 {@link ScriptType} 下、由指定 {@code scriptId} 注册的 JS 监听器；{@code scriptId} 为 {@code null} 或空白时不做任何事。 */
    public void clearTokens(ScriptType type, String scriptId) {
        if (scriptId == null || scriptId.isBlank()) return;
        // 空桶移除必须与注册（computeIfAbsent + add）对同一 key 原子：ConcurrentHashMap.compute
        // 持有 bin 锁，computeIfAbsent 同 key 无法插入执行。若像此前那样「removeIf 判空后再
        // remove(type)」，并发注册可能恰好把新 token 加入正被移除的列表：底层 bus.listen 仍生效
        // 而镜像丢失该条目，之后 clearTokens(type) 永远无法再反注册它 → 监听器永久泄漏。
        List<ScriptEventListenerToken<EVENT>> removed = new ArrayList<>();
        tokensByType.compute(type, (ignored, tokens) -> {
            if (tokens == null) return null;
            List<ScriptEventListenerToken<EVENT>> kept = null;
            for (ScriptEventListenerToken<EVENT> token : tokens) {
                if (scriptId.equals(token.scriptId())) {
                    removed.add(token);
                } else {
                    if (kept == null) kept = new ArrayList<>();
                    kept.add(token);
                }
            }
            if (removed.isEmpty()) return tokens; // 无匹配：保持原列表不动
            // 返回 null 即原子移除空桶；非空则换成新的 CopyOnWriteArrayList（保留并发读语义）
            return kept == null ? null : new CopyOnWriteArrayList<>(kept);
        });
        // bus.unregister 必须在 compute 之外执行：不得在持有 map bin 锁时产生
        // 可能重入本 map（或 bus）的副作用
        for (ScriptEventListenerToken<EVENT> token : removed) {
            bus.unregister(token.token());
        }
    }

    /**
     * 按 scriptId 前缀反注册监听器：清理所有 id 以 {@code scriptIdPrefix} 开头的脚本注册的
     * JS 监听器。用于脚本包（尤其 WORLD 包）整体卸载——包内脚本 id 统一携带
     * {@code packs/<id>/} / {@code worldpacks/<id>/} 前缀（见 ScriptPack#idPathPrefix）。
     * 并发约束与 {@link #clearTokens(ScriptType, String)} 相同（compute 原子分桶）。
     */
    public void clearTokensByPrefix(ScriptType type, String scriptIdPrefix) {
        if (scriptIdPrefix == null || scriptIdPrefix.isBlank()) return;
        List<ScriptEventListenerToken<EVENT>> removed = new ArrayList<>();
        tokensByType.compute(type, (ignored, tokens) -> {
            if (tokens == null) return null;
            List<ScriptEventListenerToken<EVENT>> kept = null;
            for (ScriptEventListenerToken<EVENT> token : tokens) {
                if (token.scriptId() != null && token.scriptId().startsWith(scriptIdPrefix)) {
                    removed.add(token);
                } else {
                    if (kept == null) kept = new ArrayList<>();
                    kept.add(token);
                }
            }
            if (removed.isEmpty()) return tokens;
            return kept == null ? null : new CopyOnWriteArrayList<>(kept);
        });
        for (ScriptEventListenerToken<EVENT> token : removed) {
            bus.unregister(token.token());
        }
    }

    /**
     * 向总线投递事件。监听器抛出的异常会被捕获并经 ScriptErrorReporter 记录，
     * 不中断其它监听器；返回事件是否被取消（不可取消总线恒为 {@code false}）。
     */
    public boolean post(EVENT event) {
        try {
            return this.bus.post(event);
        } catch (Exception e) {
            NekoJS.LOGGER.error("Error during CancellableEventBus execution", e);
            return false;
        }
    }

    // bus 的运行时类型由 of() 工厂按 dispatchKey 决定，canDispatch() 已保证是 DispatchEventBus；
    // 此处只是擦除层面的泛型收窄
    /**
     * 按 key 向 dispatch 总线投递事件；总线不支持定向分发时抛 {@link IllegalStateException}。
     * 返回事件是否被取消；监听器异常同 {@link #post(Object)} 被捕获记录。
     */
    @SuppressWarnings("unchecked")
    public boolean post(EVENT event, KEY key) {
        if (canDispatch()) {
            try {
                return ((DispatchEventBus<EVENT, KEY>) bus).post(event, key);
            } catch (Exception e) {
                NekoJS.LOGGER.error("Error during EventBus execution", e);
            }
            return false;
        }
        throw new IllegalStateException("This bus is not dispatchable");
    }

    /** 已注册监听的定向 key 集合；非 dispatch 总线返回空集。 */
    @SuppressWarnings("unchecked")
    public Set<KEY> registeredKeys() {
        if (canDispatch()) {
            return ((DispatchEventBus<EVENT, KEY>) bus).registeredKeys();
        }
        return Set.of();
    }

    /**
     * {@link ProxyExecutable} 入口：脚本直接调用总线对象即注册监听器，注册成功返回 {@code true}。
     * 支持的调用形态：
     * <ul>
     *   <li>{@code bus(listener)} —— 普通监听，NORMAL 优先级</li>
     *   <li>{@code bus(priority, listener)} —— 首参为优先级名
     *       （HIGHEST/HIGH/NORMAL/LOW/LOWEST，大小写不敏感）</li>
     *   <li>{@code bus(key, listener)} / {@code bus(priority, key, listener)} ——
     *       dispatch 总线按 key 定向注册</li>
     * </ul>
     * 可取消总线的监听器返回 {@code true} 表示取消事件；参数形态非法（缺 listener 等）抛
     * {@link IllegalArgumentException}。监听器随所属脚本 reload 自动反注册。
     *
     * <p>candidate generation（工单 06）：注册来源 Context 属于构建中的候选环境时，
     * 注册<strong>不</strong>立即挂上底层 bus（生产路由 commit 前不可见），而是作为
     * {@link PendingListener} 交给所属 {@code ScriptManager} 收集；候选全部阶段通过后
     * 在 commit 点统一 {@link PendingListener#activate()}。候选失败时挂起注册随候选
     * generation 一并丢弃，active 监听器不受影响。
     */
    @Override
    public Object execute(Value... args) {
        if (args.length == 0) {
            throw new IllegalArgumentException("EventBus requires at least one arg");
        }
        if (canDispatch() && args.length == 1 && parsePriority(args[0]) != null) {
            // listen("HIGH") on a dispatch bus must keep the legacy missing-listener error
            // instead of treating "HIGH" as a key with no listener.
            throw new IllegalArgumentException("EventBus requires a listener after priority");
        }

        // DEFECT-D6: optional priority as the first argument. args[0] is parsed as a
        // priority name (HIGHEST/HIGH/NORMAL/LOW/LOWEST, case-insensitive) only when
        // either the bus is NOT dispatchable, or when a key + listener still follow
        // after the priority (>= 3 args). Call shapes:
        //   listen(listener)                  -> NORMAL
        //   listen("key", listener)           -> NORMAL (dispatch), "key" is NOT a priority name
        //   listen("HIGH", listener)          -> non-dispatch: HIGH priority;
        //                                       dispatch: key "HIGH", NORMAL priority
        //   listen("HIGH", "key", listener)   -> HIGH priority (dispatch)
        byte priority = CommonPriority.NORMAL;
        int offset = priorityArgOffset(args, canDispatch());
        if (offset > 0) {
            priority = parsePriority(args[0]);
            if (args.length <= offset) {
                throw new IllegalArgumentException("EventBus requires a listener after priority");
            }
        }

        Value[] rest = new Value[args.length - offset];
        System.arraycopy(args, offset, rest, 0, rest.length);

        Value listener;
        Value key = null;
        if (canDispatch()) {
            boolean keyed = rest.length > 1;
            listener = keyed ? rest[1] : rest[0];
            if (keyed) {
                key = rest[0];
            }
        } else {
            listener = rest[0];
        }

        ScriptType type = ScriptContextRegistry.scriptTypeOf(listener.getContext());
        String scriptId = ScriptContextRegistry.currentScriptIdOf(listener.getContext());
        PendingListener pending = new PendingListener(this, priority, listener, key, type, scriptId);
        if (ScriptManager.collectPendingListener(listener.getContext(), pending)) {
            // candidate generation：挂起注册，commit 时激活（见类注释）
            return true;
        }
        pending.activate();
        return true;
    }

    /**
     * Decide whether {@code args[0]} should be consumed as a priority name.
     *
     * <p>On a non-dispatch bus any leading priority-name string keeps its legacy
     * meaning. On a dispatch bus a leading string is only a priority when there are
     * still at least two arguments after it (key + listener); otherwise it is the
     * dispatch key itself — so {@code listen("HIGH", listener)} registers key
     * {@code "HIGH"} instead of misreading {@code "HIGH"} as a priority.
     */
    static int priorityArgOffset(Value[] args, boolean dispatchable) {
        if (args.length == 0 || parsePriority(args[0]) == null) {
            return 0;
        }
        if (!dispatchable) {
            return 1;
        }
        return args.length >= 3 ? 1 : 0;
    }

    /**
     * Parse a JS value as a {@link CommonPriority} name. Returns {@code null} when
     * the value is not a string or does not match a priority name, so callers can
     * distinguish "not a priority" (e.g. a dispatch key string) from a valid name.
     * Matching is case-insensitive.
     */
    private static Byte parsePriority(Value value) {
        if (value == null || !value.isString()) return null;
        String name = value.asString().trim().toUpperCase(Locale.ROOT);
        return switch (name) {
            case "HIGHEST" -> CommonPriority.HIGHEST;
            case "HIGH" -> CommonPriority.HIGH;
            case "NORMAL" -> CommonPriority.NORMAL;
            case "LOW" -> CommonPriority.LOW;
            case "LOWEST" -> CommonPriority.LOWEST;
            default -> null;
        };
    }

    /**
     * 一次脚本监听器注册的完整描述：candidate phase 由 {@code ScriptManager} 收集，
     * commit 点统一 {@link #activate()}。挂起状态不触碰底层 bus 与 type 分桶 mirror，
     * 因此 candidate 监听器对生产路由完全不可见（{@link #hasListeners()} 为 false）。
     *
     * <p>审查 A1：commit 点必须不可失败——{@link #activate()} 期间会抛的工作
     * （dispatch key 的 {@code Value.as(keyType)} 转换）经 {@link #prepareForActivation()}
     * 前移到候选阶段完成，候选期失败因此走候选丢弃路径而不是留下半激活 generation。
     */
    public static final class PendingListener {
        private final EventBusJS<?, ?> owner;
        private final byte priority;
        private final Value listener;
        private final Value key;
        private final ScriptType type;
        private final String scriptId;

        /** {@link #prepareForActivation()} 解析出的可直接挂载 key（未预备时为 null）。 */
        private Object resolvedKey;
        /** 是否已预备：true 时 {@link #activate()} 不再做任何 Value → Java key 转换。 */
        private boolean prepared;

        PendingListener(EventBusJS<?, ?> owner, byte priority, Value listener, Value key,
                        ScriptType type, String scriptId) {
            this.owner = owner;
            this.priority = priority;
            this.listener = listener;
            this.key = key;
            this.type = type;
            this.scriptId = scriptId;
        }

        /** 所属总线（候选收集派发按总线筛出本候选的挂起注册，票 39）。 */
        public EventBusJS<?, ?> owner() {
            return owner;
        }

        /** 注册优先级（候选收集派发按 priority 稳定排序，与 EventBusBase 编译快照同序）。 */
        public byte priority() {
            return priority;
        }

        /**
         * 候选收集派发（票 39 DOMAIN_PLAN 阶段）：在当前线程（owner thread）直接执行
         * 监听器回调——与生产分发同款 scriptId 切换 + 回调深度标记，但异常<b>向上传播</b>
         * （调用方让候选失败或记进领域计划），不走「错误记录不失败」的生产语义。
         * 不触碰底层 bus 与 mirror（挂起注册保持未激活）。
         */
        public void executeForCollection(Object event) {
            Context context = listener.getContext();
            String previousScriptId = ScriptContextRegistry.switchCurrentScriptId(context, scriptId);
            ScriptManager.noteCallbackEnter();
            try {
                listener.executeVoid(event);
            } finally {
                ScriptManager.noteCallbackExit();
                ScriptContextRegistry.restoreCurrentScriptId(context, previousScriptId);
            }
        }

        /** 注册来源的 {@link ScriptType}（dispatch mirror 分桶键）。 */
        public ScriptType type() {
            return type;
        }

        /** 注册来源脚本 id（mirror 记账与按脚本清理用；也是候选期失败结果的 source 归因键）。 */
        public String scriptId() {
            return scriptId;
        }

        /**
         * 候选期预备：把 commit 点会抛的工作（dispatch key → Java key 的转换）前移。
         *
         * <p>必须在 commit 之前、且<strong>不在 JS 调用帧内</strong>调用（本仓库由
         * {@code ScriptManager} 的候选 EVENT_PLAN 阶段调用）：转换失败时异常直接冒泡到
         * 事务式 reload 的候选失败路径，不会被 {@code ScriptExecutor.executeEntry} 的
         * 「脚本级错误不失败 reload」语义吞掉。幂等；非候选注册路径不调用，保持
         * {@link #activate()} 内的原位转换（行为不变）。
         */
        public void prepareForActivation() {
            owner.preparePending(this);
        }

        /**
         * 激活：构建分发闭包、挂上底层 bus 并记入 type 分桶 mirror。
         * 只在 commit 点（或非候选注册路径）调用；重复 activate 会造成双重注册。
         *
         * <p>已预备（{@link #prepareForActivation()}）的挂起注册在此<strong>不会</strong>
         * 抛：key 转换已在候选期完成，剩余操作只有 {@code bus.listen(...)}（CopyOnWriteArrayList
         * 添加 + 编译快照失效）与 mirror 的 {@code ConcurrentHashMap.compute}，二者均无
         * 条件性抛出路径。
         */
        public void activate() {
            owner.activatePending(this);
        }
    }

    /**
     * 候选期预备（见 {@link PendingListener#prepareForActivation()}）：只解析 dispatch key，
     * 不触碰底层 bus 与 mirror（候选监听器在 commit 前对生产路由仍完全不可见）。
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private void preparePending(PendingListener pending) {
        if (pending.prepared) return;
        Object resolved = null;
        if (canDispatch() && pending.key != null) {
            var dispatchBus = (DispatchEventBus<EVENT, KEY>) this.bus;
            // 非法/不可转换的 key 在此抛出（候选期）：ClassCastException / 转换失败
            resolved = pending.key.as(dispatchBus.dispatchKey().keyType());
        }
        pending.resolvedKey = resolved;
        pending.prepared = true;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void activatePending(PendingListener pending) {
        EventBusJS<EVENT, KEY> self = (EventBusJS<EVENT, KEY>) this;
        byte priority = pending.priority;
        Value listener = pending.listener;
        boolean cancellable = canCancel();
        boolean dispatch = canDispatch();
        EventListenerToken<EVENT> token;
        if (dispatch) {
            var dispatchBus = (DispatchEventBus<EVENT, KEY>) this.bus;
            // 已预备：用候选期解析好的 key（commit 点不可再抛，审查 A1）；未预备（非候选注册
            // 路径）：保持原位转换，行为与改造前一致。
            KEY dispatchKey = pending.prepared
                    ? (KEY) pending.resolvedKey
                    : (pending.key == null ? null : pending.key.as(dispatchBus.dispatchKey().keyType()));
            if (cancellable) {
                token = dispatchKey != null
                        ? self.registerDispatchCancellable(priority, listener, dispatchKey)
                        : self.registerCancellable(priority, listener);
            } else {
                token = dispatchKey != null
                        ? self.registerDispatch(priority, listener, dispatchKey)
                        : self.register(priority, listener);
            }
        } else if (cancellable) {
            token = self.registerCancellable(priority, listener);
        } else {
            token = self.register(priority, listener);
        }
        // Inner list is CopyOnWriteArrayList: read-heavy (post iterates tokens via the
        // compiled bus) / write-rare (register on script load, clear on reload). Matches
        // the EventBusBase pattern and survives concurrent reload+post without CME.
        // 注册整体放在 compute 内（与 clearTokens 的 compute 互斥于同一 bin 锁）：若沿用
        // computeIfAbsent(...).add(...) 的两步写，computeIfAbsent 返回列表后、add 执行前，
        // 并发 clearTokens 可能已把该列表整体替换/移除，add 落在孤儿列表上 → 镜像丢条目、
        // 底层监听器泄漏。lambda 内只做列表添加，不产生 map/bus 副作用。
        this.tokensByType.compute(pending.type, (ignored, tokens) -> {
            List<ScriptEventListenerToken<EVENT>> list =
                    tokens == null ? new CopyOnWriteArrayList<>() : tokens;
            list.add(new ScriptEventListenerToken<>(token, pending.scriptId));
            return list;
        });
    }

    private EventListenerToken<EVENT> register(byte priority, Value listener) {
        Context context = listener.getContext();
        ScriptType type = ScriptContextRegistry.scriptTypeOf(context);
        String scriptId = ScriptContextRegistry.currentScriptIdOf(context);
        // 【票 07 尾注：Context 私有 monitor 旧路线的删除】本类四个分发点曾以
        // synchronized(context) 序列化「命令线程 reload / Render-tick 分发」的跨线程
        // 并发（Graal 单线程约束撞 Multi threaded access）。票 07 起：lifecycle 的
        // 串行改由 ScriptManager 的实例锁 + ScriptLifecycleGate 承担，CLIENT reload
        // 命令面已转投 Render 线程（ClientReloadExecutor）、网络 receiver 先 hop 再
        // 分发——分发与 lifecycle 在 owner thread 上同线程串行，分发点不再取
        // Context monitor（每次回调省一次 monitor 进入，也不再与 reload 竞争同一把锁）。
        // 残余角落：非 owner 线程绕过显式调度入口直接执行 lifecycle（违反 AC4 契约）
        // 且恰逢 owner 分发时，Graal 会拒绝后进入者并以回调错误呈现（探针
        // SyncEvalWatchdogTest.concurrentEvalOnSameContextIsRejectedByGraal）——
        // 单线程约束本身仍是同一 Context 并发进入的最终兜底。

        return this.bus.listen(priority, event -> {
            if (ScriptManager.isContextDead(context)) {
                // Context 已被 Graal 关闭（语句上限等）：监听器闭包指向死环境，跳过分发，
                // 避免每次事件都在死 Context 上抛错刷屏；所属 ScriptManager 会在下次取用时重建并清空监听器
                return;
            }
            try {
                // 票 07 线程契约：生产分发的序列化不再走 Context 私有 monitor（旧路线已删，
                // 见 register() 尾注）——分发只发生在 owner thread（SERVER=tick、CLIENT=render、
                // 网络 receiver 先 hop 再分发），与 owner 上的 lifecycle 天然同线程串行；
                // 跨线程并发进入由 Graal 单线程约束兜底（探针：concurrent eval 被拒绝）。
                // 回调执行体做回调深度标记：期间同线程的 lifecycle 请求被调度门明确拒绝
                // （不递归、不同步等待自身）。
                String previousScriptId = ScriptContextRegistry.switchCurrentScriptId(context, scriptId);
                ScriptManager.noteCallbackEnter();
                try {
                    listener.executeVoid(event);
                } finally {
                    ScriptManager.noteCallbackExit();
                    ScriptContextRegistry.restoreCurrentScriptId(context, previousScriptId);
                }
            } catch (Throwable e) {
                if (e instanceof InterruptedException) Thread.currentThread().interrupt();
                if (e instanceof Error) throw (Error) e;
                // 语句上限关闭 Context 的 kill 在稳态下只能从回调路径发现（入口早已执行完）：
                // 上报所属 ScriptManager，使其在下次取用时自动重建环境，而不是静默死亡
                ScriptManager.reportContextKilled(context, e);
                recordListenerError(type, scriptId, "normal", null, event, e);
            }
        });
    }

    private EventListenerToken<EVENT> registerCancellable(byte priority, Value listener) {
        Context context = listener.getContext();
        ScriptType type = ScriptContextRegistry.scriptTypeOf(context);
        String scriptId = ScriptContextRegistry.currentScriptIdOf(context);
        var bus = (CancellableEventBus<EVENT>) this.bus;

        return bus.listen(priority, event -> {
            if (ScriptManager.isContextDead(context)) {
                // Context 已被 Graal 关闭（语句上限等）：跳过分发，避免每次事件在死环境上报错刷屏
                return false;
            }
            try {
                // 票 07 线程契约：同 register()——owner-thread 分发 + 回调深度标记，
                // Context 私有 monitor 旧路线已删（见 register() 尾注）。
                String previousScriptId = ScriptContextRegistry.switchCurrentScriptId(context, scriptId);
                ScriptManager.noteCallbackEnter();
                try {
                    Value result = listener.execute(event);
                    return result.isBoolean() && result.asBoolean();
                } finally {
                    ScriptManager.noteCallbackExit();
                    ScriptContextRegistry.restoreCurrentScriptId(context, previousScriptId);
                }
            } catch (Throwable e) {
                if (e instanceof InterruptedException) Thread.currentThread().interrupt();
                if (e instanceof Error) throw (Error) e;
                ScriptManager.reportContextKilled(context, e);
                recordListenerError(type, scriptId, "cancellable", null, event, e);
            }
            return false; // 出错时默认不取消事件
        });
    }

    private <K> EventListenerToken<EVENT> registerDispatch(byte priority, Value listener, K key) {
        Context context = listener.getContext();
        ScriptType type = ScriptContextRegistry.scriptTypeOf(context);
        String scriptId = ScriptContextRegistry.currentScriptIdOf(context);
        var bus = (DispatchEventBus<EVENT, K>) this.bus;

        return bus.listen(
                key,
                priority,
                event -> {
                    if (ScriptManager.isContextDead(context)) {
                        // Context 已被 Graal 关闭（语句上限等）：跳过分发，避免每次事件在死环境上报错刷屏
                        return;
                    }
                    try {
                        // 票 07 线程契约：同 register()——owner-thread 分发 + 回调深度标记，
                        // Context 私有 monitor 旧路线已删（见 register() 尾注）。
                        String previousScriptId = ScriptContextRegistry.switchCurrentScriptId(context, scriptId);
                        ScriptManager.noteCallbackEnter();
                        try {
                            if (listener.canExecute()) {
                                listener.executeVoid(event);
                            }
                        } finally {
                            ScriptManager.noteCallbackExit();
                            ScriptContextRegistry.restoreCurrentScriptId(context, previousScriptId);
                        }
                    } catch (Throwable e) {
                        if (e instanceof InterruptedException) Thread.currentThread().interrupt();
                        if (e instanceof Error) throw (Error) e;
                        ScriptManager.reportContextKilled(context, e);
                        recordListenerError(type, scriptId, "dispatch", key, event, e);
                    }
                }
        );
    }

    private <K> EventListenerToken<EVENT> registerDispatchCancellable(byte priority, Value listener, K key) {
        Context context = listener.getContext();
        ScriptType type = ScriptContextRegistry.scriptTypeOf(context);
        String scriptId = ScriptContextRegistry.currentScriptIdOf(context);
        var bus = (DispatchCancellableEventBus<EVENT, K>) this.bus;

        return bus.listen(
                key,
                priority,
                event -> {
                    if (ScriptManager.isContextDead(context)) {
                        // Context 已被 Graal 关闭（语句上限等）：跳过分发，避免每次事件在死环境上报错刷屏
                        return false;
                    }
                    try {
                        // 票 07 线程契约：同 register()——owner-thread 分发 + 回调深度标记，
                        // Context 私有 monitor 旧路线已删（见 register() 尾注）。
                        String previousScriptId = ScriptContextRegistry.switchCurrentScriptId(context, scriptId);
                        ScriptManager.noteCallbackEnter();
                        try {
                            if (listener.canExecute()) {
                                Value result = listener.execute(event);
                                return result.isBoolean() && result.asBoolean();
                            }
                        } finally {
                            ScriptManager.noteCallbackExit();
                            ScriptContextRegistry.restoreCurrentScriptId(context, previousScriptId);
                        }
                    } catch (Throwable e) {
                        if (e instanceof InterruptedException) Thread.currentThread().interrupt();
                        if (e instanceof Error) throw (Error) e;
                        ScriptManager.reportContextKilled(context, e);
                        recordListenerError(type, scriptId, "dispatchCancellable", key, event, e);
                    }
                    return false; // 出错时默认不取消事件
                }
        );
    }

    private void recordListenerError(ScriptType type, String scriptId, String mode, Object dispatchKey, EVENT event, Throwable throwable) {
        String eventClass = event == null ? "null" : event.getClass().getName();
        String keyText = dispatchKey == null ? "" : " key=" + dispatchKey;
        String kind = "event mode=" + mode
                + " bus=" + bus.eventType().getName()
                + " event=" + eventClass
                + " script=" + (scriptId == null || scriptId.isBlank() ? "unknown" : scriptId)
                + " thread=" + Thread.currentThread().getName()
                + keyText;
        ScriptErrorReporter.recordCallbackError(type, kind, throwable);
    }

    private record ScriptEventListenerToken<EVENT>(EventListenerToken<EVENT> token, String scriptId) {}
}
