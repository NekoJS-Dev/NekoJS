package com.tkisor.nekojs.platform.compat;

import net.minecraft.world.entity.EntityType;

/**
 * fabric 26.1 侧 {@link McVersionCompat.Impl}（票 31）：与 NeoForge 侧
 * {@code Nf261VersionCompat} 同构——{@code LIGHTNING_BOLT} 常量 26.1 仍在
 * {@link EntityType}、26.2 移到了 {@code EntityTypes}，raw root（src/fabric）不经
 * stonecutter 预处理，版本漂移常量只能按节点 override 落位（交接单 W8：版本差异由
 * compat facade 或 versions/&lt;node&gt; override 承担）。
 *
 * <p>背景：共享树 {@code inject.MixinLevel} 让 Level 实现 {@code LevelExtension}，
 * {@code neko$spawnLightning} 经本门面取闪电实体类型；此前 fabric 两节点零 provider，
 * 该调用会触发 {@code McVersionCompat} 静态初始化的 ServiceLoader 失败（潜伏崩溃，
 * d.ts 又已宣传该方法）。本 impl 把它转为可用。
 *
 * <p>注册：{@code META-INF/services/com.tkisor.nekojs.platform.compat.McVersionCompat$Impl}
 *（与 NeoForge 节点同机制）。类名与 26.2 侧刻意不同——drift 比较对不允许同名不同体。
 */
public final class Fabric261VersionCompat implements McVersionCompat.Impl {

    @Override
    public EntityType<?> lightningBoltType() {
        return EntityType.LIGHTNING_BOLT;
    }
}
