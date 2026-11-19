// Fabric copy of CommandEvents (the shared tree's copy is guarded by `//? if neoforge`).
// On Fabric, REGISTER is delivered by FabricCommandEventBindings via CommandRegistrationCallback,
// and COMMAND is delivered via MixinCommandsPerform -> FabricCommandEventBindings.postCommand.
package com.tkisor.nekojs.bindings.event;

import com.tkisor.nekojs.api.ScriptType;
import com.tkisor.nekojs.fabric.event.FabricCommandEventBindings;
import com.tkisor.nekojs.api.annotation.Doc;
import com.tkisor.nekojs.api.event.EventBusJS;
import com.tkisor.nekojs.api.event.EventGroup;
import com.tkisor.nekojs.api.event.StableCommandExecuteEvent;
import com.tkisor.nekojs.api.event.StableCommandRegistrationEvent;
import com.tkisor.nekojs.fabric.event.FabricStableEventProjections;
import com.tkisor.nekojs.wrapper.event.server.CommandRegistryEventJS;

/**
 * Command event group for server scripts on Fabric: command registration and command execution.
 */
@Doc("Command event group for server scripts (command registration and execution).")
public interface CommandEvents {
    /** Event group published as {@code CommandEvents}. */
    EventGroup GROUP = EventGroup.of("CommandEvents");

    /** Fired on the server when commands are registered (startup and reload). */
    @Doc("Fired when the server registers commands, allowing scripts to register custom Brigadier commands.")
    @Doc("Only visible in server_scripts.")
    EventBusJS<CommandRegistryEventJS, Void> REGISTER =
            GROUP.server("register", CommandRegistryEventJS.class, api -> {
                api.versioned().when(event -> !FabricCommandEventBindings.isInstalledRegistration(event));
                api.stable()
                        .when(FabricCommandEventBindings::isInstalledRegistration)
                        .view(StableCommandRegistrationEvent.class, FabricStableEventProjections::commandRegister)
                        .scriptTypes(ScriptType.SERVER);
            });

    /** Fired before a command executes on the server. */
    @Doc("Fired before a command executes on the server. Return true to cancel.")
    EventBusJS<StableCommandExecuteEvent, Void> COMMAND =
            GROUP.add("command", ScriptType.SERVER,
                    EventBusJS.of(StableCommandExecuteEvent.class, true),
                    api -> api.stable().scriptTypes(ScriptType.SERVER));
}
