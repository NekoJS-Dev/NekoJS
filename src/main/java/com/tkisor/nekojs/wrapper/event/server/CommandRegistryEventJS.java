package com.tkisor.nekojs.wrapper.event.server;

import com.mojang.brigadier.CommandDispatcher;
import lombok.Getter;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;

/**
 * Loader-neutral event object for command registration. Scripts register commands on
 * {@code event.dispatcher} (for example,
 * {@code event.dispatcher.register(Commands.literal('mymod:hello').executes(...))}), and use
 * {@code registryAccess} for registry-backed argument types. Fired while the server builds its
 * command tree (NeoForge {@code RegisterCommandsEvent} / Fabric {@code CommandRegistrationCallback}).
 */
public class CommandRegistryEventJS {

    @Getter
    private final CommandDispatcher<CommandSourceStack> dispatcher;

    @Getter
    private final CommandBuildContext registryAccess;

    @Getter
    private final String commandSelection;

    public CommandRegistryEventJS(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext registryAccess) {
        this(dispatcher, registryAccess, "");
    }

    public CommandRegistryEventJS(
            CommandDispatcher<CommandSourceStack> dispatcher,
            CommandBuildContext registryAccess,
            String commandSelection) {
        this.dispatcher = dispatcher;
        this.registryAccess = registryAccess;
        this.commandSelection = commandSelection == null ? "" : commandSelection;
    }
}
