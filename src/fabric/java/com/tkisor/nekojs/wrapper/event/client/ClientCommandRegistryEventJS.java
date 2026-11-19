package com.tkisor.nekojs.wrapper.event.client;

import com.mojang.brigadier.CommandDispatcher;
import com.tkisor.nekojs.api.annotation.Doc;
import com.tkisor.nekojs.api.annotation.Param;
import com.tkisor.nekojs.api.annotation.Return;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.commands.CommandBuildContext;

/**
 * Fabric client command registration wrapper for {@code ClientCommandRegistrationCallback.EVENT}
 * ({@code ClientEvents.commandRegistry}).
 *
 * <p>Scripts register client-side Brigadier commands on {@link #getDispatcher()} and use
 * {@link #getRegistryAccess()} for registry-backed command arguments.
 */
@Doc("Fired on the client when client-side commands are registered (ClientEvents.commandRegistry).")
@Doc("Use event.dispatcher.register(...) to add custom client Brigadier commands.")
public class ClientCommandRegistryEventJS {

    @Doc("The client Brigadier command dispatcher targeting FabricClientCommandSource.")
    private final CommandDispatcher<FabricClientCommandSource> dispatcher;

    @Doc("The command build context providing registry access for command arguments.")
    private final CommandBuildContext registryAccess;

    /**
     * Creates a Fabric client command registration event wrapper.
     *
     * @param dispatcher client command dispatcher for registering client-side commands
     * @param registryAccess command build context with registry access
     */
    @Doc("Creates a Fabric client command registration event wrapper.")
    @Param(name = "dispatcher", value = "client command dispatcher targeting FabricClientCommandSource")
    @Param(name = "registryAccess", value = "command build context providing registry access")
    public ClientCommandRegistryEventJS(
            CommandDispatcher<FabricClientCommandSource> dispatcher,
            CommandBuildContext registryAccess) {
        this.dispatcher = dispatcher;
        this.registryAccess = registryAccess;
    }

    /**
     * Returns the client Brigadier command dispatcher.
     *
     * @return client command dispatcher targeting {@link FabricClientCommandSource}
     */
    @Doc("Gets the client Brigadier command dispatcher.")
    @Return("the CommandDispatcher<FabricClientCommandSource> used to register client commands")
    public CommandDispatcher<FabricClientCommandSource> getDispatcher() {
        return dispatcher;
    }

    /**
     * Returns the command build context providing registry access.
     *
     * @return command build context
     */
    @Doc("Gets the command build context providing registry access for argument types.")
    @Return("the CommandBuildContext for this registration pass")
    public CommandBuildContext getRegistryAccess() {
        return registryAccess;
    }
}
