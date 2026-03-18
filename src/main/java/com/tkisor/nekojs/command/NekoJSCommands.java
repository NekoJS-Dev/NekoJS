package com.tkisor.nekojs.command;

import com.mojang.brigadier.CommandDispatcher;
import com.tkisor.nekojs.NekoJS;
import com.tkisor.nekojs.core.error.NekoErrorTracker;
import com.tkisor.nekojs.core.error.ScriptError;
import com.tkisor.nekojs.core.fs.NekoJSPaths;
import com.tkisor.nekojs.network.ShowErrorScreenPacket;
import com.tkisor.nekojs.script.ScriptType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.IdentifierArgument;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.network.PacketDistributor;

public final class NekoJSCommands {

    private NekoJSCommands() {}

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
                                        NekoErrorTracker.clearAll();

                                        NekoJS.SCRIPT_MANAGER.reloadScripts(ScriptType.COMMON);
                                        NekoJS.SCRIPT_MANAGER.reloadScripts(ScriptType.SERVER);

                                        if (NekoErrorTracker.hasErrors()) {
                                            source.sendFailure(Component.literal("§c[NekoJS] ✖ 重载完毕，但存在 " + NekoErrorTracker.getAllErrors().size() + " 个错误："));

                                            for (ScriptError error : NekoErrorTracker.getAllErrors()) {
                                                String idStr = error.getScript().id.toString();
                                                String pathStr = NekoJSPaths.ROOT.relativize(error.getScript().path).toString().replace('\\', '/');

                                                MutableComponent link = Component.literal("  §4▶ §c" + pathStr + " §8(第 " + error.getLineNumber() + " 行)")
                                                        .withStyle(style -> style
                                                                .withHoverEvent(new HoverEvent.ShowText(Component.literal("§e点击在全屏 UI 中查看堆栈详情")))
                                                                .withClickEvent(new ClickEvent.RunCommand("/nekojs view_error " + idStr))
                                                        );
                                                source.sendSystemMessage(link);
                                            }
                                        } else {
                                            source.sendSuccess(() -> Component.literal("§a[NekoJS] ✔ 脚本完美重载！"), true);
                                        }
                                    } catch (Exception e) {
                                        source.sendFailure(Component.literal("§c[NekoJS] ✖ 重载发生致命崩溃！请查看后台日志。"));
                                    }
                                    return 1;
                                })
                        )

                        .then(Commands.literal("view_error")
                                .then(Commands.argument("scriptId", IdentifierArgument.id())
                                        .executes(context -> {
                                            Identifier scriptId = IdentifierArgument.getId(context, "scriptId");
                                            ScriptError error = NekoErrorTracker.getError(scriptId);

                                            if (error != null) {
                                                ServerPlayer player = context.getSource().getPlayerOrException();
                                                PacketDistributor.sendToPlayer(player, new ShowErrorScreenPacket(scriptId.toString(), error.getFullDetailText()));
                                            } else {
                                                context.getSource().sendFailure(Component.literal("§c找不到该脚本的错误记录。可能已被清理。"));
                                            }
                                            return 1;
                                        })
                                )
                        )
        );
    }
}