package com.tkisor.nekojs.core;

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

    default void clearListeners(ScriptType type, String scriptId) {
        clearListeners(type);
    }
}
