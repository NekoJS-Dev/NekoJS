package com.tkisor.nekojs.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.tkisor.nekojs.NekoJS;
import com.tkisor.nekojs.NekoJSMod;
import com.tkisor.nekojs.core.ScriptLocator;
import com.tkisor.nekojs.script.ScriptManager;
import com.tkisor.nekojs.core.error.NekoErrorUIHelper;
import com.tkisor.nekojs.core.lifecycle.NekoRuntimeRoot;
import com.tkisor.nekojs.network.OpenWorkspacePacket;
import com.tkisor.nekojs.network.ShowErrorListPacket;
import com.tkisor.nekojs.network.dto.ErrorSummaryDTO;
import com.tkisor.nekojs.platform.Platform;
import com.tkisor.nekojs.script.ScriptType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.List;
import java.util.concurrent.CompletableFuture;

public final class NekoJSCommands {

    private NekoJSCommands() {}

    public static void register(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();

        dispatcher.register(
                Commands.literal("nekojs")
                        .requires(source -> source.hasPermission(2))

                        .then(reloadCommand())

                        .then(Commands.literal("test")
                                .executes(context -> {
                                    CommandSourceStack source = context.getSource();
                                    source.sendSystemMessage(Component.literal("Running NekoJS test scripts..."));

                                    try {
                                        NekoRuntimeRoot root = NekoJSMod.RUNTIME_ROOT;
                                        ScriptManager testSm = root.scriptManagerOrNull(ScriptType.TEST);
                                        if (testSm == null) {
                                            testSm = root.createScriptManager(ScriptType.TEST);
                                        }
                                        testSm.runTestScripts();
                                        sendReloadResult(source, "NekoJS test scripts completed.");
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
                                    if (NekoJSMod.RUNTIME_ROOT.errors().count() > 0) {
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
                                    if (NekoJSMod.RUNTIME_ROOT.errors().count() > 0) {
                                        ServerPlayer player = source.getPlayerOrException();

                                        PacketDistributor.sendToPlayer(player, new ShowErrorListPacket(errorSnapshot()));
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

    private static LiteralArgumentBuilder<CommandSourceStack> reloadCommand() {
        LiteralArgumentBuilder<CommandSourceStack> reload = Commands.literal("reload")
                .executes(context -> reloadType(context.getSource(), ScriptType.SERVER));
        for (ScriptType type : ScriptType.all()) {
            addReloadType(reload, type);
        }
        return reload;
    }

    private static void addReloadType(LiteralArgumentBuilder<CommandSourceStack> reload, ScriptType type) {
        reload.then(Commands.literal(type.name)
                .executes(context -> reloadType(context.getSource(), type))
                .then(Commands.argument("file", StringArgumentType.greedyString())
                        .suggests((context, builder) -> suggestReloadFiles(type, builder))
                        .executes(context -> reloadFile(context.getSource(), type, StringArgumentType.getString(context, "file")))));
    }

    private static CompletableFuture<Suggestions> suggestReloadFiles(ScriptType type, SuggestionsBuilder builder) {
        String prefix = "nekojs reload " + type.name + " ";
        String input = builder.getInput();
        int commandStart = input.startsWith("/") ? 1 : 0;
        int fileStart = input.startsWith(prefix, commandStart) ? commandStart + prefix.length() : builder.getStart();
        String fileInput = input.substring(Math.min(fileStart, input.length())).replace('\\', '/');
        SuggestionsBuilder pathBuilder = builder.createOffset(fileStart);
        for (String suggestion : ScriptLocator.suggestScriptFiles(type, fileInput)) {
            pathBuilder.suggest(suggestion);
        }
        return pathBuilder.buildFuture();
    }

    private static int reloadType(CommandSourceStack source, ScriptType type) {
        if (!canReloadHere(source, type)) {
            return 0;
        }
//        source.sendSystemMessage(Component.literal("Reloading NekoJS " + type.name + " scripts..."));
        try {
            NekoRuntimeRoot root = NekoJSMod.RUNTIME_ROOT;
            if (type == ScriptType.TEST) {
                ScriptManager testSm = root.scriptManagerOrNull(ScriptType.TEST);
                if (testSm == null) {
                    testSm = root.createScriptManager(ScriptType.TEST);
                }
                testSm.runTestScripts();
            } else {
                root.reload(type);
            }
            sendReloadResult(source, "NekoJS " + type.name + " scripts reloaded.");
        } catch (Exception e) {
            NekoJS.LOGGER.error("Reloading {} scripts failed fatally", type.name, e);
            source.sendFailure(Component.literal("Reloading NekoJS " + type.name + " scripts failed fatally."));
        }
        return 1;
    }

    private static int reloadFile(CommandSourceStack source, ScriptType type, String filePath) {
        if (!canReloadHere(source, type)) {
            return 0;
        }
        source.sendSystemMessage(Component.literal("Reloading NekoJS " + type.name + " script " + filePath + "..."));
        try {
            NekoRuntimeRoot root = NekoJSMod.RUNTIME_ROOT;
            int affectedEntries = root.scriptManagerOf(type).reloadScriptFile(filePath).size();
            if (type == ScriptType.TEST) {
                ScriptManager testSm = root.scriptManagerOrNull(ScriptType.TEST);
                if (testSm != null) {
                    testSm.flushReadyNodeTimers();
                }
            }
            sendReloadResult(source, "NekoJS " + type.name + " script " + filePath + " reloaded (" + affectedEntries + " affected entr" + (affectedEntries == 1 ? "y" : "ies") + ").");
        } catch (Exception e) {
            NekoJS.LOGGER.error("Reloading {} script file {} failed fatally", type.name, filePath, e);
            source.sendFailure(Component.literal("Reloading NekoJS " + type.name + " script " + filePath + " failed: " + e.getMessage()));
        }
        return 1;
    }

    private static boolean canReloadHere(CommandSourceStack source, ScriptType type) {
        if (type == ScriptType.CLIENT && !Platform.isClient()) {
            source.sendFailure(Component.literal("Client script reload is only available in an integrated client runtime."));
            return false;
        }
        return true;
    }

    private static List<ErrorSummaryDTO> errorSnapshot() {
        return NekoJSMod.RUNTIME_ROOT.errors().errors().stream()
                .map(err -> new ErrorSummaryDTO(
                        err.getErrorId().toString(),
                        err.getDisplayPath(),
                        err.getLineNumber(),
                        err.getOccurrenceCount(),
                        err.getErrorMessage(),
                        err.getFullDetailText()
                )).toList();
    }

    private static void refreshOpenErrorDashboard(CommandSourceStack source) {
        if (source.getEntity() instanceof ServerPlayer player) {
            PacketDistributor.sendToPlayer(player, new ShowErrorListPacket(errorSnapshot(), false));
        }
    }

    private static void sendReloadResult(CommandSourceStack source, String successMessage) {
        refreshOpenErrorDashboard(source);
        int count = NekoJSMod.RUNTIME_ROOT.errors().count();
        if (count > 0) {
            source.sendSuccess(() -> Component.literal(successMessage + " (" + count + " error(s) remain)"), false);
            source.sendFailure(NekoErrorUIHelper.getErrorComponent());
        } else {
            source.sendSuccess(() -> Component.literal(successMessage + " - no errors."), false);
        }
    }
}
