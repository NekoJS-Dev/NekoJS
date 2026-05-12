package com.tkisor.nekojs.command;

import com.mojang.brigadier.CommandDispatcher;
import com.tkisor.nekojs.NekoJS;
import com.tkisor.nekojs.core.error.NekoErrorTracker;
import com.tkisor.nekojs.core.error.NekoErrorUIHelper;
import com.tkisor.nekojs.network.OpenWorkspacePacket;
import com.tkisor.nekojs.network.ShowErrorListPacket;
import com.tkisor.nekojs.network.dto.ErrorSummaryDTO;
import com.tkisor.nekojs.script.ScriptType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.List;

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
                            source.sendSystemMessage(Component.translatable("nekojs.command.reloading"));

                            try {
                                NekoJS.SCRIPT_MANAGER.reloadScripts(ScriptType.SERVER);

                                if (NekoErrorTracker.hasErrors()) {
                                    source.sendFailure(NekoErrorUIHelper.getErrorComponent());
                                } else {
                                    source.sendSuccess(NekoErrorUIHelper::getSuccessComponent, true);
                                }
                            } catch (Exception e) {
                                NekoJS.LOGGER.error("Reloading scripts failed fatally", e);
                                source.sendFailure(Component.translatable("nekojs.command.reload.fatal"));
                            }
                            return 1;
                        })
                )

                        .then(Commands.literal("test")
                                .executes(context -> {
                                    CommandSourceStack source = context.getSource();
                                    source.sendSystemMessage(Component.literal("Running NekoJS test scripts..."));

                                    try {
                                        NekoJS.SCRIPT_MANAGER.runTestScripts();

                                        if (NekoErrorTracker.hasErrors()) {
                                            source.sendFailure(NekoErrorUIHelper.getErrorComponent());
                                        } else {
                                            source.sendSuccess(() -> Component.literal("NekoJS test scripts completed."), true);
                                        }
                                    } catch (Exception e) {
                                        NekoJS.LOGGER.error("Running test scripts failed fatally", e);
                                        source.sendFailure(Component.literal("Running NekoJS test scripts failed fatally."));
                                    }
                                    return 1;
                                })
                        )

                        .then(Commands.literal("error")
                                .executes(context -> {
                                    CommandSourceStack source = context.getSource();
                                    if (NekoErrorTracker.hasErrors()) {
                                        source.sendFailure(NekoErrorUIHelper.getErrorComponent());
                                    } else {
                                        source.sendSuccess(() -> Component.translatable("nekojs.command.error.healthy"), false);
                                    }
                                    return 1;
                                })
                        )

                        .then(Commands.literal("view_all_errors")
                                .executes(context -> {
                                    CommandSourceStack source = context.getSource();
                                    if (NekoErrorTracker.hasErrors()) {
                                        ServerPlayer player = source.getPlayerOrException();

                                        List<ErrorSummaryDTO> dtoList = NekoErrorTracker.getAllErrors().stream()
                                                .map(err -> new ErrorSummaryDTO(
                                                        err.getErrorId().toString(),
                                                        err.getDisplayPath(),
                                                        err.getLineNumber(),
                                                        err.getOccurrenceCount(),
                                                        err.getErrorMessage(),
                                                        err.getFullDetailText()
                                                )).toList();

                                        PacketDistributor.sendToPlayer(player, new ShowErrorListPacket(dtoList));
                                    } else {
                                        source.sendSuccess(() -> Component.translatable("nekojs.command.error.none"), false);
                                    }
                                    return 1;
                                })
                        )

                        .then(Commands.literal("editor")
                                .executes(context -> {
                                    CommandSourceStack source = context.getSource();
                                    ServerPlayer player = source.getPlayerOrException();
                                    PacketDistributor.sendToPlayer(player, new OpenWorkspacePacket());
                                    return 1;
                                })
                        )
        );
    }
}