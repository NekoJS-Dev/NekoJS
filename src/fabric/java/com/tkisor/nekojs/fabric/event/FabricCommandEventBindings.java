package com.tkisor.nekojs.fabric.event;

import com.tkisor.nekojs.bindings.event.CommandEvents;
import com.tkisor.nekojs.wrapper.event.server.CommandRegistryEventJS;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;

/**
 * Fabric bridge for the command registration event: CommandRegistrationCallback (fires while the
 * server command tree is built, the same timing as NeoForge's RegisterCommandsEvent) ->
 * {@code CommandEvents.register} bus.
 * Scripts in server_scripts call
 * {@code CommandEvents.register(event => event.dispatcher.register(...))}.
 */
public final class FabricCommandEventBindings {

    private FabricCommandEventBindings() {}

    public static void register() {
        FabricEventBusBridge.create().bind(
                CommandEvents.REGISTER,
                listener -> CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                        listener.accept(new CommandRegistryEventJS(dispatcher, registryAccess))));
    }
}
