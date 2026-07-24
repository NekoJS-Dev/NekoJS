package com.tkisor.nekojs.api.inject;

import com.tkisor.nekojs.api.annotation.RemapByPrefix;
import com.tkisor.nekojs.api.data.AttachedData;
import net.minecraft.server.MinecraftServer;

/**
 * @see MinecraftServer
 */
@RemapByPrefix("neko$")
public interface ServerExtension {
    /** 返回挂载到该 server 的内存数据容器；首次访问时由 mixin lazy 创建并触发 {@code attachServerData}。 */
    AttachedData<MinecraftServer> neko$data();
}
