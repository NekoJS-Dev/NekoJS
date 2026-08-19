package com.tkisor.nekojs.api.event;

import com.tkisor.nekojs.NekoJS;
import com.tkisor.nekojs.api.ScriptType;
import com.tkisor.nekojs.api.WithScriptType;
import com.tkisor.nekojs.api.event.DispatchKey;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * 具名事件组（如 {@code ServerEvents}）：一组按名注册、暴露给脚本的 {@link EventBusJS}。
 * 引导（bootstrap）阶段注册完毕后应 {@link #freeze()} 冻结，此后禁止再增删总线。
 *
 * @author ZZZank
 */
public class EventGroup {
    /** 创建一个具名空事件组；组名不可为 {@code null}。 */
    public static EventGroup of(String name) {
        return new EventGroup(name);
    }

    private final String name;
    private final Map<String, RegisteredBus> buses;
    private volatile boolean frozen;

    private EventGroup(String name) {
        this.name = Objects.requireNonNull(name);
        // LinkedHashMap：保持注册顺序，viewBuses()/probe 目录迭代确定（HashMap 的
        // String key 迭代序依赖 hashCode 内部布局，跨 JDK/运行脆弱）
        this.buses = new LinkedHashMap<>();
    }

    /** 组名（如 {@code "ServerEvents"}）。 */
    public String name() {
        return name;
    }

    /** 冻结本组；冻结后 {@link #add} 与 {@link #merge} 将抛 {@link IllegalStateException}。 */
    public void freeze() {
        frozen = true;
    }

    /** 组内总线的只读视图（总线名 → {@link BusHolder}），按注册顺序迭代。 */
    public Map<String, BusHolder> viewBuses() {
        return Collections.unmodifiableMap(buses);
    }

    /** 按总线名获取 holder；不存在时返回 {@code null}。 */
    public BusHolder getBusHolder(String busName) {
        return this.buses.get(busName);
    }

    /** 注册 SERVER 脚本可监听的事件总线（不可取消、不分发）。 */
    public <E> EventBusJS<E, Void> server(String name, Class<E> type) {
        return add(name, ScriptType.SERVER, EventBusJS.of(type));
    }

    /** 注册 CLIENT 脚本可监听的事件总线（不可取消、不分发）。 */
    public <E> EventBusJS<E, Void> client(String name, Class<E> type) {
        return add(name, ScriptType.CLIENT, EventBusJS.of(type));
    }

    /** 注册 STARTUP 脚本可监听的事件总线（不可取消、不分发）。 */
    public <E> EventBusJS<E, Void> startup(String name, Class<E> type) {
        return add(name, ScriptType.STARTUP, EventBusJS.of(type));
    }

    /** 注册 SERVER 脚本可监听、按 {@code dispatchKey} 定向分发的事件总线。 */
    public <E, K> EventBusJS<E, K> server(String name, Class<E> type, DispatchKey<E, K> dispatchKey) {
        return add(name, ScriptType.SERVER, EventBusJS.of(type, EventBusJS.eventCancellability(type), dispatchKey));
    }

    /** 注册 CLIENT 脚本可监听、按 {@code dispatchKey} 定向分发的事件总线。 */
    public <E, K> EventBusJS<E, K> client(String name, Class<E> type, DispatchKey<E, K> dispatchKey) {
        return add(name, ScriptType.CLIENT, EventBusJS.of(type, EventBusJS.eventCancellability(type), dispatchKey));
    }

    /** 注册 STARTUP 脚本可监听、按 {@code dispatchKey} 定向分发的事件总线。 */
    public <E, K> EventBusJS<E, K> startup(String name, Class<E> type, DispatchKey<E, K> dispatchKey) {
        return add(name, ScriptType.STARTUP, EventBusJS.of(type, EventBusJS.eventCancellability(type), dispatchKey));
    }

    /**
     * 注册总线：绑定 {@code scriptType} 与组/事件名元数据后收入本组，返回该总线。
     * 组已冻结抛 {@link IllegalStateException}；总线名重复抛 {@link IllegalArgumentException}；
     * 任一参数为 {@code null} 抛 {@link NullPointerException}。
     */
    public <BUS extends EventBusJS<?, ?>> BUS add(String name, ScriptType scriptType, BUS bus) {
        if (frozen) {
            throw new IllegalStateException("EventGroup '" + this.name + "' is frozen after bootstrap");
        }
        Objects.requireNonNull(name, "name == null");
        Objects.requireNonNull(scriptType, "scriptType == null");
        Objects.requireNonNull(bus, "bus == null");
        if (this.buses.containsKey(name)) {
            throw new IllegalArgumentException(String.format("A bus with name '%s' has already been registered", name));
        }

        bus.scriptType(scriptType);
        bus.metadata(this.name, name);
        this.buses.put(name, new RegisteredBus(bus, scriptType));
        return bus;
    }

    /**
     * 合并另一个同名事件组的所有总线到本组（受 {@link #add} 的冻结/重名约束）。
     * 组名不同时仅记录告警并跳过，不复制任何总线。
     */
    public void merge(EventGroup other) {
        if (!this.name.equals(other.name)) {
            // DEFECT-D8: previously this branch silently no-oped, hiding a real
            // misconfiguration (two unrelated groups passed to merge). Warn so the
            // mismatch is observable; no buses are copied on mismatch.
            NekoJS.LOGGER.warn(
                    "EventGroup.merge skipped: name mismatch ('{}' != '{}')",
                    this.name, other.name);
            return;
        }
        other.buses.forEach((busName, registered) -> this.add(busName, registered.scriptType, registered.bus));
    }

    /**
     * 清理本组内可应用于指定 {@link ScriptType} 的所有总线下、该类型已注册的全部 JS 监听器
     * （脚本 reload 清理用）。
     */
    // 清理指定类型的监听器，用于reload scripts，但由于新的eventbus还未熟悉，也许后续会需要调整
    public void clearListeners(ScriptType type) {
        for (var entry : buses.entrySet()) {
            var registered = entry.getValue();

            if (registered.canApplyOn(type)) {
                clearBus(registered.bus, type);
            }
        }
    }

    /** 清理本组内可应用于指定 {@link ScriptType} 的所有总线下、由指定 {@code scriptId} 注册的 JS 监听器。 */
    public void clearListeners(ScriptType type, String scriptId) {
        for (var entry : buses.entrySet()) {
            var registered = entry.getValue();

            if (registered.canApplyOn(type)) {
                clearBus(registered.bus, type, scriptId);
            }
        }
    }

    /** 按 scriptId 前缀清理本组监听器（脚本包整体卸载用，见 {@link EventBusJS#clearTokensByPrefix}）。 */
    public void clearListenersByPrefix(ScriptType type, String scriptIdPrefix) {
        for (var entry : buses.entrySet()) {
            var registered = entry.getValue();

            if (registered.canApplyOn(type)) {
                clearBusByPrefix(registered.bus, type, scriptIdPrefix);
            }
        }
    }

    /// using a separate method to avoid problematic generic check
    private static <E> void clearBus(EventBusJS<E, ?> bus, ScriptType type) {
        bus.clearTokens(type);
    }

    private static <E> void clearBus(EventBusJS<E, ?> bus, ScriptType type, String scriptId) {
        bus.clearTokens(type, scriptId);
    }

    private static <E> void clearBusByPrefix(EventBusJS<E, ?> bus, ScriptType type, String scriptIdPrefix) {
        bus.clearTokensByPrefix(type, scriptIdPrefix);
    }

    /** 组内单个总线的持有句柄：携带总线绑定的 {@link ScriptType}，并可按目标脚本环境取用总线。 */
    public interface BusHolder extends WithScriptType {

        /** 总线可应用于 {@code targetEnv} 时返回该总线，否则返回 {@code null}。 */
        EventBusJS<?, ?> getBus(ScriptType targetEnv);
    }

    private record RegisteredBus(EventBusJS<?, ?> bus, ScriptType scriptType) implements BusHolder {

        @Override
        public EventBusJS<?, ?> getBus(ScriptType targetEnv) {
            return canApplyOn(targetEnv) ? bus : null;
        }
    }
}
