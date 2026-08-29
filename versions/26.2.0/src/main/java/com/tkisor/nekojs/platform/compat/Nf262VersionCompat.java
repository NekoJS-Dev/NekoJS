package com.tkisor.nekojs.platform.compat;

import net.minecraft.world.entity.EntityTypes;

/**
 * 26.2 侧 {@link McVersionCompat.Impl}：{@code LIGHTNING_BOLT} 常量移到了
 * {@code EntityTypes}（26.1 在 {@code EntityType}）。
 * 注册：{@code META-INF/services/com.tkisor.nekojs.platform.compat.McVersionCompat$Impl}。
 */
public final class Nf262VersionCompat implements McVersionCompat.Impl {

    @Override
    public net.minecraft.world.entity.EntityType<?> lightningBoltType() {
        return EntityTypes.LIGHTNING_BOLT;
    }
}
