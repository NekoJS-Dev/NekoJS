package com.tkisor.nekojs.wrapper.event.server;

import com.mojang.brigadier.CommandDispatcher;
import lombok.Getter;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;

/**
 * 命令注册事件的中立 payload（成员 {@code event.dispatcher}——向其注册脚本命令，
 * 如 {@code event.dispatcher.register(Commands.literal('mymod:hello').executes(...))}；
 * {@code registryAccess} 供参数类型引用）。时机：服务器命令树构建期
 * （NeoForge RegisterCommandsEvent / fabric CommandRegistrationCallback）。
 */
public class CommandRegistryEventJS {

    @Getter
    private final CommandDispatcher<CommandSourceStack> dispatcher;

    @Getter
    private final CommandBuildContext registryAccess;

    public CommandRegistryEventJS(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext registryAccess) {
        this.dispatcher = dispatcher;
        this.registryAccess = registryAccess;
    }
}
