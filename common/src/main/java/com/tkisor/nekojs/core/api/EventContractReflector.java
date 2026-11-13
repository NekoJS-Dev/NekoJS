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
 * Derives {@link NormativeApiContract.ContractEvent} values by reflection from the runtime
 * {@link EventGroup} set, instead of reading the events field of the portable-core JSON.
 *
 * <p>Every {@link EventBusJS} carries all event metadata (group, name, tier, dispatch,
 * cancellable), so it can be reflected directly. Payload fields come from the public no-arg
 * getters of {@code bus.eventType()}.
 *
 * <p>Nothing at runtime reads tier, dispatch or cancellable, and the probe does not read the JSON
 * events either. The only runtime consumer of ContractEvent is
 * {@code ManagedCallbackSchemaRegistry.installContractEvents}, which reads only the payload field
 * names, so a reflected ContractEvent with an imprecise tier or dispatch still works.
 */
public final class EventContractReflector {

    private EventContractReflector() {
    }

    /**
     * Reflects the event groups into a list of ContractEvent values.
     *
     * @param eventGroups event groups registered at runtime, from {@code IPluginRuntime.eventGroups()}
     * @return one ContractEvent per bus in each event group
     */
    public static List<NormativeApiContract.ContractEvent> extractEvents(Collection<EventGroup> eventGroups) {
        List<NormativeApiContract.ContractEvent> result = new ArrayList<>();
        if (eventGroups == null) return result;
        for (EventGroup group : eventGroups) {
            for (var entry : group.viewBuses().entrySet()) {
                String eventName = entry.getKey();
                var holder = entry.getValue();
                // Take the bus of any ScriptType; the bus instance is the same for every ScriptType
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

        // Dispatch key type: reflect keyType from DispatchEventBus (currently always string)
        String dispatchKeyType = null;
        if (bus.canDispatch() && bus.bus() instanceof DispatchEventBus<?, ?> dispatchBus) {
            Class<?> keyClass = dispatchBus.dispatchKey().keyType();
            // The registration key on the script side is always a string id, converted by JSTypeAdapter, so
            // declare string
            dispatchKeyType = "string";
        }

        Boolean cancellable = bus.canCancel();

        // Payload: reflect the public no-arg getters of the event type into fields
        List<NormativeApiContract.ContractEventField> payload = reflectPayload(bus.eventType());

        return new NormativeApiContract.ContractEvent(
                groupName, eventName, tier, dispatch, dispatchKeyType, cancellable, payload, null);
    }

    /**
     * Reflects the public no-arg getters of an event class into its payload fields.
     *
     * <p>Every field is marked NATIVE because its value is a platform object and reflection cannot
     * tell whether it is portable across platforms. The field name comes from the getter, so
     * getMessage becomes message.
     */
    private static List<NormativeApiContract.ContractEventField> reflectPayload(Class<?> eventType) {
        List<NormativeApiContract.ContractEventField> fields = new ArrayList<>();
        for (Method method : eventType.getMethods()) {
            if (method.getDeclaringClass() == Object.class) continue;
            if (method.getParameterCount() != 0) continue;
            if (method.getReturnType() == void.class) continue;
            if (method.isBridge() || method.isSynthetic()) continue;
            String name = method.getName();
            // Skip neko$ methods: they are added by mixins and are not native event fields
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
