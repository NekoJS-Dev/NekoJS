package com.tkisor.nekojs.core.api;

import com.tkisor.nekojs.api.ScriptType;
import com.tkisor.nekojs.api.data.BindingExposure;
import com.tkisor.nekojs.api.event.DispatchEventBus;
import com.tkisor.nekojs.api.event.ModuleEventGroup;
import graal.graalvm.polyglot.Context;
import graal.graalvm.polyglot.Value;
import graal.graalvm.polyglot.proxy.ProxyExecutable;
import graal.graalvm.polyglot.proxy.ProxyObject;
import java.util.LinkedHashMap;
import java.util.Map;

/** Publishes subscriptions to the original bus, projecting payloads and selectors at subscription and callback delivery. */
public final class ModuleEventGroupProxy implements ProxyObject {
    private final Map<String, Object> subscriptions = new LinkedHashMap<>();

    public ModuleEventGroupProxy(Context context, ModuleEventGroup publication, ScriptType type) {
        Value wrapCallback = context.eval("js", "invoke => event => invoke(event)");
        for (String name : publication.group().viewBuses().keySet()) {
            if (!publication.events().contains(name)) continue;
            var holder = publication.group().getBusHolder(name);
            if (!publication.supports(name, type)) continue;
            var bus = holder.getBus(type);
            var view = publication.views().get(name);
            var selector = publication.selectors().get(name);
            var filter = publication.deliveryFilters().get(name);
            boolean stable = publication.category() == BindingExposure.Category.STABLE;
            boolean rawPayload = view == null && selector == null && !stable;
            boolean notificationOnly = publication.notificationOnlyEvents().contains(name);
            if (rawPayload && filter == null && !notificationOnly) {
                subscriptions.put(name, bus);
                continue;
            }
            Class<?> payloadType = view == null ? bus.eventType() : view.type();
            subscriptions.put(name, (ProxyExecutable) arguments -> {
                var subscription = EventSubscriptionArguments.parse(arguments, bus.canDispatch());
                if (selector != null && selector.required()) {
                    if (subscription.key() == null || subscription.key().isNull()) {
                        throw new IllegalArgumentException("[NEKO-4057] Event subscription requires a selector: " + name);
                    }
                    if (subscription.listenerIndex() != arguments.length - 1
                            || (selector.type() == String.class && !subscription.key().isString())) {
                        throw new IllegalArgumentException("[NEKO-4057] Invalid event selector or subscription arguments for " + name);
                    }
                }
                Value callback = subscription.listener();
                ProxyExecutable invoke = values -> {
                    Object payload;
                    if (rawPayload) {
                        payload = values[0];
                    } else if (view == null) {
                        // Scalars and arrays retain their value representation; host objects expose only the declared type.
                        payload = values[0].isHostObject() && !payloadType.isArray()
                                ? BindingValueProxy.stableEvent(values[0], payloadType) : values[0];
                    } else {
                        if (values.length != 1 || !values[0].isHostObject()) {
                            throw new IllegalArgumentException("[NEKO-4057] Event view requires a host payload: "
                                    + publication.moduleId() + ":" + publication.group().name() + "." + name);
                        }
                        Object projected = view.converter().apply(values[0].asHostObject());
                        if (!payloadType.isInstance(projected)) {
                            throw new IllegalArgumentException("[NEKO-4057] Event converter returned an invalid public view: " + name);
                        }
                        Value projectedValue = context.asValue(projected);
                        payload = stable ? BindingValueProxy.stableEvent(projectedValue, payloadType)
                                : new BindingValueProxy(projectedValue, payloadType, false);
                    }
                    Value result = callback.execute(payload);
                    if ((view != null || stable)
                            && result != null && result.hasMember("then") && result.getMember("then").canExecute()) {
                        throw new IllegalArgumentException("[NEKO-4057] Event callbacks must not return a Promise");
                    }
                    return notificationOnly ? false : result;
                };
                Value wrapper = wrapCallback.execute(invoke);
                Value[] forwarded = arguments.clone();
                forwarded[subscription.listenerIndex()] = wrapper;
                if (selector != null && subscription.key() != null) {
                    Object rawKey;
                    try {
                        rawKey = subscription.key().as(selector.type());
                    } catch (RuntimeException failure) {
                        throw new IllegalArgumentException("[NEKO-4057] Invalid event selector for " + name, failure);
                    }
                    Object convertedKey = selector.converter().apply(rawKey);
                    Class<?> expectedKeyType = ((DispatchEventBus<?, ?>) bus.bus()).dispatchKey().keyType();
                    if (!expectedKeyType.isInstance(convertedKey)) {
                        throw new IllegalArgumentException("[NEKO-4057] Event selector converter returned an invalid dispatch key: " + name);
                    }
                    forwarded[subscription.listenerIndex() - 1] = context.asValue(convertedKey);
                }
                if (stable) return bus.executeStableModule(forwarded, filter);
                return filter == null ? bus.execute(forwarded) : bus.executeFiltered(forwarded, filter);
            });
        }
    }
    @Override public Object getMember(String name) { return subscriptions.get(name); }
    @Override public boolean hasMember(String name) { return subscriptions.containsKey(name); }
    @Override public Object getMemberKeys() { return subscriptions.keySet().toArray(String[]::new); }
    @Override public void putMember(String name, Value value) { throw new UnsupportedOperationException("[NEKO-4057] Event modules are read-only"); }
}
