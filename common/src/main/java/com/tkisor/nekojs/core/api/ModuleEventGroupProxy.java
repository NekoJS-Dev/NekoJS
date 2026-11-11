package com.tkisor.nekojs.core.api;

import com.tkisor.nekojs.api.ScriptType;
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
            if (view == null) subscriptions.put(name, bus);
            else subscriptions.put(name, (ProxyExecutable) arguments -> {
                if (arguments.length == 0 || !arguments[arguments.length - 1].canExecute()) {
                    throw new IllegalArgumentException("[NEKO-4057] Event subscription requires a synchronous callback");
                }
                Value callback = arguments[arguments.length - 1];
                ProxyExecutable invoke = values -> {
                    if (values.length != 1 || !values[0].isHostObject()) {
                        throw new IllegalArgumentException("[NEKO-4057] Event view requires a host payload: "
                                + publication.moduleId() + ":" + publication.group().name() + "." + name);
                    }
                    Object nativeEvent = values[0].asHostObject();
                    Object projected = view.converter().apply(nativeEvent);
                    if (!view.type().isInstance(projected)) {
                        throw new IllegalArgumentException("[NEKO-4057] Event converter returned an invalid public view: " + name);
                    }
                    Object payload = new BindingValueProxy(context.asValue(projected), view.type(), false);
                    Value result = callback.execute(payload);
                    if (result != null && result.hasMember("then") && result.getMember("then").canExecute()) {
                        throw new IllegalArgumentException("[NEKO-4057] Event callbacks must not return a Promise");
                    }
                    return result;
                };
                Value wrapper = wrapCallback.execute(invoke);
                Value[] forwarded = arguments.clone();
                forwarded[forwarded.length - 1] = wrapper;
                return bus.execute(forwarded);
            });
        }
    }
    @Override public Object getMember(String name) { return subscriptions.get(name); }
    @Override public boolean hasMember(String name) { return subscriptions.containsKey(name); }
    @Override public Object getMemberKeys() { return subscriptions.keySet().toArray(String[]::new); }
    @Override public void putMember(String name, Value value) { throw new UnsupportedOperationException("[NEKO-4057] Event modules are read-only"); }
}
