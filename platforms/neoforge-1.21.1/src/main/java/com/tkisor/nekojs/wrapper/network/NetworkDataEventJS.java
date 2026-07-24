package com.tkisor.nekojs.wrapper.network;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

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
