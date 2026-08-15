package com.tkisor.nekojs.api.event;

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
import com.tkisor.nekojs.eventbus.EventBusFactory;
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
 * @author ZZZank
 */
public class EventBusJS<EVENT, KEY> implements ProxyExecutable {
    private static Predicate<Class<?>> externalCancellabilityPredicate = c -> false;

    public static void setExternalCancellabilityPredicate(Predicate<Class<?>> predicate) {
        externalCancellabilityPredicate = predicate == null ? c -> false : predicate;
    }

    public static <E, K> EventBusJS<E, K> of(Class<E> eventType) {
        return of(eventType, eventCancellability(eventType));
    }

    public static <E, K> EventBusJS<E, K> of(Class<E> eventType, boolean cancellable) {
        return of(eventType, cancellable, null);
    }

    public static <E, K> EventBusJS<E, K> of(
        Class<E> eventType,
        boolean cancellable,
        @Nullable DispatchKey<E, K> dispatchKey
    ) {
        EventBus<E> bus;
        if (cancellable) {
            bus = dispatchKey != null
                ? EventBusFactory.createDispatchCancellableEventBus(eventType, dispatchKey)
                : EventBusFactory.createCancellableEventBus(eventType);
        } else {
            bus = dispatchKey != null
                ? EventBusFactory.createDispatchEventBus(eventType, dispatchKey)
                : EventBusFactory.createEventBus(eventType);
        }
        return new EventBusJS<>(bus);
    }

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

    public EventBusJS(EventBus<EVENT> bus) {
        this.bus = Objects.requireNonNull(bus);
        this.tokensByType = new ConcurrentHashMap<>();
    }

    public boolean canCancel() {
        return bus instanceof CancellableEventBus<?>;
    }

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

    public EventBus<EVENT> bus() {
        return bus;
    }

    public Class<EVENT> eventType() {
        return bus.eventType();
    }

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

    public ScriptType scriptType() {
        return scriptType;
    }

    public void scriptType(ScriptType scriptType) {
        if (this.scriptType != null && this.scriptType != scriptType) {
            throw new IllegalStateException("Event bus script type is already " + this.scriptType + ": " + bus.eventType().getName());
        }
        this.scriptType = Objects.requireNonNull(scriptType, "scriptType");
    }

    public void clearTokens(ScriptType type) {
        List<ScriptEventListenerToken<EVENT>> tokens = tokensByType.remove(type);
        if (tokens == null) return;
        for (var token : tokens) {
            bus.unregister(token.token());
        }
    }

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

        EventListenerToken<EVENT> token;
        Value listener;
        if (canDispatch()) {
            boolean keyed = rest.length > 1;
            listener = keyed ? rest[1] : rest[0];
            if (canCancel()) {
                token = keyed
                    ? registerDispatchCancellable(priority, rest[1], rest[0]) // listen([prio,] "key", (e) => true)
                    : registerCancellable(priority, rest[0]); // listen([prio,] (e) => true)
            } else {
                token = keyed
                    ? registerDispatch(priority, rest[1], rest[0]) // listen([prio,] "key", (e) => {})
                    : register(priority, rest[0]); // listen([prio,] (e) => {})
            }
        } else {
            listener = rest[0];
            if (canCancel()) {
                token = registerCancellable(priority, rest[0]); // listen([prio,] (e) => true)
            } else {
                token = register(priority, rest[0]); // listen([prio,] (e) => {})
            }
        }
        ScriptType type = ScriptContextRegistry.scriptTypeOf(listener.getContext());
        String scriptId = ScriptContextRegistry.currentScriptIdOf(listener.getContext());
        // Inner list is CopyOnWriteArrayList: read-heavy (post iterates tokens via the
        // compiled bus) / write-rare (register on script load, clear on reload). Matches
        // the EventBusBase pattern and survives concurrent reload+post without CME.
        // 注册整体放在 compute 内（与 clearTokens 的 compute 互斥于同一 bin 锁）：若沿用
        // computeIfAbsent(...).add(...) 的两步写，computeIfAbsent 返回列表后、add 执行前，
        // 并发 clearTokens 可能已把该列表整体替换/移除，add 落在孤儿列表上 → 镜像丢条目、
        // 底层监听器泄漏。lambda 内只做列表添加，不产生 map/bus 副作用。
        tokensByType.compute(type, (ignored, tokens) -> {
            List<ScriptEventListenerToken<EVENT>> list =
                    tokens == null ? new CopyOnWriteArrayList<>() : tokens;
            list.add(new ScriptEventListenerToken<>(token, scriptId));
            return list;
        });
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

    private EventListenerToken<EVENT> register(Value listener) {
        return register(CommonPriority.NORMAL, listener);
    }

