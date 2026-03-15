package com.tkisor.nekojs.command;

import com.mojang.brigadier.CommandDispatcher;
import com.tkisor.nekojs.NekoJS;
import com.tkisor.nekojs.script.ScriptType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

public final class NekoJSCommands {

    private NekoJSCommands() {
    }

    public static void register(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();

        dispatcher.register(
                Commands.literal("nekojs")
                        .requires(source -> Commands.LEVEL_GAMEMASTERS.check(source.permissions()))

                        .then(Commands.literal("reload")
                                .executes(context -> {
                                    CommandSourceStack source = context.getSource();

                                    source.sendSystemMessage(Component.literal("§e[NekoJS] 正在重载脚本..."));

                                    try {
                                        NekoJS.SCRIPT_MANAGER.reloadScripts(ScriptType.COMMON);
                                        NekoJS.SCRIPT_MANAGER.reloadScripts(ScriptType.SERVER);

                                        source.sendSuccess(() -> Component.literal("§a[NekoJS] ✔ 脚本重载成功！"), true);
                                    } catch (Exception e) {
                                        source.sendFailure(Component.literal("§c[NekoJS] ✖ 重载失败，请查看控制台日志！"));
                                        NekoJS.LOGGER.error("重载脚本时发生致命错误", e);
                                    }

                                    return 1;
                                })
                        )
        );
    }


}