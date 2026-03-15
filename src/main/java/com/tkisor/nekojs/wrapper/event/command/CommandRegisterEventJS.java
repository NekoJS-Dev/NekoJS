package com.tkisor.nekojs.wrapper.event.command;

import com.mojang.brigadier.CommandDispatcher;
import com.tkisor.nekojs.bindings.event.NekoEvent;
import com.tkisor.nekojs.wrapper.command.CommandContextJS;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import java.util.function.Consumer;

public class CommandRegisterEventJS implements NekoEvent {
    private final RegisterCommandsEvent rawEvent;
    private final CommandDispatcher<CommandSourceStack> dispatcher;

    public CommandRegisterEventJS(RegisterCommandsEvent rawEvent) {
        this.rawEvent = rawEvent;
        this.dispatcher = rawEvent.getDispatcher();
    }

    public void register(String commandName, Consumer<CommandContextJS> executor) {
        dispatcher.register(
                Commands.literal(commandName)
                        .executes(context -> {
                            executor.accept(new CommandContextJS(context));
                            return 1;
                        })
        );
    }
}