    private EventListenerToken<EVENT> register(byte priority, Value listener) {
        Context context = listener.getContext();
        ScriptType type = ScriptContextRegistry.scriptTypeOf(context);
        String scriptId = ScriptContextRegistry.currentScriptIdOf(context);

        return this.bus.listen(priority, event -> {
            if (ScriptManager.isContextDead(context)) {
                // Context 已被 Graal 关闭（语句上限等）：监听器闭包指向死环境，跳过分发，
                // 避免每次事件都在死 Context 上抛错刷屏；所属 ScriptManager 会在下次取用时重建并清空监听器
                return;
            }
            try {
                // No synchronized(context): the Graal Context is single-threaded (allowMultiThread
                // is not set), so Graal itself enforces single-thread access. All listener invocations
                // (NeoForge events + timer flushes) run on the game tick thread. The lock was a redundant
                // uncontended monitor enter/exit per event dispatch.
                String previousScriptId = ScriptContextRegistry.switchCurrentScriptId(context, scriptId);
                try {
                    listener.executeVoid(event);
                } finally {
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

    private EventListenerToken<EVENT> registerCancellable(Value listener) {
        return registerCancellable(CommonPriority.NORMAL, listener);
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
                String previousScriptId = ScriptContextRegistry.switchCurrentScriptId(context, scriptId);
                try {
                    Value result = listener.execute(event);
                    return result.isBoolean() && result.asBoolean();
                } finally {
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

    private EventListenerToken<EVENT> registerDispatch(Value listener, Value key) {
        return registerDispatch(CommonPriority.NORMAL, listener, key);
    }

    // bus 的运行时类型由 of() 工厂按 dispatchKey 决定，canDispatch() 已保证是 DispatchEventBus；
    // 此处只是擦除层面的泛型收窄
    @SuppressWarnings("unchecked")
    private EventListenerToken<EVENT> registerDispatch(byte priority, Value listener, Value key) {
        Context context = listener.getContext();
        ScriptType type = ScriptContextRegistry.scriptTypeOf(context);
        String scriptId = ScriptContextRegistry.currentScriptIdOf(context);
        var bus = (DispatchEventBus<EVENT, KEY>) this.bus;
        KEY dispatchKey = key.as(bus.dispatchKey().keyType());

        return bus.listen(
                dispatchKey,
                priority,
                event -> {
                    if (ScriptManager.isContextDead(context)) {
                        // Context 已被 Graal 关闭（语句上限等）：跳过分发，避免每次事件在死环境上报错刷屏
                        return;
                    }
                    try {
                        String previousScriptId = ScriptContextRegistry.switchCurrentScriptId(context, scriptId);
                        try {
                            if (listener.canExecute()) {
                                listener.executeVoid(event);
                            }
                        } finally {
                            ScriptContextRegistry.restoreCurrentScriptId(context, previousScriptId);
                        }
                    } catch (Throwable e) {
                        if (e instanceof InterruptedException) Thread.currentThread().interrupt();
                        if (e instanceof Error) throw (Error) e;
                        ScriptManager.reportContextKilled(context, e);
                        recordListenerError(type, scriptId, "dispatch", dispatchKey, event, e);
                    }
                }
        );
    }

    private EventListenerToken<EVENT> registerDispatchCancellable(Value listener, Value key) {
        return registerDispatchCancellable(CommonPriority.NORMAL, listener, key);
    }

    // bus 的运行时类型由 of() 工厂按 dispatchKey 决定，canDispatch() 已保证是 DispatchCancellableEventBus；
    // 此处只是擦除层面的泛型收窄
    @SuppressWarnings("unchecked")
    private EventListenerToken<EVENT> registerDispatchCancellable(byte priority, Value listener, Value key) {
        Context context = listener.getContext();
        ScriptType type = ScriptContextRegistry.scriptTypeOf(context);
        String scriptId = ScriptContextRegistry.currentScriptIdOf(context);
        var bus = (DispatchCancellableEventBus<EVENT, KEY>) this.bus;
        KEY dispatchKey = key.as(bus.dispatchKey().keyType());

        return bus.listen(
                dispatchKey,
                priority,
                event -> {
                    if (ScriptManager.isContextDead(context)) {
                        // Context 已被 Graal 关闭（语句上限等）：跳过分发，避免每次事件在死环境上报错刷屏
                        return false;
                    }
                    try {
                        String previousScriptId = ScriptContextRegistry.switchCurrentScriptId(context, scriptId);
                        try {
                            if (listener.canExecute()) {
                                Value result = listener.execute(event);
                                return result.isBoolean() && result.asBoolean();
                            }
                        } finally {
                            ScriptContextRegistry.restoreCurrentScriptId(context, previousScriptId);
                        }
                    } catch (Throwable e) {
                        if (e instanceof InterruptedException) Thread.currentThread().interrupt();
                        if (e instanceof Error) throw (Error) e;
                        ScriptManager.reportContextKilled(context, e);
                        recordListenerError(type, scriptId, "dispatchCancellable", dispatchKey, event, e);
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
