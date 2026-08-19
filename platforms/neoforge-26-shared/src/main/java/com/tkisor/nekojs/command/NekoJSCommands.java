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
import com.tkisor.nekojs.network.ErrorSummaryDTO;
import com.tkisor.nekojs.platform.Platform;
import com.tkisor.nekojs.api.ScriptType;
import com.tkisor.nekojs.api.catalog.NekoScriptCatalog;
import com.tkisor.nekojs.api.plugin.NekoRuntimeAccess;
import com.tkisor.nekojs.api.recipe.IRecipeManagerExtension;
import com.tkisor.nekojs.probe.ProbeBackend;
import com.tkisor.nekojs.probe.ProbeBackendRegistry;
import com.tkisor.nekojs.probe.ProbeCoordinator;
import com.tkisor.nekojs.probe.ProbeGenerator;
import com.tkisor.nekojs.wrapper.event.server.ItemModificationEventJS;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.crafting.RecipeManager;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

public final class NekoJSCommands {

    private NekoJSCommands() {}

    public static void register(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();

        dispatcher.register(
                Commands.literal("nekojs")
                        .requires(source -> Commands.LEVEL_GAMEMASTERS.check(source.permissions()))

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

                        .then(packsCommand())

                        .then(probeCommand())
        );
    }

    /**
     * /nekojs packs：列出全部脚本包（GLOBAL/WORLD、启用态、目录）。
     * /nekojs packs enable|disable <id>：写包状态文件（优先级高于 manifest），提示 reload 生效；
     * 状态文件对 WORLD 包同样有效，但其脚本在下次进入该世界时才重新评估。
     */
    private static LiteralArgumentBuilder<CommandSourceStack> packsCommand() {
        return Commands.literal("packs")
                .executes(context -> {
                    listPacks(context.getSource());
                    return 1;
                })
                .then(Commands.literal("enable")
                        .then(Commands.argument("id", StringArgumentType.word())
                                .executes(context -> togglePack(context.getSource(), StringArgumentType.getString(context, "id"), true))))
                .then(Commands.literal("disable")
                        .then(Commands.argument("id", StringArgumentType.word())
                                .executes(context -> togglePack(context.getSource(), StringArgumentType.getString(context, "id"), false))));
    }

    private static void listPacks(CommandSourceStack source) {
        var registry = com.tkisor.nekojs.core.pack.ScriptPackRegistry.get();
        registry.refreshGlobalPacks();
        var all = new java.util.ArrayList<com.tkisor.nekojs.core.pack.ScriptPack>();
        all.addAll(registry.globalPacks());
        all.addAll(registry.worldPacks());
        if (all.isEmpty()) {
            source.sendSystemMessage(Component.literal("No script packs found (looked in nekojs/packs/ and <world>/nekojs_packs/)."));
            return;
        }
        source.sendSystemMessage(Component.literal("Script packs (" + all.size() + "):"));
        for (var pack : all) {
            source.sendSystemMessage(Component.literal("  [" + (pack.enabled() ? "x" : " ") + "] "
                    + pack.scope() + ":" + pack.id() + " v" + pack.version()
                    + (pack.name().equals(pack.id()) ? "" : " (" + pack.name() + ")")
                    + " - " + pack.root()));
        }
    }

