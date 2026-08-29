package com.tkisor.nekojs.wrapper.event.player;

import lombok.Getter;
import net.minecraft.world.entity.player.Player;

/**
 * 聊天事件的中立 payload（fabric 桥首用）。成员对齐契约：{@code event.player} /
 * {@code event.username} / {@code event.message}（均为可移植字符串/实体）。
 */
public class ServerChatEventJS {

    @Getter
    private final Player player;

    @Getter
    private final String username;

    @Getter
    private final String message;

    public ServerChatEventJS(Player player, String message) {
        this.player = player;
        this.username = player.getScoreboardName();
        this.message = message;
    }
}
