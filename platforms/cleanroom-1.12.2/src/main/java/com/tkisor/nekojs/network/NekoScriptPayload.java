package com.tkisor.nekojs.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fml.common.network.ByteBufUtils;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;

/**
 * 1.12.2 script network payload: a channel name plus an NBTTagCompound.
 *
 * <p>Used by {@link NetworkMessageHandler} to deliver script-authored messages
 * between client and server. The empty constructor is required by FML for
 * reflective instantiation when deserializing incoming packets.
 */
public class NekoScriptPayload implements IMessage {

    private String channel;
    private NBTTagCompound data;

    /** Required for reflective deserialization. */
    public NekoScriptPayload() {
        this("", null);
    }

    public NekoScriptPayload(String channel, NBTTagCompound data) {
        this.channel = channel == null ? "" : channel;
        this.data = data != null ? data : new NBTTagCompound();
    }

    public String channel() {
        return channel;
    }

    public NBTTagCompound data() {
        return data;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        this.channel = ByteBufUtils.readUTF8String(buf);
        this.data = ByteBufUtils.readTag(buf);
    }

    @Override
    public void toBytes(ByteBuf buf) {
        ByteBufUtils.writeUTF8String(buf, this.channel);
        ByteBufUtils.writeTag(buf, this.data);
    }
}
