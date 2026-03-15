package com.tkisor.nekojs.api.event; // 建议放在专门的 event 包下

import com.tkisor.nekojs.script.ScriptType;
import org.graalvm.polyglot.Value;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

public final class NekoJSEventBus {

    private static final Logger LOGGER = LoggerFactory.getLogger("NekoJS-EventBus");

    /**
     * 单个注册记录 (Registration)
     * 使用 Record 极其轻量，保存了环境、目标过滤器和底层 JS 函数指针
     */
    private record EventRegistration(
            ScriptType scriptType,
            String target,
            Value callback
    ) {}

    private static final Map<String, List<EventRegistration>> LISTENERS = new ConcurrentHashMap<>();

    private NekoJSEventBus() {}

    /**
     * JS 侧注册事件
     * @param eventName  事件全名 (如 "BlockEvents.broken")
     * @param scriptType 当前注册该事件的 JS 脚本环境
     * @param target     目标过滤器 (可为 null)
     * @param callback   JS 回调函数
     */
    public static void register(String eventName, ScriptType scriptType, String target, Value callback) {
        LISTENERS.computeIfAbsent(eventName, k -> new CopyOnWriteArrayList<>())
                .add(new EventRegistration(scriptType, target, callback));
    }

    /**
     * 触发无目标事件 (Global Event)
     */
    public static void post(String eventName, ScriptType currentEnv, Object eventObj) {
        postTargeted(eventName, currentEnv, null, eventObj);
    }

    /**
     * 触发带目标的事件 (Targeted Event)
     */
    public static void postTargeted(String eventName, ScriptType currentEnv, String eventTarget, Object eventObj) {
        List<EventRegistration> registrations = LISTENERS.get(eventName);
        if (registrations == null || registrations.isEmpty()) return;

        for (EventRegistration reg : registrations) {

            if (reg.scriptType() != ScriptType.COMMON && reg.scriptType() != currentEnv) {
                continue;
            }

            if (reg.target() != null && !reg.target().equals(eventTarget)) {
                continue;
            }

            try {
                reg.callback().executeVoid(eventObj);
            } catch (Exception e) {
                LOGGER.error("Exception occurred while executing script event [{}]:", eventName, e);
            }
        }
    }

    /**
     * 清除特定脚本类型的所有事件监听器，彻底杜绝热重载导致的内存泄漏
     */
    public static void clearByType(ScriptType type) {
        for (List<EventRegistration> registrations : LISTENERS.values()) {
            registrations.removeIf(reg -> reg.scriptType() == type);
        }
    }
}