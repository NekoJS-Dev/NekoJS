package com.tkisor.nekojs.wrapper.network;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

/**
 * 脚本网络事件对象：脚本通过 {@link com.tkisor.nekojs.bindings.event.NetworkEvents} 接收对端发来的
 * {@link com.tkisor.nekojs.network.NekoScriptPayload}。
 *
 * <ul>
 *   <li>{@code channel}：脚本自定义的通道名，与发送侧 {@code Network.sendToServer(channel, data)} 一致。</li>
 *   <li>{@code data}：载荷，{@link CompoundTag}。脚本可读写 NBT。</li>
 *   <li>{@code player}：SERVER 端是发送该包的玩家；CLIENT 端为 {@code null}（包来自服务端）。</li>
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
