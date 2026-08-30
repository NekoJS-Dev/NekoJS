package com.tkisor.nekojs.network;

import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

/**
 * play 阶段服务器→客户端发送面的中立通道：由各平台在初始化时装配一次
 * （NeoForge = {@code PacketDistributor}，fabric = {@code ServerPlayNetworking} + {@code PlayerLookup}），
 * 版本树里的业务代码（{@code ClientDataSyncJS} / {@code PDataSyncService} 等）只依赖本接口。
 *
 * <p>发送失败不应打断脚本执行：未装配（专用服务器早期 / 单元测试）或没有可达目标时静默丢弃，
 * 不抛异常。装配与取用见 {@link PlayPacketDispatchers}。
 */
public interface PlayPacketDispatcher {

    /** 发给单个玩家。 */
    void sendToPlayer(ServerPlayer player, CustomPacketPayload payload);

    /** 发给全部在线玩家。 */
    void sendToAllPlayers(CustomPacketPayload payload);

    /** 发给跟踪该实体的玩家（实体自身是玩家时也包含它）。 */
    void sendToPlayersTrackingEntityAndSelf(Entity entity, CustomPacketPayload payload);
}
