package com.tkisor.nekojs.core;

import com.tkisor.nekojs.api.event.ScriptEventRegistrar;
import com.tkisor.nekojs.script.ScriptType;
import graal.graalvm.polyglot.Value;

public interface ScriptEventBridge {
    ScriptEventBridge EMPTY = new ScriptEventBridge() {
        @Override
        public void bindEvents(Value bindings, ScriptType type) {
        }

        @Override
        public void clearListeners(ScriptType type) {
        }
    };

    void bindEvents(Value bindings, ScriptType type);

    void clearListeners(ScriptType type);

    default ScriptEventRegistrar scriptEventRegistrar() {
        return (targetType, groupName, eventName, eventClass, priority, receiveCancelled) -> {
            throw new UnsupportedOperationException("Script event registration is not available");
        };
    }

    default void clearListeners(ScriptType type, String scriptId) {
        clearListeners(type);
    }
}
