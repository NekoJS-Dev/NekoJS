//? if neoforge {
package com.tkisor.nekojs.bindings.event;

import com.tkisor.nekojs.api.ScriptType;
import com.tkisor.nekojs.api.annotation.Doc;
import com.tkisor.nekojs.api.event.EventBusForgeBridge;
import com.tkisor.nekojs.api.event.EventBusJS;
import com.tkisor.nekojs.api.event.EventGroup;
import com.tkisor.nekojs.api.event.StableCommandExecuteEvent;
import com.tkisor.nekojs.api.event.StableCommandRegistrationEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.CommandEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/**
 * Command event group (server scripts): command registration and command execution.
 */
@Doc("Command event group for server scripts (command registration and execution).")
public interface CommandEvents {
    /** Event group published as {@code CommandEvents} in the versioned and stable event modules. */
    EventGroup GROUP = EventGroup.of("CommandEvents");

    /** Fired when server commands are being registered. */
    @Doc("Fired when server commands are registered; use event.register(...) to add custom Brigadier commands.")
    EventBusJS<RegisterCommandsEvent, Void> REGISTER =
            GROUP.server("register", RegisterCommandsEvent.class, api -> {
                api.versioned().when(event -> !NeoForgeCommandRegistration.isInstalled(event));
                api.stable()
                        .when(NeoForgeCommandRegistration::isInstalled)
                        .view(StableCommandRegistrationEvent.class, NeoForgeStableEventProjections::commandRegister)
                        .scriptTypes(ScriptType.SERVER);
            });

    /** Fired when a command is about to be executed on the server. */
    @Doc("Fired before a command executes on the server. Return true to cancel.")
    EventBusJS<CommandEvent, Void> COMMAND =
            GROUP.server("command", CommandEvent.class, api -> {
                api.versioned();
                api.stable()
                        .when(event -> CommandEventThread.isCurrent(event.getParseResults()))
                        .view(StableCommandExecuteEvent.class, NeoForgeStableEventProjections::commandExecute)
                        .cancellable()
                        .scriptTypes(ScriptType.SERVER);
            });

    /** Native NeoForge event bus bridge for command events. */
    EventBusForgeBridge FORGE_BRIDGE = EventBusForgeBridge.create(NeoForge.EVENT_BUS)
            .bind(REGISTER, NeoForgeCommandRegistration::capture)
            .bind(COMMAND);
}
//?}
