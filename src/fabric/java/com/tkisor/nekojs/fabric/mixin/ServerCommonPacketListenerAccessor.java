package com.tkisor.nekojs.fabric.mixin;

import net.minecraft.network.Connection;
import net.minecraft.server.network.ServerCommonPacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Exposes the {@link Connection} held by the configuration/play packet listener. It is a protected
 * field in vanilla, and the Fabric networking API does not forward it (NeoForge's
 * {@code IPayloadContext#connection()} does). Packet distribution needs it to test for a memory
 * connection: the singleplayer/LAN host's own connection does not take part in syncing.
 */
@Mixin(ServerCommonPacketListenerImpl.class)
public interface ServerCommonPacketListenerAccessor {

    @Accessor("connection")
    Connection nekojs$connection();
}
