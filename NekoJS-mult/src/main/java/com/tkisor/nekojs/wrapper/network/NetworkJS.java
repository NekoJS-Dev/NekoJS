package com.tkisor.nekojs.wrapper.network;

import com.tkisor.nekojs.network.NekoScriptPayload;
import com.tkisor.nekojs.network.PlayPacketDispatchers;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
//? if neoforge {
//? if >=26 {
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
//?} else {
/*import net.neoforged.neoforge.network.PacketDistributor;
*///?}
//?} else {
/*import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
*///?}

public class NetworkJS {

    /**
     * 【客户端脚本专用】发给服务端
     */
    public static void sendToServer(String channel, CompoundTag data) {
        NekoScriptPayload payload = new NekoScriptPayload(channel, data != null ? data : new CompoundTag());
//? if neoforge {
//? if >=26 {
        ClientPacketDistributor.sendToServer(payload);
//?} else {
/*        PacketDistributor.sendToServer(payload);
*///?}
//?} else {
/*        ClientPlayNetworking.send(payload);
*///?}
    }

    /**
     * 【服务端脚本专用】发给指定玩家
     */
    public static void sendToPlayer(ServerPlayer player, String channel, CompoundTag data) {
        PlayPacketDispatchers.get().sendToPlayer(player, new NekoScriptPayload(channel, data != null ? data : new CompoundTag()));
    }

    /**
     * 【服务端脚本专用】发给所有人
     */
    public static void sendToAll(String channel, CompoundTag data) {
        PlayPacketDispatchers.get().sendToAllPlayers(new NekoScriptPayload(channel, data != null ? data : new CompoundTag()));
    }
}
