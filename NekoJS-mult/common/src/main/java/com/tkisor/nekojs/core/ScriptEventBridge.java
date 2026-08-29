package com.tkisor.nekojs.core;

import com.tkisor.nekojs.api.event.ScriptEventRegistrar;
import com.tkisor.nekojs.api.ScriptType;
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

    /**
     * 按 scriptId 前缀反注册监听器（脚本包整体卸载用；前缀见
     * {@code ScriptPack#scriptIdPrefix(ScriptType)}）。默认空实现，与 {@link #EMPTY} 一致。
     */
    default void clearListenersByPrefix(ScriptType type, String scriptIdPrefix) {
    }
}
