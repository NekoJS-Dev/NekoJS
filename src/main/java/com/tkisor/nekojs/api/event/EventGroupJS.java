package com.tkisor.nekojs.api.event;

import com.tkisor.nekojs.script.ScriptType;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.proxy.ProxyObject;

/**
 * @author ZZZank
 */
public class EventGroupJS implements ProxyObject {
    private final EventGroup group;
    private final ScriptType currentEnv;

    public EventGroupJS(EventGroup group, ScriptType currentEnv) {
        this.group = group;
        this.currentEnv = currentEnv;
    }

    @Override
    public Object getMember(String key) {
        var handler = group.buses.get(key);
        if (handler == null) {
            throw new IllegalArgumentException(String.format("No such event bus: %s.%s", group.name(), key));
        }
        if (!group.isHandlerValidFor(key, currentEnv)) {
            throw new IllegalArgumentException(String.format("Event '%s.%s' not available in %s", group.name(), key, currentEnv));
        }

        return handler;
    }

    @Override
    public Object getMemberKeys() {
        return group.buses.keySet().toArray();
    }

    @Override
    public boolean hasMember(String key) {
        return group.buses.containsKey(key);
    }

    @Override
    public void putMember(String key, Value value) {
        throw new UnsupportedOperationException("putMember(...) not supported.");
    }
}
