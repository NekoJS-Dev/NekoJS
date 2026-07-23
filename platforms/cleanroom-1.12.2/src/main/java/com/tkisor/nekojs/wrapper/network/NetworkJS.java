package com.tkisor.nekojs.wrapper.network;

import com.tkisor.nekojs.network.NekoJSNetwork;
import com.tkisor.nekojs.network.NekoScriptPayload;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;

/**
 * 1.12.2 NetworkJS - script-facing network API.
 *
 * <p>Sends {@link NekoScriptPayload} packets over {@link NekoJSNetwork#CHANNEL}.
 * Incoming messages are dispatched to {@link com.tkisor.nekojs.bindings.event.NetworkEvents}
 * listeners by channel name on the receiving side.
 */
public class NetworkJS {

    /**
     * Client-side: send data to server.
     */
    public static void sendToServer(String channel, NBTTagCompound data) {
        NekoJSNetwork.CHANNEL.sendToServer(new NekoScriptPayload(channel, data));
    }

    /**
     * Server-side: send data to a specific player.
     */
    public static void sendToPlayer(EntityPlayerMP player, String channel, NBTTagCompound data) {
        NekoJSNetwork.CHANNEL.sendTo(new NekoScriptPayload(channel, data), player);
    }

    /**
     * Server-side: broadcast to all players.
     */
    public static void sendToAll(String channel, NBTTagCompound data) {
        NekoJSNetwork.CHANNEL.sendToAll(new NekoScriptPayload(channel, data));
    }
}
