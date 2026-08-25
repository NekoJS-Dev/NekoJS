package com.tkisor.nekojs.platform.compat;

import net.minecraft.world.entity.EntityType;

import java.util.ServiceLoader;

/**
 * 26.x 版本间编译期符号差异的通用侧门面（W5/A8 step 2）。
 *
 * <p>26.1 与 26.2 之间存在纯编译期改名（如 {@code EntityType.LIGHTNING_BOLT} →
 * {@code EntityTypes.LIGHTNING_BOLT}），共享代码直接引用任何一侧都会在另一侧编不过。
 * 统一改走本门面：实现在各版本模块（{@code Nf261VersionCompat} / {@code Nf262VersionCompat}，
 * 类名刻意不同——26.1↔26.2 的 drift 比较对不允许同名不同体），经
 * {@code META-INF/services} 由 ServiceLoader 在首次取用时解析。
 *
 * <p>本类只允许引用两端都存在且<strong>服务端可加载</strong>的类型
 * （{@code api.inject.LevelExtension} 注入 Level，双端可达）；客户端专属符号走 {@link McClientCompat}。
 * 1.21.1 的 LevelExtension 是独立实现（时间/天气 API 结构不同），不经过本门面，
 * 因此 1.21.1 不提供 {@code McVersionCompat$Impl} 服务条目。
 */
public final class McVersionCompat {

    private static final Impl IMPL = ServiceLoader.load(Impl.class, McVersionCompat.class.getClassLoader())
            .findFirst()
            .orElseThrow(() -> new IllegalStateException(
                    "No McVersionCompat.Impl provider on classpath: expected a per-version impl "
                            + "in neoforge-26.1/neoforge-26.2 (META-INF/services)"));

    private McVersionCompat() {}

    public static Impl get() {
        return IMPL;
    }

    public interface Impl {
        EntityType<?> lightningBoltType();
    }
}
