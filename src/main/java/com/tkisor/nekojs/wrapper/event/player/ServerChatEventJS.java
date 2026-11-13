package com.tkisor.nekojs.wrapper.event.player;

import lombok.Getter;
import net.minecraft.world.entity.player.Player;

/**
 * Loader-neutral payload for the chat event (first used by the fabric bridge). The members
 * are {@code event.player} / {@code event.username} / {@code event.message}, all portable
 * strings or entities.
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
