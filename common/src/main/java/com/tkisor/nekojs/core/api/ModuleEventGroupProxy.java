package com.tkisor.nekojs.core.api;

import com.tkisor.nekojs.api.ScriptType;
import com.tkisor.nekojs.api.data.BindingExposure;
import com.tkisor.nekojs.api.event.ModuleEventGroup;
import graal.graalvm.polyglot.Context;
import graal.graalvm.polyglot.Value;
import graal.graalvm.polyglot.proxy.ProxyExecutable;
import graal.graalvm.polyglot.proxy.ProxyObject;
import java.util.LinkedHashMap;
import java.util.Map;

/** Publishes subscriptions to the original bus, projecting payloads only at callback delivery. */
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
            if (view == null && publication.category() != BindingExposure.Category.STABLE) {
                subscriptions.put(name, bus);
                continue;
            }
            Class<?> payloadType = view == null ? bus.eventType() : view.type();
            subscriptions.put(name, (ProxyExecutable) arguments -> {
                var subscription = EventSubscriptionArguments.parse(arguments, bus.canDispatch());
                Value callback = subscription.listener();
                ProxyExecutable invoke = values -> {
                    Object payload;
                    if (view == null) {
                        // Scalars and arrays retain their value representation; host objects expose only the declared type.
                        payload = values[0].isHostObject() && !payloadType.isArray()
                                ? new BindingValueProxy(values[0], payloadType, false) : values[0];
                    } else {
                        if (values.length != 1 || !values[0].isHostObject()) {
                            throw new IllegalArgumentException("[NEKO-4057] Event view requires a host payload: "
                                    + publication.moduleId() + ":" + publication.group().name() + "." + name);
                        }
                        Object projected = view.converter().apply(values[0].asHostObject());
                        if (!payloadType.isInstance(projected)) {
                            throw new IllegalArgumentException("[NEKO-4057] Event converter returned an invalid public view: " + name);
                        }
                        payload = new BindingValueProxy(context.asValue(projected), payloadType, false);
                    }
                    Value result = callback.execute(payload);
                    if (view != null && result != null && result.hasMember("then") && result.getMember("then").canExecute()) {
                        throw new IllegalArgumentException("[NEKO-4057] Event callbacks must not return a Promise");
                    }
                    return result;
                };
                Value wrapper = wrapCallback.execute(invoke);
                Value[] forwarded = arguments.clone();
                forwarded[subscription.listenerIndex()] = wrapper;
                return bus.execute(forwarded);
            });
        }
    }
    @Override public Object getMember(String name) { return subscriptions.get(name); }
    @Override public boolean hasMember(String name) { return subscriptions.containsKey(name); }
    @Override public Object getMemberKeys() { return subscriptions.keySet().toArray(String[]::new); }
    @Override public void putMember(String name, Value value) { throw new UnsupportedOperationException("[NEKO-4057] Event modules are read-only"); }
}
