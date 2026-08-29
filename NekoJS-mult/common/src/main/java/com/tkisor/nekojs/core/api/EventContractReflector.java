package com.tkisor.nekojs.core.api;

import com.tkisor.nekojs.api.ScriptType;
import com.tkisor.nekojs.api.contract.NormativeApiContract;
import com.tkisor.nekojs.api.event.DispatchEventBus;
import com.tkisor.nekojs.api.event.EventBusJS;
import com.tkisor.nekojs.api.event.EventGroup;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * 从运行时 {@link EventGroup} 集合反射派生 {@link NormativeApiContract.ContractEvent}，
 * 替代从 portable-core JSON 的 events 字段读取。
 *
 * <p>每个 {@link EventBusJS} 携带全部事件元数据（group/name/tier/dispatch/cancellable），
 * 可直接反射。payload 字段从 {@code bus.eventType()} 的 public 零参 getter 反射。
 *
 * <p>调研结论：tier/dispatch/cancellable 从未被运行时代码消费（probe 也不读 JSON events），
 * ContractEvent 的唯一运行时消费者是 {@code ManagedCallbackSchemaRegistry.installContractEvents}，
 * 它只读 payload 字段名。故反射出的 ContractEvent 即使 tier/dispatch 不完全精确也不影响功能。
 */
public final class EventContractReflector {

    private EventContractReflector() {
    }

    /**
     * 从事件组集合反射出 ContractEvent 列表。
     *
     * @param eventGroups 运行时已注册的事件组（来自 {@code IPluginRuntime.eventGroups()}）
     * @return ContractEvent 列表，每个事件组中的每个 bus 一个条目
     */
    public static List<NormativeApiContract.ContractEvent> extractEvents(Collection<EventGroup> eventGroups) {
        List<NormativeApiContract.ContractEvent> result = new ArrayList<>();
        if (eventGroups == null) return result;
        for (EventGroup group : eventGroups) {
            for (var entry : group.viewBuses().entrySet()) {
                String eventName = entry.getKey();
                var holder = entry.getValue();
                // 取任意 ScriptType 的 bus（bus 实例不随 ScriptType 变化）
                EventBusJS<?, ?> bus = null;
                for (ScriptType st : ScriptType.values()) {
                    bus = holder.getBus(st);
                    if (bus != null) break;
                }
                if (bus == null) continue;

                result.add(buildEvent(group.name(), eventName, bus.scriptType(), bus));
            }
        }
        return result;
    }

    private static NormativeApiContract.ContractEvent buildEvent(
            String groupName, String eventName, ScriptType scriptType, EventBusJS<?, ?> bus) {

        NormativeApiContract.EventTier tier = switch (scriptType) {
            case STARTUP -> NormativeApiContract.EventTier.STARTUP;
            case SERVER -> NormativeApiContract.EventTier.SERVER;
            case CLIENT -> NormativeApiContract.EventTier.CLIENT;
            default -> NormativeApiContract.EventTier.SERVER;
        };

        NormativeApiContract.Dispatch dispatch = bus.canDispatch()
                ? NormativeApiContract.Dispatch.BY_ID
                : NormativeApiContract.Dispatch.PLAIN;

        // dispatch key type：从 DispatchEventBus 反射 keyType（当前固定为 string）
        String dispatchKeyType = null;
        if (bus.canDispatch() && bus.bus() instanceof DispatchEventBus<?, ?> dispatchBus) {
            Class<?> keyClass = dispatchBus.dispatchKey().keyType();
            // 脚本侧注册键永远是字符串 id（由 JSTypeAdapter 转换），故声明 string
            dispatchKeyType = "string";
        }

        Boolean cancellable = bus.canCancel();

        // payload：从事件类型反射 public 零参 getter 作为字段
        List<NormativeApiContract.ContractEventField> payload = reflectPayload(bus.eventType());

        return new NormativeApiContract.ContractEvent(
                groupName, eventName, tier, dispatch, dispatchKeyType, cancellable, payload, null);
    }

    /**
     * 反射事件类的 public 零参 getter，产出 payload 字段列表。
     *
     * <p>所有字段标为 NATIVE（值是平台原生对象），因为反射无法判断跨平台可移植性。
     * 字段名从 getXxx/isXxx 推导（如 getMessage → message）。
     */
    private static List<NormativeApiContract.ContractEventField> reflectPayload(Class<?> eventType) {
        List<NormativeApiContract.ContractEventField> fields = new ArrayList<>();
        for (Method method : eventType.getMethods()) {
            if (method.getDeclaringClass() == Object.class) continue;
            if (method.getParameterCount() != 0) continue;
            if (method.getReturnType() == void.class) continue;
            if (method.isBridge() || method.isSynthetic()) continue;
            String name = method.getName();
            // 跳过 neko$ 方法（mixin 注入的，不是原生事件字段）
            if (name.startsWith("neko$")) continue;
            if (name.startsWith("get") && name.length() > 3) {
                String field = Character.toLowerCase(name.charAt(3)) + name.substring(4);
                fields.add(new NormativeApiContract.ContractEventField(field, NormativeApiContract.FieldKind.NATIVE, null, null));
            } else if (name.startsWith("is") && name.length() > 2) {
                String field = Character.toLowerCase(name.charAt(2)) + name.substring(3);
                fields.add(new NormativeApiContract.ContractEventField(field, NormativeApiContract.FieldKind.NATIVE, null, null));
            }
        }
        return fields;
    }
}
