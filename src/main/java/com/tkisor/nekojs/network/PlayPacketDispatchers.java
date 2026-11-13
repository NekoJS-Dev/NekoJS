package com.tkisor.nekojs.network;

import com.tkisor.nekojs.NekoJS;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

/**
 * Installation point for {@link PlayPacketDispatcher}, shaped like {@code Platform} and
 * {@code Diagnostics}: a platform calls {@link #install} at init and business code gets the
 * dispatcher with {@link #get}.
 *
 * <p>Before installation {@link #NOOP} is used: every send is dropped silently and only the first
 * one logs a WARN. That way nothing interrupts scripts, and a platform that forgets to install does
 * not fail completely silently.
 */
public final class PlayPacketDispatchers {

    /** Fallback used before installation: drops the send and logs a warning once. */
    public static final PlayPacketDispatcher NOOP = new PlayPacketDispatcher() {
        @Override
        public void sendToPlayer(ServerPlayer player, CustomPacketPayload payload) {
            warnOnce(payload);
        }

        @Override
        public void sendToAllPlayers(CustomPacketPayload payload) {
            warnOnce(payload);
        }

        @Override
        public void sendToPlayersTrackingEntityAndSelf(Entity entity, CustomPacketPayload payload) {
            warnOnce(payload);
        }
    };

    private static volatile PlayPacketDispatcher current = NOOP;
    private static volatile boolean warned;

    private PlayPacketDispatchers() {}

    public static void install(PlayPacketDispatcher dispatcher) {
        current = dispatcher == null ? NOOP : dispatcher;
    }

    public static PlayPacketDispatcher get() {
        return current;
    }

    private static void warnOnce(CustomPacketPayload payload) {
        if (warned) return;
        warned = true;
        NekoJS.LOGGER.warn("No play packet dispatcher installed; dropping {} (and further payloads silently)",
                payload.type().id());
    }
}
