package com.tkisor.nekojs.api.event;

import com.tkisor.nekojs.script.ScriptType;

public interface ScriptEventRegistrar {
    void register(ScriptType targetType, String groupName, String eventName, Object eventClass, String priority, boolean receiveCancelled);
}
