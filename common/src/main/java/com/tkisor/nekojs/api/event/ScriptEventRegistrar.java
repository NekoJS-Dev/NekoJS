package com.tkisor.nekojs.api.event;

import com.tkisor.nekojs.api.ScriptType;

/**
 * Script event registrar. It backs the platform callbacks of the {@code ScriptEvents.server/client}
 * registration API. During startup {@link ScriptEvents#post} broadcasts a
 * {@link ScriptEventRegistrationEvent} carrying this instance to SERVER and CLIENT scripts. When a
 * script calls {@code event.register(...)}, the call ends up in {@link #register}, which records the
 * definition in {@link ScriptEventRegistry} so reload can clean it up.
 *
 * <p>A declared event is fired by the script itself ({@code group.name.post(payload)}) and is not
 * bound to any platform event. Binding native events by class name belongs to the loader and lives in
 * {@code NativeEvents}.
 */
public interface ScriptEventRegistrar {
    /**
     * Registers a custom script event.
     *
     * @param targetType     script type the event is for (SERVER or CLIENT)
     * @param groupName      event group name, the access prefix on the script side, such as {@code "MyEvents"}
     * @param eventName      event name, the access name inside the group
     * @param sourceScriptId id of the script that registered the event; reload cleans up by this id
     */
    void register(ScriptType targetType, String groupName, String eventName, String sourceScriptId);
}
