package com.tkisor.nekojs.fabric.mixin;

import net.minecraft.network.Connection;
import net.minecraft.server.network.ServerCommonPacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * 暴露配置/游玩阶段监听器持有的 {@link Connection}：vanilla 里是 protected 字段，
 * fabric networking API 也不转发（NeoForge 侧的 {@code IPayloadContext#connection()} 有）。
 * 包分发需要它做内存连接判定（单人/局域网主机自己的连接不参与同步）。
 */
@Mixin(ServerCommonPacketListenerImpl.class)
public interface ServerCommonPacketListenerAccessor {

    @Accessor("connection")
    Connection nekojs$connection();
}
