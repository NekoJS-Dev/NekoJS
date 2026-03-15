package com.tkisor.nekojs.api.event;

import com.tkisor.nekojs.script.ScriptType;

public final class EventHandler<E> implements IScriptHandler {
    private final String fullName;
    private final ScriptType scriptType;

    public EventHandler(String fullName, ScriptType scriptType) {
        this.fullName = fullName;
        this.scriptType = scriptType;
    }

    @Override
    public ScriptType scriptType() {
        return scriptType;
    }

    /**
     * 触发全局事件
     * @param event 事件包装对象
     */
    public void post(E event) {
        // ✨ 核心修正：调用统一的 NekoJSEventBus
        // currentEnv 传入 this.scriptType，确保环境隔离
        NekoJSEventBus.post(this.fullName, this.scriptType, event);
    }
}