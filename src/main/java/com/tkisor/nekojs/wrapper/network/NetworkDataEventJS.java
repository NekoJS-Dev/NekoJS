package com.tkisor.nekojs.wrapper.network;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

/**
 * Script network event object: scripts receive the {@link com.tkisor.nekojs.network.NekoScriptPayload} sent by the
 * other side through {@link com.tkisor.nekojs.bindings.event.NetworkEvents}.
 *
 * <ul>
 *   <li>{@code channel}: the script-defined channel name, matching the sending side
 *       {@code Network.sendToServer(channel, data)}.</li>
 *   <li>{@code data}: the payload, a {@link CompoundTag}. Scripts can read and write NBT.</li>
 *   <li>{@code player}: the player who sent the packet on the SERVER side; {@code null} on the CLIENT side
 *       (the packet came from the server).</li>
 * </ul>
 */
public final class NetworkDataEventJS {
    private final String channel;
    private final CompoundTag data;
    @Nullable
    private final ServerPlayer sender;

    public NetworkDataEventJS(String channel, CompoundTag data, @Nullable ServerPlayer sender) {
        this.channel = channel;
        this.data = data;
        this.sender = sender;
    }

    public String getChannel() {
        return channel;
    }

    public CompoundTag getData() {
        return data;
    }

    @Nullable
    public ServerPlayer getPlayer() {
        return sender;
    }
}
