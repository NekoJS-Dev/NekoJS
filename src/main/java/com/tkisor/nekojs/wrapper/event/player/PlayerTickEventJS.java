package com.tkisor.nekojs.wrapper.event.player;

import lombok.Getter;
import net.minecraft.world.entity.player.Player;

/**
 * 玩家 tick 事件的中立 payload（tickPre / tickPost 共用；成员 {@code event.player}，
 * 与 NeoForge 原生 PlayerTickEvent 的 getPlayer() 同形）。
 */
public class PlayerTickEventJS {

    @Getter
    private final Player player;

    public PlayerTickEventJS(Player player) {
        this.player = player;
    }
}
