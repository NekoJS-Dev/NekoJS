package com.tkisor.nekojs.network;

import com.tkisor.nekojs.NekoJS;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

/**
 * {@link PlayPacketDispatcher} 的装配点（与 {@code Platform} / {@code Diagnostics} 同形：
 * 平台在初始化时 {@link #install}，业务侧 {@link #get} 取用）。
 *
 * <p>未装配时用 {@link #NOOP}：全部发送静默丢弃，仅首次记一条 WARN——既不打断脚本，
 * 也不让"某个平台忘了装配"变成完全无声的故障。
 */
public final class PlayPacketDispatchers {

    /** 未装配时的兜底实现：丢弃并首次告警。 */
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
