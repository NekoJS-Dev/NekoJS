package com.tkisor.nekojs.bindings.event;

import com.tkisor.nekojs.api.event.EventBusForgeBridge;
import com.tkisor.nekojs.api.event.EventBusJS;
import com.tkisor.nekojs.api.event.EventGroup;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.CommandEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/** 命令事件组（server 脚本）：命令注册与命令执行。 */
public interface CommandEvents {
    EventGroup GROUP = EventGroup.of("CommandEvents");

    EventBusJS<RegisterCommandsEvent, Void> REGISTER =
            GROUP.server("register", RegisterCommandsEvent.class);
    EventBusJS<CommandEvent, Void> COMMAND =
            GROUP.server("command", CommandEvent.class);

    EventBusForgeBridge FORGE_BRIDGE = EventBusForgeBridge.create(NeoForge.EVENT_BUS)
            .bind(REGISTER)
            .bind(COMMAND);
}
