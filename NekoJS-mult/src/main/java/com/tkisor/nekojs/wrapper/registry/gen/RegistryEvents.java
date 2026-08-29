package com.tkisor.nekojs.wrapper.registry.gen;

import com.tkisor.nekojs.api.event.EventBusJS;
import com.tkisor.nekojs.api.event.EventGroup;

/**
 * 通用注册入口绑定（ADR-0006 单一入口）：{@code register} 总线挂在与旧类型化入口
 * 同名的 {@code RegistryEvents} 组下（{@code EventGroupRegistry} 对同名组做合并，
 * 新旧入口在 Builder 重写完成前共存）。由 {@link NekoRegistryPointsPlugin} 注册，
 * 平台 adapter 在首个注册表 pass 前 post 一次。
 *
 * <pre>
 * RegistryEvents.register(event =&gt; {
 *     event.item('mymod:ruby', b =&gt; { b.maxStackSize = 16 })
 * })
 * </pre>
 */
public final class RegistryEvents {

    /** 组名与旧入口共用（同名组注册即合并）。 */
    public static final EventGroup GROUP = EventGroup.of("RegistryEvents");

    /** 单一收集入口（STARTUP 脚本监听；每轮游戏启动 post 一次）。 */
    public static final EventBusJS<RegistryEventJS, Void> REGISTER = GROUP.startup("register", RegistryEventJS.class);

    private RegistryEvents() {}
}
