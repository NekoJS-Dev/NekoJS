package com.tkisor.nekojs.wrapper.event.player;

import lombok.Getter;
import net.minecraft.server.level.ServerPlayer;

/**
 * 玩家实体克隆事件的中立 payload（死亡重生/末地返回时的数据拷贝点；成员对齐 NeoForge
 * {@code PlayerEvent.Clone}：{@code event.player} 为新实体，{@code event.original} 为
 * 旧实体；{@code alive} 表示旧实体是否仍存活——fabric COPY_FROM 的三参全量）。
 */
public class PlayerCloneEventJS {

    @Getter
    private final ServerPlayer player;

    @Getter
    private final ServerPlayer original;

    @Getter
    private final boolean alive;

    public PlayerCloneEventJS(ServerPlayer player, ServerPlayer original, boolean alive) {
        this.player = player;
        this.original = original;
        this.alive = alive;
    }
}
