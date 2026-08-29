package com.tkisor.nekojs.wrapper.registry.gen;

import java.util.function.Function;

import net.minecraft.core.Registry;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;

/**
 * 注册表对象类型（ADR-0004 决策 4）：某注册表下的一个<b>命名 builder 工厂</b>——
 * 脚本端 {@code event.custom(id, '<name>', cb)} 按名取用；每注册表另有一个
 * default 类型（{@code event.<registry>(id, cb)} 免名直达）。
 *
 * <p>类型层冲突策略为 overrideWarn（内置可被第三方覆盖 + warn，ADR-0004 决策 4；
 * 对象层同 id 重复注册为 failFast，由事件仓库执行）。
 *
 * @param name    类型名（注册表内唯一，如 {@code basic} / {@code tool}）
 * @param registry 归属注册表键
 * @param factory builder 工厂（接收对象 id，产出 {@link RegistryObjectBuilder}）
 * @param <B>     builder 类型
 */
public record RegistryObjectType<B extends RegistryObjectBuilder<?>>(String name, ResourceKey<? extends Registry<?>> registry, Function<Identifier, B> factory) {
}
