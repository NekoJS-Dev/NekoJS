package com.tkisor.nekojs.api.event;

import com.tkisor.nekojs.script.ScriptType;

public final class TargetedEventHandler<E> implements IScriptHandler {
    private final String fullName;
    private final ScriptType scriptType;

    public TargetedEventHandler(String fullName, ScriptType scriptType) {
        this.fullName = fullName;
        this.scriptType = scriptType;
    }

    @Override
    public ScriptType scriptType() {
        return scriptType;
    }

    /**
     * 触发带目标的事件
     */
    public void post(String target, E event) {
        NekoJSEventBus.postTargeted(this.fullName, this.scriptType, target, event);
    }
}