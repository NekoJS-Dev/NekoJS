package com.tkisor.nekojs.fabric.event;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.ParseResults;
import com.tkisor.nekojs.api.ScriptType;
import com.tkisor.nekojs.bindings.event.CommandEventThread;
import com.tkisor.nekojs.bindings.event.CommandEvents;
import com.tkisor.nekojs.wrapper.event.server.CommandRegistryEventJS;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.MinecraftServer;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * Fabric bridge for command events: {@code CommandRegistrationCallback} (fires while the server
 * command tree is built, matching NeoForge's {@code RegisterCommandsEvent}) ->
 * {@code CommandEvents.register}, and {@code MixinCommandsPerform} -> {@code CommandEvents.command}.
 */
public final class FabricCommandEventBindings {

    private static final Map<CommandDispatcher<CommandSourceStack>, Registration> pendingRegistrations = new WeakHashMap<>();

    private record Registration(CommandBuildContext registries, String environment, boolean beforeServer) {}

    private FabricCommandEventBindings() {}

    /** Registers Fabric event callbacks onto the corresponding NekoJS script event buses. */
    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            String name = environment == null ? "" : environment.name();
            boolean beforeServer = FabricServerEventBindings.currentServer() == null;
            synchronized (pendingRegistrations) {
                pendingRegistrations.put(dispatcher, new Registration(registryAccess, name, beforeServer));
            }
            if (!beforeServer && CommandEvents.REGISTER.hasListeners()) {
                CommandEvents.REGISTER.post(new CommandRegistryEventJS(dispatcher, registryAccess, name));
            }
        });
    }

    static void postInitialRegistration(MinecraftServer server) {
        postInstalledRegistration(server);
    }

    public static void postInstalledRegistration(MinecraftServer server) {
        if (!server.isSameThread()) return;
        var dispatcher = server.getCommands().getDispatcher();
        Registration registration;
        synchronized (pendingRegistrations) {
            registration = pendingRegistrations.remove(dispatcher);
        }
        if (registration != null && CommandEvents.REGISTER.hasListeners()) {
            if (registration.beforeServer()) {
                CommandEvents.REGISTER.post(new CommandRegistryEventJS(dispatcher, registration.registries(), registration.environment()));
            }
            CommandEvents.REGISTER.postStableModuleNotification(
                    new InstalledRegistration(dispatcher, registration.registries(), registration.environment()), ScriptType.SERVER);
        }
    }

    public static boolean isInstalledRegistration(CommandRegistryEventJS event) {
        return event instanceof InstalledRegistration;
    }

    private static final class InstalledRegistration extends CommandRegistryEventJS {
        private InstalledRegistration(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext registries, String environment) {
            super(dispatcher, registries, environment);
        }
    }

    /**
     * Dispatches {@code CommandEvents.command} before Brigadier executes a parsed command on the server.
     *
     * @return {@code true} if a script listener cancelled execution
     */
    public static boolean postCommand(ParseResults<CommandSourceStack> parseResults, String command) {
        if (!CommandEvents.COMMAND.hasListeners() || !CommandEventThread.isCurrent(parseResults)) {
            return false;
        }
        return CommandEvents.COMMAND.post(FabricStableEventProjections.commandExecute(parseResults, command));
    }
}
