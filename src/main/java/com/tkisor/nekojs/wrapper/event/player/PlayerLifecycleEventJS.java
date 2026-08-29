package com.tkisor.nekojs.wrapper.event.player;

import lombok.Getter;
import net.minecraft.world.entity.player.Player;

/**
 * 玩家进出服事件的中立 payload（loggedIn / loggedOut 共用；成员 {@code event.player}，
 * 与 NeoForge 原生 PlayerEvent 的 getPlayer() 同形——脚本写法跨加载器一致）。
 */
public class PlayerLifecycleEventJS {

    @Getter
    private final Player player;

    public PlayerLifecycleEventJS(Player player) {
        this.player = player;
    }
}
