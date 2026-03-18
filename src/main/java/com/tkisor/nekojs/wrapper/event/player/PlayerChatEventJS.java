package com.tkisor.nekojs.wrapper.event.player;

import com.tkisor.nekojs.bindings.event.NekoEvent;
import com.tkisor.nekojs.wrapper.entity.PlayerWrapper;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.event.ServerChatEvent;

public class PlayerChatEventJS implements NekoEvent {

    private final ServerChatEvent rawEvent;

    public PlayerChatEventJS(ServerChatEvent rawEvent) {
        this.rawEvent = rawEvent;
    }

    /**
     * 获取发送消息的玩家
     * JS 侧调用: event.player 或 event.getPlayer()
     */
    public PlayerWrapper getPlayer() {
        return new PlayerWrapper(rawEvent.getPlayer());
    }

    /**
     * 获取玩家发送的原始文本
     * JS 侧调用: event.message 或 event.getMessage()
     */
    public String getMessage() {
        return rawEvent.getRawText();
    }

    /**
     * 篡改玩家发送的消息
     * JS 侧调用: event.setMessage("被篡改的消息")
     */
    public void setMessage(String newMessage) {
        rawEvent.setMessage(Component.literal(newMessage));
    }

    /**
     * 撤回/取消这条消息（其他玩家将看不到）
     * JS 侧调用: event.cancel()
     */
    public void cancel() {
        rawEvent.setCanceled(true);
    }

    /**
     * 检查事件是否已经被取消
     * JS 侧调用: event.isCanceled()
     */
    public boolean isCanceled() {
        return rawEvent.isCanceled();
    }
}