    private static int togglePack(CommandSourceStack source, String id, boolean enabled) {
        var registry = com.tkisor.nekojs.core.pack.ScriptPackRegistry.get();
        registry.refreshGlobalPacks();
        var all = new java.util.ArrayList<com.tkisor.nekojs.core.pack.ScriptPack>();
        all.addAll(registry.globalPacks());
        all.addAll(registry.worldPacks());
        var match = all.stream().filter(p -> p.id().equals(id)).findFirst();
        if (match.isEmpty()) {
            source.sendFailure(Component.literal("No script pack with id '" + id + "'. Use /nekojs packs to list."));
            return 0;
        }
        com.tkisor.nekojs.core.pack.ScriptPackState.save(match.get().root(), enabled);
        source.sendSystemMessage(Component.literal("Script pack " + match.get().scope() + ":" + id
                + " " + (enabled ? "enabled" : "disabled")
                + ". Run /nekojs reload (server|client) to apply."));
        return 1;
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
            // SERVER 脚本 reload 后重新应用配方脚本（NeoForge 配方热重载）
            boolean recipeBroadcast = false;
            if (type == ScriptType.SERVER) {
                recipeBroadcast = applyRecipeScripts(source);
                // 物品属性修改重放：与服务器启动同一路径（先恢复快照再重跑 modification 事件）
                ItemModificationEventJS.fire(source.getServer());
            }
            // nekojs$applyScripts 内部已向全体 gamemaster 广播 ✔/⚠ 结果，命令权限与广播过滤同为
            // LEVEL_GAMEMASTERS，执行者本人必在广播名单内；玩家执行时不再补发命令行，避免一次 reload 出现两条消息。
            // 控制台收不到玩家广播，仍走 sendReloadResult。
            if (recipeBroadcast && source.getEntity() instanceof ServerPlayer) {
                refreshOpenErrorDashboard(source);
                return 1;
            }
            sendReloadResult(source, "NekoJS " + type.name + " scripts reloaded.");
        } catch (Exception e) {
            NekoJS.LOGGER.error("Reloading {} scripts failed fatally", type.name, e);
            source.sendFailure(Component.literal("Reloading NekoJS " + type.name + " scripts failed fatally."));
        }
        return 1;
    }

    /**
     * SERVER 脚本 reload 后重新应用配方脚本（NeoForge 配方热重载）。
     * RecipeManagerMixin.nekojs$applyScripts() 从永久缓存的 baseJsons 重建工作集并重跑配方脚本，
     * 把脚本生成/修改/删除的配方 JSON 重新解析并替换 RecipeManager 的 recipes。
     */
    private static boolean applyRecipeScripts(CommandSourceStack source) {
        MinecraftServer server = source.getServer();
        if (server == null) return false;
        RecipeManager recipeManager = server.getRecipeManager();
        if (recipeManager instanceof IRecipeManagerExtension ext) {
            ext.nekojs$applyScripts();
            return true;
        }
        return false;
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
            // 错误数并进同一条消息且可点击打开错误列表，不再追加独立的警告组件：
            // 此分支只覆盖无配方广播的路径（test/单文件 reload/CLIENT/STARTUP/控制台），
            // 玩家执行的 SERVER reload 由 RecipeManagerMixin 的广播单独反馈（见 reloadType）。
            MutableComponent message = Component.literal(successMessage + " (" + count + " error(s) remain)")
                    .withStyle(style -> style
                            .withHoverEvent(new HoverEvent.ShowText(Component.translatable("nekojs.error.tracker.hover_hint")))
                            .withClickEvent(new ClickEvent.RunCommand("/nekojs view_all_errors")));
            source.sendSuccess(() -> message, false);
        } else {
            source.sendSuccess(() -> Component.literal(successMessage + " - no errors."), false);
        }
    }

    // ------------------------------------------------------------------
    //  Probe（多 backend）
    // ------------------------------------------------------------------

    private static LiteralArgumentBuilder<CommandSourceStack> probeCommand() {
        LiteralArgumentBuilder<CommandSourceStack> probe = Commands.literal("probe")
                .executes(context -> runProbe(context.getSource(), selectDefaultTypescript()));
        probe.then(Commands.literal("all")
                .executes(context -> runProbe(context.getSource(), selectAll())));
        probe.then(Commands.literal("list")
                .executes(context -> listProbeBackends(context.getSource())));
        probe.then(Commands.literal("reload")
                .executes(context -> reloadProbeConfig(context.getSource())));
        // reset_config：删除各 backend 管理的编辑器配置（jsconfig/pyrightconfig/snippets，保留共享
        // 的 .vscode/settings.json 与用户内容），重建出厂基础配置后立即重跑默认 probe 生成。
        probe.then(Commands.literal("reset_config")
                .executes(context -> {
                    CommandSourceStack source = context.getSource();
                    int backends = ProbeCoordinator.resetEditorConfigs();
                    source.sendSystemMessage(Component.literal(
                            "Editor configs reset (" + backends + " backend(s)); regenerating probe..."));
                    return runProbe(source, selectDefaultTypescript());
                }));
        probe.then(Commands.literal("enable")
                .executes(context -> enableProbe(context.getSource())));
        probe.then(Commands.literal("disable")
                .executes(context -> disableProbe(context.getSource())));
        probe.then(Commands.argument("language", StringArgumentType.word())
                .suggests((context, builder) -> suggestProbeLanguages(builder))
                .executes(context -> runProbe(context.getSource(),
                        selectLanguage(StringArgumentType.getString(context, "language"))))
                .then(Commands.argument("name", StringArgumentType.word())
                        .suggests((context, builder) -> suggestProbeBackendNames(
                                StringArgumentType.getString(context, "language"), builder))
                        .executes(context -> runProbe(context.getSource(),
                                selectNamed(
                                        StringArgumentType.getString(context, "language"),
                                        StringArgumentType.getString(context, "name"))))));
        return probe;
    }

    /** /nekojs probe 无参：默认只跑 TS builtin。 */
    private static List<ProbeBackend> selectDefaultTypescript() {
        return ProbeBackendRegistry.get().backend("typescript", "builtin")
                .map(List::of).orElse(List.of());
    }

    /** /nekojs probe all：所有已注册 backend（跨语言）。 */
    private static List<ProbeBackend> selectAll() {
        ProbeBackendRegistry registry = ProbeBackendRegistry.get();
        List<ProbeBackend> all = new ArrayList<>();
        for (String lang : registry.languages()) {
            all.addAll(registry.backendsFor(lang));
        }
        return all;
    }

    private static List<ProbeBackend> selectLanguage(String languageId) {
        ProbeBackendRegistry registry = ProbeBackendRegistry.get();
        // per-language 配置（probe.toml [languages.<lang>].backend）优先：指定了 backend 名时按 (语言, 名字) 精确选取，
        // 找不到再回退该语言的注册表默认（priority 最高者）；无配置则维持现状（defaultBackend）。
        var langCfg = ProbeCoordinator.config().language(languageId);
        if (langCfg.isPresent()) {
            String configuredName = langCfg.get().backend();
            if (configuredName != null && !configuredName.isBlank()) {
                var configured = registry.backend(languageId, configuredName);
                if (configured.isPresent()) return List.of(configured.get());
            }
        }
        return registry.defaultBackend(languageId)
                .map(List::of).orElse(List.of());
    }

    private static List<ProbeBackend> selectNamed(String languageId, String name) {
        return ProbeBackendRegistry.get().backend(languageId, name)
                .map(List::of).orElse(List.of());
    }

    private static int runProbe(CommandSourceStack source, List<ProbeBackend> backends) {
        if (backends.isEmpty()) {
            source.sendFailure(Component.literal("No probe backend matched. Use /nekojs probe list."));
            return 0;
        }
        String names = backends.stream()
                .map(b -> b.languageId() + ":" + b.name())
                .collect(Collectors.joining(", "));
        source.sendSystemMessage(Component.literal("Generating probe (" + names + ")..."));

        try {
            var snapshot = NekoScriptCatalog.snapshot(NekoRuntimeAccess.get());
            List<ProbeGenerator.GenerateResult> results = ProbeCoordinator.run(snapshot, backends);

            int totalFiles = 0;
            long maxMs = 0;
            boolean allOk = true;
            for (ProbeGenerator.GenerateResult r : results) {
                if (r.success()) {
                    totalFiles += r.filesGenerated();
                    maxMs = Math.max(maxMs, r.durationMs());
                } else {
                    allOk = false;
                    source.sendFailure(Component.literal("  backend failed: " + r.message()));
                }
            }
            if (allOk) {
                final int tf = totalFiles;
                final long ms = maxMs;
                source.sendSuccess(() -> Component.literal(
                        "Probe generated: " + tf + " files in " + ms + "ms"), false);
            }
        } catch (Exception e) {
            NekoJS.LOGGER.error("Probe generation failed", e);
            source.sendFailure(Component.literal("Probe generation failed: " + e.getMessage()));
        }
        return 1;
    }

    private static int listProbeBackends(CommandSourceStack source) {
        var entries = ProbeBackendRegistry.get().registrars();
        if (entries.isEmpty()) {
            source.sendSystemMessage(Component.literal("No probe backends registered."));
        } else {
            source.sendSystemMessage(Component.literal("Registered probe backends:"));
            for (String e : entries) {
                source.sendSystemMessage(Component.literal("  - " + e));
            }
        }
        return 1;
    }

    private static int reloadProbeConfig(CommandSourceStack source) {
        ProbeCoordinator.reloadConfig();
        source.sendSuccess(() -> Component.literal("Probe config (probe.toml) reloaded."), false);
        return 1;
    }

    /** /nekojs probe enable：运行时启用 probe（开关持久化由 ProbeCoordinator.setEnabled 负责）。 */
    private static int enableProbe(CommandSourceStack source) {
        ProbeCoordinator.setEnabled(true);
        source.sendSuccess(() -> Component.literal("Probe enabled."), false);
        return 1;
    }

    /** /nekojs probe disable：运行时禁用 probe。 */
    private static int disableProbe(CommandSourceStack source) {
        ProbeCoordinator.setEnabled(false);
        source.sendSuccess(() -> Component.literal("Probe disabled."), false);
        return 1;
    }

    private static CompletableFuture<Suggestions> suggestProbeLanguages(SuggestionsBuilder builder) {
        for (String lang : ProbeBackendRegistry.get().languages()) {
            builder.suggest(lang);
        }
        return builder.buildFuture();
    }

    private static CompletableFuture<Suggestions> suggestProbeBackendNames(String languageId, SuggestionsBuilder builder) {
        if (languageId != null && !languageId.isBlank()) {
            for (ProbeBackend b : ProbeBackendRegistry.get().backendsFor(languageId)) {
                builder.suggest(b.name());
            }
        }
        return builder.buildFuture();
    }
}
