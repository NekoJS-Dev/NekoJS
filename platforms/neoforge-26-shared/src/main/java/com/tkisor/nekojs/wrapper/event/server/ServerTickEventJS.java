package com.tkisor.nekojs.wrapper.event.server;

import lombok.Getter;
import net.minecraft.server.MinecraftServer;

/**
 * 统一的服务器 tick 事件 wrapper（跨平台字段名一致）。
 *
 * <p>tick 事件本身没有有用的可变字段，{@code ServerEvents.tickPre}/{@code tickPost}
 * 主要作为脚本信号使用。统一类型让脚本侧签名一致（NeoForge 21.1/26.x 与 Cleanroom）。
 *
 * <p>NeoForge 有独立的 {@code Pre}/{@code Post} 类；Cleanroom 用单一类 + phase 过滤
 * （由 {@code EventBusForgeBridge.bind(bus, filter)} 拆分）。
 */
@Getter
public class ServerTickEventJS {
    private final MinecraftServer server;
    private final boolean hasTime;

    public ServerTickEventJS(MinecraftServer server, boolean hasTime) {
        this.server = server;
        this.hasTime = hasTime;
    }
}
