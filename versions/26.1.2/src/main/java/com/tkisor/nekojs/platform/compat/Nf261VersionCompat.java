package com.tkisor.nekojs.platform.compat;

import net.minecraft.world.entity.EntityType;

/**
 * 26.1 侧 {@link McVersionCompat.Impl}：{@code LIGHTNING_BOLT} 常量仍在
 * {@link EntityType}（26.2 移到了 {@code EntityTypes}）。
 * 注册：{@code META-INF/services/com.tkisor.nekojs.platform.compat.McVersionCompat$Impl}。
 */
public final class Nf261VersionCompat implements McVersionCompat.Impl {

    @Override
    public EntityType<?> lightningBoltType() {
        return EntityType.LIGHTNING_BOLT;
    }
}
