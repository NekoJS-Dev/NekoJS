package com.tkisor.nekojs.bindings.event;

import com.mojang.brigadier.CommandDispatcher;
import com.tkisor.nekojs.api.ScriptType;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.server.MinecraftServer;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import java.util.Map;
import java.util.WeakHashMap;

public final class NeoForgeCommandRegistration {
    private static final Map<CommandDispatcher<CommandSourceStack>, Registration> PENDING = new WeakHashMap<>();

    private NeoForgeCommandRegistration() {}

    private record Registration(Commands.CommandSelection environment, CommandBuildContext registries) {}

    static boolean capture(RegisterCommandsEvent event) {
        synchronized (PENDING) {
            PENDING.put(event.getDispatcher(), new Registration(event.getCommandSelection(), event.getBuildContext()));
        }
        return true;
    }

    static boolean isInstalled(RegisterCommandsEvent event) {
        return event instanceof InstalledRegistration;
    }

    public static void postInstalled(MinecraftServer server) {
        if (!server.isSameThread()) return;
        var dispatcher = server.getCommands().getDispatcher();
        Registration registration;
        synchronized (PENDING) {
            registration = PENDING.remove(dispatcher);
        }
        if (registration != null && CommandEvents.REGISTER.hasStableModuleListeners(ScriptType.SERVER)) {
            CommandEvents.REGISTER.postStableModuleNotification(
                    new InstalledRegistration(dispatcher, registration), ScriptType.SERVER);
        }
    }

    private static final class InstalledRegistration extends RegisterCommandsEvent {
        private InstalledRegistration(CommandDispatcher<CommandSourceStack> dispatcher, Registration registration) {
            super(dispatcher, registration.environment(), registration.registries());
        }
    }
}
