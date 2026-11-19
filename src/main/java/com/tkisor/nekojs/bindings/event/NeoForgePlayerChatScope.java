package com.tkisor.nekojs.bindings.event;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.ServerChatEvent;

import java.util.function.Supplier;

public final class NeoForgePlayerChatScope {
    private static final ThreadLocal<PacketChat> ACTIVE = new ThreadLocal<>();

    private NeoForgePlayerChatScope() {}

    public static Component decorate(ServerPlayer sender, Component message, Supplier<Component> original) {
        if (sender == null || !ServerLevelEventThread.isCurrent(sender.level()) || !PlayerEvents.CHAT.hasListeners()) {
            return original.get();
        }
        var previous = ACTIVE.get();
        ACTIVE.set(new PacketChat(sender, message));
        try {
            return original.get();
        } finally {
            if (previous == null) ACTIVE.remove();
            else ACTIVE.set(previous);
        }
    }

    static boolean isPacketChat(ServerChatEvent event) {
        var active = ACTIVE.get();
        if (active == null || active.sender != event.getPlayer()) return false;
        if (active.event == null && active.message == event.getMessage()) active.event = event;
        return active.event == event;
    }

    private static final class PacketChat {
        private final ServerPlayer sender;
        private final Component message;
        private ServerChatEvent event;

        private PacketChat(ServerPlayer sender, Component message) {
            this.sender = sender;
            this.message = message;
        }
    }
}
