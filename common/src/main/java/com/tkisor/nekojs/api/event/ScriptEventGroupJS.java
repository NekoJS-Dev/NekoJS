package com.tkisor.nekojs.api.event;

import graal.graalvm.polyglot.Value;
import graal.graalvm.polyglot.proxy.ProxyObject;

import java.util.Map;

public class ScriptEventGroupJS implements ProxyObject {
    private final String groupName;
    private final Map<String, ScriptEventDefinition> definitions;
    /** Event handles are cached by name: creating a new one on each getMember would make {@code G.n !== G.n}. */
    private final Map<String, ScriptEventBusJS> handles = new java.util.concurrent.ConcurrentHashMap<>();

    public ScriptEventGroupJS(String groupName, Map<String, ScriptEventDefinition> definitions) {
        this.groupName = groupName;
        this.definitions = definitions;
    }

    @Override
    public Object getMember(String key) {
        ScriptEventDefinition definition = definitions.get(key);
        if (definition == null) {
            throw new IllegalArgumentException("No such script event bus: " + groupName + "." + key);
        }
        return handles.computeIfAbsent(key, name -> new ScriptEventBusJS(groupName, name, definition.bus()));
    }

    @Override
    public Object getMemberKeys() {
        return definitions.keySet().toArray();
    }

    @Override
    public boolean hasMember(String key) {
        return definitions.containsKey(key);
    }

    @Override
    public void putMember(String key, Value value) {
        throw new UnsupportedOperationException("putMember(...) not supported.");
    }
}
