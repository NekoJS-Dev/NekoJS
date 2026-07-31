package com.tkisor.nekojs.bindings.event;

import com.tkisor.nekojs.api.event.EventBusForgeBridge;
import com.tkisor.nekojs.api.event.EventBusJS;
import com.tkisor.nekojs.api.event.EventGroup;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.CommandEvent;
import net.minecraftforge.fml.common.event.FMLServerStartingEvent;

public interface CommandEvents {
    EventGroup GROUP = EventGroup.of("CommandEvents");

    /**
     * 命令注册事件。1.12.2 无 {@code RegisterCommandsEvent}；命令注册走
     * {@link FMLServerStartingEvent#registerServerCommand(net.minecraft.command.ICommand)}。
     * 该事件继承 {@code FMLStateEvent}（非 {@code eventhandler.Event}），不走
     * {@code MinecraftForge.EVENT_BUS}，由 {@code NekoJSMod.serverStarting} 手动转发。
     * 脚本可 {@code CommandEvents.register(event => event.registerServerCommand(cmd))}。
     */
    EventBusJS<FMLServerStartingEvent, Void> REGISTER =
            GROUP.server("register", FMLServerStartingEvent.class);

    EventBusJS<CommandEvent, Void> COMMAND =
            GROUP.server("command", CommandEvent.class);

    EventBusForgeBridge FORGE_BRIDGE = EventBusForgeBridge.create(MinecraftForge.EVENT_BUS)
            .bind(COMMAND);
}
