package com.tkisor.nekojs.api.inject;

import net.minecraft.nbt.CompoundTag;

/**
 * 实体持久化数据容器的 duck 接口：fabric 由 {@code NekoEntityPDataMixin} 实现到
 * {@code Entity} 上，NeoForge 侧不需要它（{@code Entity#getPersistentData()} 原生存在）。
 * 平台桥用它把两边的持久化读写收敛成同一份代码。
 */
public interface NekoEntityPData {

    /** 实体的 NekoJS 持久化数据根（可变，写入即生效；随实体存档读写）。 */
    CompoundTag neko$getPDataRoot();
}
