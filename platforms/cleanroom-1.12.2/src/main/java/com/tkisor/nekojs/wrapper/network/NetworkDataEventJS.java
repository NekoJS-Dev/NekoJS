package com.tkisor.nekojs.wrapper.network;

import com.tkisor.nekojs.api.annotation.Doc;
import com.tkisor.nekojs.api.annotation.Return;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import org.jetbrains.annotations.Nullable;

/**
 * Event object delivered to script listeners via {@code NetworkEvents.server}
 * and {@code NetworkEvents.client}.
 *
 * <p>On the server side {@link #getPlayer()} returns the {@link EntityPlayerMP}
 * that sent the message; on the client side it is {@code null} (no sender
 * context available).
 */
@Doc("Network message event delivered to NetworkEvents.server / NetworkEvents.client listeners.")
public class NetworkDataEventJS {

    private final String channel;
    private final NBTTagCompound data;
    @Nullable
    private final EntityPlayer sender;

    /** Wraps a received network message. */
    public NetworkDataEventJS(String channel, NBTTagCompound data, @Nullable EntityPlayer sender) {
        this.channel = channel;
        this.data = data;
        this.sender = sender;
    }

    /** The channel the message was sent on. */
    @Doc("Gets the channel name the message was sent on.")
    @Return("the channel string used for dispatch")
    public String getChannel() {
        return channel;
    }

    /** The message payload. */
    @Doc("Gets the message payload.")
    @Return("the NBTTagCompound payload")
    public NBTTagCompound getData() {
        return data;
    }

    /**
     * The player that sent the message, or {@code null} on the client side.
     */
    @Doc("Gets the player that sent the message.")
    @Return("the sending EntityPlayerMP on the server, or null on the client")
    @Nullable
    public EntityPlayerMP getPlayer() {
        return sender instanceof EntityPlayerMP ? (EntityPlayerMP) sender : null;
    }
}
