package com.tkisor.nekojs.api.event;

import com.tkisor.nekojs.api.ScriptType;

public interface ScriptEventRegistrar {
    void register(ScriptType targetType, String groupName, String eventName, Object eventClass, String priority, boolean receiveCancelled);
}
