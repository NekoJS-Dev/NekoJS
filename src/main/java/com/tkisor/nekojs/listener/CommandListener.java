package com.tkisor.nekojs.listener;

import com.tkisor.nekojs.bindings.event.CommandEvents;
import com.tkisor.nekojs.wrapper.event.command.CommandRegisterEventJS;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

@EventBusSubscriber
public class CommandListener {
    @SubscribeEvent
    public static void onCommandsRegister(RegisterCommandsEvent event) {
        CommandRegisterEventJS eventJS = new CommandRegisterEventJS(event);
        CommandEvents.REGISTER.post(eventJS);
    }
}
