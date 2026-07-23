package com.tkisor.nekojs.wrapper.network;

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
public class NetworkDataEventJS {

    private final String channel;
    private final NBTTagCompound data;
    @Nullable
    private final EntityPlayer sender;

    public NetworkDataEventJS(String channel, NBTTagCompound data, @Nullable EntityPlayer sender) {
        this.channel = channel;
        this.data = data;
        this.sender = sender;
    }

    public String getChannel() {
        return channel;
    }

    public NBTTagCompound getData() {
        return data;
    }

    /**
     * The player that sent the message, or {@code null} on the client side.
     */
    @Nullable
    public EntityPlayerMP getPlayer() {
        return sender instanceof EntityPlayerMP ? (EntityPlayerMP) sender : null;
    }
}
