package com.tkisor.nekojs.wrapper.network;

import com.tkisor.nekojs.api.annotation.Doc;
import com.tkisor.nekojs.api.annotation.Param;
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
@Doc("Script-facing network API: sends NBT payloads over named channels.")
@Doc("Received messages are dispatched to NetworkEvents.server / NetworkEvents.client listeners by channel name.")
public class NetworkJS {

    /**
     * Client-side: send data to server.
     */
    @Doc("Client-side only: sends data to the server.")
    @Param(name = "channel", value = "the channel name the server listener registered with")
    @Param(name = "data", value = "the NBTTagCompound payload")
    public static void sendToServer(String channel, NBTTagCompound data) {
        NekoJSNetwork.CHANNEL.sendToServer(new NekoScriptPayload(channel, data));
    }

    /**
     * Server-side: send data to a specific player.
     */
    @Doc("Server-side only: sends data to one player.")
    @Param(name = "player", value = "the receiving player")
    @Param(name = "channel", value = "the channel name the client listener registered with")
    @Param(name = "data", value = "the NBTTagCompound payload")
    public static void sendToPlayer(EntityPlayerMP player, String channel, NBTTagCompound data) {
        NekoJSNetwork.CHANNEL.sendTo(new NekoScriptPayload(channel, data), player);
    }

    /**
     * Server-side: broadcast to all players.
     */
    @Doc("Server-side only: broadcasts data to every connected player.")
    @Param(name = "channel", value = "the channel name the client listeners registered with")
    @Param(name = "data", value = "the NBTTagCompound payload")
    public static void sendToAll(String channel, NBTTagCompound data) {
        NekoJSNetwork.CHANNEL.sendToAll(new NekoScriptPayload(channel, data));
    }
}
