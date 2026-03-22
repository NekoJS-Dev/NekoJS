package com.tkisor.nekojs.wrapper.entity.client;

import com.tkisor.nekojs.wrapper.entity.PlayerWrapper;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;

public class LocalPlayerWrapper extends PlayerWrapper {

    public LocalPlayerWrapper(LocalPlayer player) {
        super(player);
    }

    private LocalPlayer getLocalPlayer() {
        return (LocalPlayer) super.raw;
    }

    /**
     * 让客户端玩家模拟在聊天框发送消息/执行命令 (这会发包给服务端)
     * JS 侧调用: event.player.chat("/help")
     */
    public void chat(String message) {
        if (message.startsWith("/")) {
            getLocalPlayer().connection.sendCommand(message.substring(1));
        } else {
            getLocalPlayer().connection.sendChat(message);
        }
    }

    /**
     * 获取玩家视角的 X 轴旋转 (俯仰角)
     */
    public float getPitch() {
        return getLocalPlayer().getXRot();
    }

    /**
     * 模拟玩家按下挥手动作
     */
    public void swingArm() {
        getLocalPlayer().swing(InteractionHand.MAIN_HAND);
    }

    @Override
    public LocalPlayer unwrap() {
        return (LocalPlayer) super.unwrap();
    }
}