// fabric 节点孪生：共享树同名接口整文件 `//? if neoforge` 守卫（总线直传 NeoForge 原生
// RegisterCommandsEvent/CommandEvent）。fabric 侧 REGISTER 由 FabricCommandEventBindings 挂
// CommandRegistrationCallback 投递；command（执行拦截）需 mixin Commands#performCommand，
// 落地后本孪生随之扩。
package com.tkisor.nekojs.bindings.event;

import com.tkisor.nekojs.api.event.EventBusJS;
import com.tkisor.nekojs.api.event.EventGroup;
import com.tkisor.nekojs.wrapper.event.server.CommandRegistryEventJS;

/** 命令事件组（server 脚本）：命令注册（fabric 子集）。 */
public interface CommandEvents {
    EventGroup GROUP = EventGroup.of("CommandEvents");

    EventBusJS<CommandRegistryEventJS, Void> REGISTER =
            GROUP.server("register", CommandRegistryEventJS.class);
}
