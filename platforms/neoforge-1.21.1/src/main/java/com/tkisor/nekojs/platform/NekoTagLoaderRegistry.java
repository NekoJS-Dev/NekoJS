package com.tkisor.nekojs.platform;

import net.minecraft.resources.ResourceKey;

/**
 * {@code TagLoaderMixin} 实现、{@code TagManagerMixin} 写入的 duck 接口：
 * 跨 mixin 类给 TagLoader 实例附加注册表 key（与 inject Extension 的接口注入同思路）。
 * 必须放在 mixin 配置包之外——Sponge 禁止从 mixin 包直接加载非 mixin 类
 * （IllegalClassLoadError），故归入 platform 内部桥包。
 */
public interface NekoTagLoaderRegistry {

    void nekojs$setRegistryKey(ResourceKey<?> registryKey);
}
