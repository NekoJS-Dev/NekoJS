package com.tkisor.nekojs.network;

import com.tkisor.nekojs.bindings.event.NetworkEvents;
import com.tkisor.nekojs.wrapper.network.NetworkDataEventJS;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

/**
 * Handles {@link NekoScriptPayload} on both sides. Incoming messages run on a
 * network thread, so delivery to script listeners is scheduled on the main
 * thread via {@code IThreadListener.addScheduledTask} before posting to
 * {@link NetworkEvents}.
 */
public class NetworkMessageHandler implements IMessageHandler<NekoScriptPayload, IMessage> {

    @Override
    public IMessage onMessage(NekoScriptPayload message, MessageContext ctx) {
        final String channel = message.channel();
        final NBTTagCompound data = message.data();

        switch (ctx.side) {
            case SERVER -> {
                EntityPlayerMP sender = ctx.getServerHandler().player;
                MinecraftServer server = FMLCommonHandler.instance().getMinecraftServerInstance();
                if (server != null) {
                    server.addScheduledTask(() ->
                            NetworkEvents.SERVER.post(new NetworkDataEventJS(channel, data, sender), channel));
                }
            }
            case CLIENT -> Minecraft.getMinecraft().addScheduledTask(() ->
                    NetworkEvents.CLIENT.post(new NetworkDataEventJS(channel, data, null), channel));
        }
        return null;
    }
}
