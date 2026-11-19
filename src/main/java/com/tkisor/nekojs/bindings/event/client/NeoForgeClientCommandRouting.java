package com.tkisor.nekojs.bindings.event.client;

import com.tkisor.nekojs.client.ClientEventThread;
import net.minecraft.commands.CommandBuildContext;
import net.neoforged.bus.api.Event;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;

import java.util.function.Supplier;

public final class NeoForgeClientCommandRouting {
    private static final ThreadLocal<PacketRegistration> PACKET = new ThreadLocal<>();

    private NeoForgeClientCommandRouting() {}

    static boolean isVersioned(RegisterClientCommandsEvent event) {
        return !(event instanceof PacketComplete);
    }

    static boolean isStable(RegisterClientCommandsEvent event) {
        var packet = PACKET.get();
        return ClientEventThread.isCurrent() && (packet == null || packet.registration != event);
    }

    public static void duringPacket(Runnable handlePacket) {
        if (!ClientEventThread.isCurrent()) {
            handlePacket.run();
            return;
        }
        var previous = PACKET.get();
        var packet = new PacketRegistration();
        PACKET.set(packet);
        try {
            handlePacket.run();
        } finally {
            if (previous == null) PACKET.remove();
            else PACKET.set(previous);
        }
        if (packet.registration != null && ClientEvents.COMMAND_REGISTRY.hasListeners()) {
            ClientEvents.COMMAND_REGISTRY.post(new PacketComplete(packet.registration));
        }
    }

    public static <T> T duringMerge(CommandBuildContext context, Supplier<T> merge) {
        var packet = PACKET.get();
        if (packet == null) return merge.get();
        var previous = packet.context;
        packet.context = context;
        try {
            return merge.get();
        } finally {
            packet.context = previous;
        }
    }

    public static void captureRegistration(Event event) {
        var packet = PACKET.get();
        if (packet != null && packet.context != null && event instanceof RegisterClientCommandsEvent registration
                && registration.getBuildContext() == packet.context) {
            packet.context = null;
            packet.registration = registration;
        }
    }

    private static final class PacketRegistration {
        private CommandBuildContext context;
        private RegisterClientCommandsEvent registration;
    }

    private static final class PacketComplete extends RegisterClientCommandsEvent {
        private PacketComplete(RegisterClientCommandsEvent event) {
            super(event.getDispatcher(), event.getBuildContext());
        }
    }
}
