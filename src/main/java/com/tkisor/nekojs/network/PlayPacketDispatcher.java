package com.tkisor.nekojs.network;

import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

/**
 * play 阶段服务器→客户端发送面的中立通道：由各平台在初始化时装配一次
 * （NeoForge = {@code PacketDistributor}，fabric = {@code ServerPlayNetworking} + {@code PlayerLookup}），
 * 共享树的业务侧（{@code ClientDataSyncJS} / {@code PDataSyncService} 等）只依赖本接口。
 *
 * <p>未装配时全部发送静默丢弃（专用服务器早期 / 单元测试环境），不抛异常——发送失败不应
 * 打断脚本执行。
 */
public interface PlayPacketDispatcher {

    /** 发给单个玩家。 */
    void sendToPlayer(ServerPlayer player, CustomPacketPayload payload);

    /** 发给全部在线玩家。 */
    void sendToAllPlayers(CustomPacketPayload payload);

    /** 发给跟踪该实体的玩家（实体自身是玩家时也包含它）。 */
    void sendToPlayersTrackingEntityAndSelf(Entity entity, CustomPacketPayload payload);

    /** 未装配时的空实现。 */
    PlayPacketDispatcher NOOP = new PlayPacketDispatcher() {
        @Override
        public void sendToPlayer(ServerPlayer player, CustomPacketPayload payload) {}

        @Override
        public void sendToAllPlayers(CustomPacketPayload payload) {}

        @Override
        public void sendToPlayersTrackingEntityAndSelf(Entity entity, CustomPacketPayload payload) {}
    };

    static void install(PlayPacketDispatcher dispatcher) {
        Holder.current = dispatcher == null ? NOOP : dispatcher;
    }

    static PlayPacketDispatcher get() {
        return Holder.current;
    }

    /** 接口不能有可变静态字段，用嵌套类持有。 */
    final class Holder {
        private static volatile PlayPacketDispatcher current = NOOP;

        private Holder() {}
    }
}
