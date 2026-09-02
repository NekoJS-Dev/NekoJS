package com.tkisor.nekojs.wrapper.event.player;

import lombok.Getter;
import net.minecraft.server.level.ServerPlayer;

/**
 * 玩家重生事件的中立 payload（成员 {@code event.player} 为重生后的实体；
 * {@code oldPlayer} 为重生前实体，{@code alive} 表示旧实体是否仍存活——末地返回式
 * 重生为 true，死亡重生为 false；fabric AFTER_RESPAWN 的三参全量）。
 */
public class PlayerRespawnEventJS {

    @Getter
    private final ServerPlayer player;

    @Getter
    private final ServerPlayer oldPlayer;

    @Getter
    private final boolean alive;

    public PlayerRespawnEventJS(ServerPlayer player, ServerPlayer oldPlayer, boolean alive) {
        this.player = player;
        this.oldPlayer = oldPlayer;
        this.alive = alive;
    }
}
