package com.tkisor.nekojs.fabric.event;

import com.tkisor.nekojs.bindings.event.CommandEvents;
import com.tkisor.nekojs.wrapper.event.server.CommandRegistryEventJS;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;

/**
 * 命令注册事件的 fabric 桥：CommandRegistrationCallback（服务器命令树构建期，与
 * NeoForge RegisterCommandsEvent 时机等价）→ {@code CommandEvents.register} 总线。
 * 脚本在 server_scripts 里 {@code CommandEvents.register(event => event.dispatcher.register(...))}。
 */
public final class FabricCommandEventBindings {

    private FabricCommandEventBindings() {}

    public static void register() {
        FabricEventBusBridge.create().bind(
                CommandEvents.REGISTER,
                listener -> CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                        listener.accept(new CommandRegistryEventJS(dispatcher, registryAccess))));
    }
}
