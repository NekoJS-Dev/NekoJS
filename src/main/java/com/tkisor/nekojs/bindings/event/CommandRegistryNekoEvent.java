package com.tkisor.nekojs.bindings.event;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;

public class CommandRegistryNekoEvent implements NekoEvent {
    public final CommandDispatcher<CommandSourceStack> dispatcher;
    public final CommandBuildContext context;
    public final Commands.CommandSelection selection;

    public CommandRegistryNekoEvent(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext context, Commands.CommandSelection selection) {
        this.dispatcher = dispatcher;
        this.context = context;
        this.selection = selection;
    }

    public boolean isForSinglePlayer() {
        return selection.includeIntegrated;
    }

    public boolean isForMultiPlayer() {
        return selection.includeDedicated;
    }

    public CommandBuildContext getRegistry() {
        return context;
    }
}
