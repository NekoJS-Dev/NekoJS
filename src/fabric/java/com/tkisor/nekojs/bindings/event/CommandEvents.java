// Fabric copy of the shared tree's CommandEvents interface, which is guarded file-wide by
// `//? if neoforge` and passes bus calls straight to the native NeoForge
// RegisterCommandsEvent/CommandEvent. On Fabric, REGISTER is delivered by
// FabricCommandEventBindings through CommandRegistrationCallback. The execution-interception bus
// (command) needs a mixin on Commands#performCommand; this copy grows once that lands.
package com.tkisor.nekojs.bindings.event;

import com.tkisor.nekojs.api.event.EventBusJS;
import com.tkisor.nekojs.api.event.EventGroup;
import com.tkisor.nekojs.wrapper.event.server.CommandRegistryEventJS;

/** Command event group for server scripts: command registration (the Fabric subset). */
public interface CommandEvents {
    EventGroup GROUP = EventGroup.of("CommandEvents");

    EventBusJS<CommandRegistryEventJS, Void> REGISTER =
            GROUP.server("register", CommandRegistryEventJS.class);
}
