package com.tkisor.nekojs.api.event;

import com.tkisor.nekojs.script.ScriptType;
import graal.graalvm.polyglot.Value;
import graal.graalvm.polyglot.proxy.ProxyObject;

import java.util.Map;

/**
 * @author ZZZank
 */
public class EventGroupJS implements ProxyObject {
    private final EventGroup group;
    private final ScriptType currentEnv;
    private final Map<String, EventGroup.BusHolder> busView;

    public EventGroupJS(EventGroup group, ScriptType currentEnv) {
        this.group = group;
        this.currentEnv = currentEnv;
        this.busView = group.viewBuses();
    }

    @Override
    public Object getMember(String key) {
        var busHolder = group.getBusHolder(key);
        if (busHolder == null) {
            throw new IllegalArgumentException(String.format("No such event bus: %s.%s", group.name(), key));
        }

        var bus = busHolder.getBus(this.currentEnv);
        if (bus == null) {
            throw new IllegalArgumentException(String.format("Event '%s.%s' not available in %s", group.name(), key, currentEnv));
        }

        return bus;
    }

    @Override
    public Object getMemberKeys() {
        return busView.keySet().toArray();
    }

    @Override
    public boolean hasMember(String key) {
        var holder = busView.get(key);
        return holder != null && holder.canApplyOn(currentEnv);
    }

    @Override
    public void putMember(String key, Value value) {
        throw new UnsupportedOperationException("putMember(...) not supported.");
    }
}
