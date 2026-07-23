package com.tkisor.nekojs.bindings.event;

import com.tkisor.nekojs.api.event.EventBusForgeBridge;
import com.tkisor.nekojs.api.event.EventBusJS;
import com.tkisor.nekojs.api.event.EventGroup;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.CommandEvent;

public interface CommandEvents {
    EventGroup GROUP = EventGroup.of("CommandEvents");

    EventBusJS<CommandEvent, Void> COMMAND =
            GROUP.server("command", CommandEvent.class);

    // 注：1.12.2 没有 RegisterCommandsEvent。命令注册走 FMLServerStartingEvent.registerServerCommand(ICommand)。
    // FMLServerStartingEvent 继承 FMLStateEvent（非 eventhandler.Event），不走 MinecraftForge.EVENT_BUS，
    // 无法用 EventBusForgeBridge 订阅。但 NekoJSMod 的 @Mod.EventHandler 会把它转发 post 到
    // ServerEvents.starting，因此脚本可在 ServerEvents.starting(event => event.registerServerCommand(cmd)) 注册命令。

    EventBusForgeBridge FORGE_BRIDGE = EventBusForgeBridge.create(MinecraftForge.EVENT_BUS)
            .bind(COMMAND);
}
