//? if neoforge {
//? if >=26 {
package com.tkisor.nekojs.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.mojang.brigadier.tree.CommandNode;
import com.tkisor.nekojs.NekoJS;
import com.tkisor.nekojs.core.ScriptLocator;
import com.tkisor.nekojs.core.error.NekoErrorUIHelper;
import com.tkisor.nekojs.core.lifecycle.NekoRuntimeRoot;
import com.tkisor.nekojs.core.lifecycle.RuntimeCommandResultFormatter;
import com.tkisor.nekojs.network.ShowErrorListPacket;
import com.tkisor.nekojs.network.ErrorSummaryDTO;
import com.tkisor.nekojs.platform.Platform;
import com.tkisor.nekojs.api.ScriptType;
import com.tkisor.nekojs.api.catalog.NekoScriptCatalog;
import com.tkisor.nekojs.api.plugin.NekoRuntimeAccess;
import com.tkisor.nekojs.api.recipe.IRecipeManagerExtension;
import com.tkisor.nekojs.core.fs.NekoJSPaths;
import com.tkisor.nekojs.probe.ProbeBackend;
import com.tkisor.nekojs.probe.ProbeBackendRegistry;
import com.tkisor.nekojs.probe.ProbeBackendSelector;
import com.tkisor.nekojs.probe.ProbeCoordinator;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeManager;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

public final class NekoJSCommands {

    private NekoJSCommands() {}

    public static void register(RegisterCommandsEvent event, NekoRuntimeRoot root) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();

        dispatcher.register(
                Commands.literal("nekojs")
                        .requires(source -> Commands.LEVEL_GAMEMASTERS.check(source.permissions()))
                        .executes(context -> sendRootUsage(context.getSource(), dispatcher))

                        .then(reloadCommand(root))

                        .then(Commands.literal("test")
                                .executes(context -> runTests(context.getSource(), root))
                        )

                        .then(Commands.literal("error")
                                .executes(context -> {
                                    CommandSourceStack source = context.getSource();
                                    if (root == null) {
                                        source.sendFailure(Component.literal("NekoJS runtime is not available."));
                                        return 0;
                                    }
                                    NekoRuntimeRoot.ErrorSnapshot errors = root.errors();
                                    if (errors.count() > 0) {
                                        source.sendFailure(NekoErrorUIHelper.getErrorComponent(errors.count()));
                                    } else {
                                        source.sendSuccess(() -> Component.translatable("nekojs.command.error.healthy"), false);
                                    }
                                    return 1;
                                })
                        )

                        .then(Commands.literal("view_all_errors")
                                .executes(context -> {
                                    CommandSourceStack source = context.getSource();
                                    if (root == null) {
                                        source.sendFailure(Component.literal("NekoJS runtime is not available."));
                                        return 0;
                                    }
                                    NekoRuntimeRoot.ErrorSnapshot errors = root.errors();
                                    if (errors.count() > 0) {
                                        ServerPlayer player = source.getPlayerOrException();
                                        PacketDistributor.sendToPlayer(player, new ShowErrorListPacket(errorSnapshot(errors)));
                                    } else {
                                        source.sendSuccess(() -> Component.translatable("nekojs.command.error.none"), false);
                                    }
                                    return 1;
                                })
                        )

                        .then(packsCommand(root))

                        .then(registryCommand())

                        .then(handCommand())

                        .then(inventoryCommand())

                        .then(Commands.literal("trust")
                                .then(Commands.argument("address", AddressArgument.address())
                                        .executes(context -> trustServer(context.getSource(), AddressArgument.getAddress(context, "address")))))

                        .then(probeCommand())
        );
    }

    /** Bare /nekojs: list the registered subtree instead of brigadier's "Unknown or incomplete command". */
    private static int sendRootUsage(CommandSourceStack source, CommandDispatcher<CommandSourceStack> dispatcher) {
        String subtree = dispatcher.getRoot().getChild("nekojs").getChildren().stream()
                .map(CommandNode::getName)
                .collect(Collectors.joining("|"));
        source.sendSuccess(() -> Component.translatable("nekojs.command.usage", Component.literal(subtree)), false);
        return 1;
    }

    /**
     * /nekojs registry：动态注册健康快照（每注册表条目数 + stale 残留）。
     * /nekojs registry stale：只列出上次 reload 后脚本不再注册的条目 id。
     */
    private static LiteralArgumentBuilder<CommandSourceStack> registryCommand() {
        return Commands.literal("registry")
                .executes(context -> {
                    for (var s : com.tkisor.nekojs.dynamic.DynamicRegistryDebug.snapshot()) {
                        context.getSource().sendSystemMessage(Component.literal(s.summary()));
                    }
                    return 1;
                })
                .then(Commands.literal("stale")
                        .executes(context -> {
                            boolean any = false;
                            for (var s : com.tkisor.nekojs.dynamic.DynamicRegistryDebug.snapshot()) {
                                for (String id : s.prettyStaleIds()) {
                                    context.getSource().sendSystemMessage(Component.literal(id));
                                    any = true;
                                }
                            }
                            if (!any) {
                                context.getSource().sendSystemMessage(Component.literal("No stale dynamic registry entries."));
                            }
                            return 1;
                        }));
    }

    /**
     * /nekojs hand: prints the main-hand item of the executing player (item id, count,
     * damage if damageable, and the data component patch). Requires a player source.
     */
    private static LiteralArgumentBuilder<CommandSourceStack> handCommand() {
        return Commands.literal("hand")
                .executes(context -> {
                    ServerPlayer player = context.getSource().getPlayerOrException();
                    showHand(context.getSource(), player.getMainHandItem());
                    return 1;
                });
    }

    private static void showHand(CommandSourceStack source, ItemStack stack) {
        if (stack.isEmpty()) {
            source.sendSystemMessage(Component.literal("Empty hand"));
            return;
        }
        String id = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
        // Item id is click-to-copy: the main dev-loop use is pasting the id into a script
        MutableComponent line = Component.literal(id)
                .withStyle(style -> style
                        .withClickEvent(new ClickEvent.CopyToClipboard(id))
                        .withHoverEvent(new HoverEvent.ShowText(Component.literal("Click to copy"))))
                .append(Component.literal(" x" + stack.getCount()));
        source.sendSystemMessage(line);
        if (stack.isDamageableItem()) {
            source.sendSystemMessage(Component.literal("  damage: " + stack.getDamageValue() + "/" + stack.getMaxDamage()));
        }
        source.sendSystemMessage(Component.literal("  components: " + compactComponents(stack)));
    }

    /** Single-line component patch summary, flattened and truncated so chat stays readable. */
    private static String compactComponents(ItemStack stack) {
        String text = stack.getComponentsPatch().toString().replace('\n', ' ');
        return text.length() > 300 ? text.substring(0, 300) + "..." : text;
    }

    /**
     * /nekojs inventory: lists all non-empty main inventory slots (0-35) of the executing
     * player, one line per slot. Requires a player source.
     */
    private static LiteralArgumentBuilder<CommandSourceStack> inventoryCommand() {
        return Commands.literal("inventory")
                .executes(context -> {
                    ServerPlayer player = context.getSource().getPlayerOrException();
                    listInventory(context.getSource(), player.getInventory());
                    return 1;
                });
    }

    private static void listInventory(CommandSourceStack source, Inventory inventory) {
        int used = 0;
        for (int slot = 0; slot < Inventory.INVENTORY_SIZE; slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack.isEmpty()) {
                continue;
            }
            source.sendSystemMessage(Component.literal("slot " + slot + ": "
                    + BuiltInRegistries.ITEM.getKey(stack.getItem()).toString() + " x" + stack.getCount()));
            used++;
        }
        if (used == 0) {
            source.sendSystemMessage(Component.literal("Inventory is empty."));
        }
    }

    /**
     * /nekojs trust &lt;address&gt;：把服务器脚本包来源加入本进程的信任存储（多人脚本分发的
     * 首连断连→信任→重连流程）。仅在客户端进程生效（单人环境即本进程）；远程多人请用
     * 客户端命令（见 NekoClientCommands）或先进入单人世界执行。
     */
    private static int trustServer(CommandSourceStack source, String address) {
        if (!com.tkisor.nekojs.platform.Platform.isClient()) {
            source.sendFailure(Component.literal("Run /nekojs trust on a client process (e.g. in a singleplayer world)."));
            return 0;
        }
        var store = com.tkisor.nekojs.core.pack.sync.PackSyncTrustStore.get();
        store.trustServer(address);
        source.sendSuccess(() -> Component.literal(
                "Trusted " + address + " (bucket " + com.tkisor.nekojs.core.pack.sync.PackSyncTrustStore.bucketFor(address)
                        + "). Reconnect to receive its script packs."), false);
        return 1;
    }

    /**
     * Lists discovered pack intent and saves source-qualified enablement requests.
     * Runtime changes require a type reload; STARTUP changes require a restart.
     */
    private static LiteralArgumentBuilder<CommandSourceStack> packsCommand(NekoRuntimeRoot root) {
        var packs = Commands.literal("packs")
                .executes(context -> {
                    listPacks(context.getSource());
                    return 1;
                })
                .then(Commands.literal("enable")
                        .then(Commands.argument("id", StringArgumentType.greedyString())
                                .executes(context -> togglePack(context.getSource(), StringArgumentType.getString(context, "id"), true))))
                .then(Commands.literal("disable")
                        .then(Commands.argument("id", StringArgumentType.greedyString())
                                .executes(context -> togglePack(context.getSource(), StringArgumentType.getString(context, "id"), false))));
        var status = Commands.literal("status");
        var plan = Commands.literal("plan");
        for (ScriptType type : ScriptType.all()) {
            status.then(Commands.literal(type.name)
                    .executes(context -> showPackExecution(context.getSource(), root, type, false)));
            plan.then(Commands.literal(type.name)
                    .executes(context -> showPackExecution(context.getSource(), root, type, true)));
        }
        var reload = Commands.literal("reload");
        var create = Commands.literal("create");
        for (ScriptType type : ScriptType.all()) {
            create.then(Commands.literal(type.name).then(Commands.argument("id", StringArgumentType.greedyString())
                    .executes(context -> reportPackTool(context.getSource(), root, "create",
                            StringArgumentType.getString(context, "id"), type, null, null))));
            if (type == ScriptType.STARTUP) continue;
            reload.then(Commands.literal(type.name).then(Commands.argument("id", StringArgumentType.greedyString())
                    .executes(context -> reloadPackCommand(context.getSource(), root, type, StringArgumentType.getString(context, "id")))));
        }
        packs.then(reload).then(create);
        for (String action : java.util.List.of("validate", "archive")) {
            packs.then(Commands.literal(action).then(Commands.argument("id", StringArgumentType.greedyString())
                    .executes(context -> reportPackTool(context.getSource(), root, action,
                            StringArgumentType.getString(context, "id"), null, null, null))));
        }
        var jar = Commands.literal("jar");
        for (String loader : java.util.List.of("neoforge", "fabric")) {
            var target = Commands.literal(loader);
            for (String minecraft : loader.equals("neoforge") ? java.util.List.of("1.21.1", "26.1.2", "26.2") : java.util.List.of("26.1.2", "26.2")) {
                target.then(Commands.literal(minecraft).then(Commands.argument("id", StringArgumentType.greedyString())
                        .executes(context -> reportPackTool(context.getSource(), root, "jar",
                                StringArgumentType.getString(context, "id"), null, loader, minecraft))));
            }
            jar.then(target);
        }
        packs.then(jar);
        return packs.then(status).then(plan);
    }

    private static int reportPackTool(CommandSourceStack source, NekoRuntimeRoot root, String action,
                                      String selector, ScriptType type, String loader, String minecraft) {
        if (root == null) { source.sendFailure(Component.translatable("nekojs.command.packs.tool_failed", action, selector, "runtime unavailable")); return 0; }
        var result = root.packTool(action, selector, type, loader, minecraft);
        if (!result.success()) {
            source.sendFailure(Component.translatable("nekojs.command.packs.tool_failed", action, selector, result.detail())); return 0;
        }
        source.sendSystemMessage(Component.translatable("nekojs.command.packs.tool_success", action, result.selector(),
                result.output() == null ? result.detail() : result.output().toString()));
        return 1;
    }

    private static int reloadPackCommand(CommandSourceStack source, NekoRuntimeRoot root, ScriptType type, String selector) {
        if (!canReloadHere(source, type) || root == null || !root.hasScriptType(type)) return 0;
        if (type == ScriptType.CLIENT) {
            com.tkisor.nekojs.client.ClientReloadExecutor.execute(() -> {
                var result = root.reloadPack(type, selector);
                source.getServer().execute(() -> reportPackReload(source, result));
            });
            return 1;
        }
        var result = root.reloadPack(type, selector);
        int outcome = reportPackReload(source, result);
        if (result.success() && type == ScriptType.SERVER) {
            try { applyRecipeScripts(source); }
            catch (Exception failure) { source.sendFailure(Component.translatable("nekojs.command.packs.post_reload_failed", failure.getMessage())); return 0; }
        }
        return outcome;
    }

    private static int reportPackReload(CommandSourceStack source, com.tkisor.nekojs.core.pack.PackActivationCoordinator.Result result) {
        if (!result.success()) { source.sendFailure(Component.translatable("nekojs.command.packs.reload_failed", result.failure())); return 0; }
        source.sendSystemMessage(Component.translatable("nekojs.command.packs.reload_success", result.type().name, result.affected().size()));
        result.affected().forEach((selector, reason) -> source.sendSystemMessage(Component.translatable("nekojs.command.packs.affected", selector, reason)));
        result.waiting().forEach((selector, reason) -> source.sendSystemMessage(Component.translatable("nekojs.command.packs.blocked", selector, reason)));
        result.cleanupFailures().forEach(failure -> source.sendFailure(Component.translatable("nekojs.command.packs.cleanup_failed", failure)));
        return 1;
    }

    private static int showPackExecution(CommandSourceStack source, NekoRuntimeRoot root, ScriptType type, boolean plan) {
        if (root == null || !root.hasScriptType(type)) {
            source.sendFailure(Component.translatable("nekojs.command.packs.type_unavailable", type.name));
            return 0;
        }
        if (!plan) {
            for (var activation : root.packActivations(type)) source.sendSystemMessage(Component.translatable(
                    "nekojs.command.packs.activation", activation.selector(), activation.activation(), activation.version(),
                    Component.translatable("nekojs.command.packs.activation_" + activation.status().toLowerCase(java.util.Locale.ROOT)), activation.reason() == null ? "" : activation.reason()));
        }
        java.util.List<com.tkisor.nekojs.core.pack.PackScriptView> entries;
        if (plan) {
            var view = root.packExecutionPlan(type);
            entries = view.entries();
            source.sendSystemMessage(Component.translatable("nekojs.command.packs.plan_header", type.name, entries.size()));
            view.blockedPacks().forEach((selector, reason) -> source.sendSystemMessage(
                    Component.translatable("nekojs.command.packs.blocked", selector, reason)));
            if (!view.orderWarning().isEmpty()) {
                source.sendSystemMessage(Component.translatable("nekojs.command.packs.order_warning", view.orderWarning()));
            }
        } else {
            var view = root.packRuntimeView(type);
            entries = view.entries();
            source.sendSystemMessage(Component.translatable("nekojs.command.packs.status_header", type.name,
                    view.generation(), entries.size(), Component.translatable(view.activeFailed()
                            ? "nekojs.command.packs.isolated" : "nekojs.command.packs.not_isolated")));
        }
        for (var entry : entries) {
            String outcomeKey = switch (entry.status()) {
                case READY -> "nekojs.command.packs.ready";
                case EXECUTED -> "nekojs.command.packs.executed";
                case SKIPPED -> "nekojs.command.packs.skipped";
                case FAILED -> "nekojs.command.packs.failed";
            };
            var reason = switch (entry.reason()) {
                case "disabled" -> Component.translatable("nekojs.command.packs.reason_disabled");
                case "mod_unavailable" -> Component.translatable("nekojs.command.packs.reason_mod");
                default -> Component.literal(entry.reason());
            };
            source.sendSystemMessage(Component.translatable("nekojs.command.packs.execution_entry", entry.scriptId(),
                    entry.selector(), entry.version(), Component.translatable(outcomeKey), reason, entry.source().toString()));
        }
        return 1;
    }

    private static void listPacks(CommandSourceStack source) {
        var registry = com.tkisor.nekojs.core.pack.ScriptPackRegistry.get();
        registry.refreshLocalPacks();
        var all = registry.allPacks();
        if (all.isEmpty()) {
            source.sendSystemMessage(Component.translatable("nekojs.command.packs.empty"));
            return;
        }
        source.sendSystemMessage(Component.translatable("nekojs.command.packs.header", all.size()));
        for (var pack : all) {
            var intent = Component.translatable(pack.scope() == com.tkisor.nekojs.core.pack.ScriptPackScope.SERVER_CACHE
                    ? "nekojs.command.packs.remote"
                    : pack.enabled() ? "nekojs.command.packs.enabled" : "nekojs.command.packs.disabled");
            source.sendSystemMessage(Component.translatable("nekojs.command.packs.entry",
                    pack.selector(), pack.version(), pack.name(), intent, pack.root().toString()));
        }
    }

    private static int togglePack(CommandSourceStack source, String id, boolean enabled) {
        var registry = com.tkisor.nekojs.core.pack.ScriptPackRegistry.get();
        var result = registry.setEnabled(id, enabled);
        if (!result.succeeded()) {
            String key = switch (result.status()) {
                case NOT_FOUND -> "nekojs.command.packs.not_found";
                case AMBIGUOUS -> "nekojs.command.packs.ambiguous";
                case READ_ONLY -> "nekojs.command.packs.read_only";
                case SAVE_FAILED -> "nekojs.command.packs.save_failed";
                case SAVED -> "nekojs.command.packs.saved";
            };
            source.sendFailure(Component.translatable(key, result.selector(), String.join(", ", result.matches())));
            return 0;
        }
        source.sendSystemMessage(Component.translatable("nekojs.command.packs.saved", result.selector(),
                Component.translatable(enabled ? "nekojs.command.packs.enabled" : "nekojs.command.packs.disabled")));
        return 1;
    }

    private static LiteralArgumentBuilder<CommandSourceStack> reloadCommand(NekoRuntimeRoot root) {
        LiteralArgumentBuilder<CommandSourceStack> reload = Commands.literal("reload")
                .executes(context -> reloadType(context.getSource(), ScriptType.SERVER, root));
        for (ScriptType type : ScriptType.all()) {
            addReloadType(reload, type, root);
        }
        return reload;
    }

    private static void addReloadType(LiteralArgumentBuilder<CommandSourceStack> reload, ScriptType type, NekoRuntimeRoot root) {
        reload.then(Commands.literal(type.name)
                .executes(context -> reloadType(context.getSource(), type, root))
                .then(Commands.argument("file", StringArgumentType.greedyString())
                        .suggests((context, builder) -> suggestReloadFiles(type, builder))
                        .executes(context -> reloadFile(context.getSource(), type, StringArgumentType.getString(context, "file"), root))));
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

    private static int reloadType(CommandSourceStack source, ScriptType type, NekoRuntimeRoot root) {
        if (!canReloadHere(source, type)) {
            return 0;
        }
        if (root == null) {
            source.sendFailure(Component.literal("NekoJS runtime is not available."));
            return 0;
        }
        if (type == ScriptType.TEST) {
            return runTests(source, root);
        }
        NekoRuntimeRoot.ReloadResult reloadResult;
        try {
            if (type == ScriptType.CLIENT && com.tkisor.nekojs.client.ClientReloadExecutor.isClientDist()) {
                return scheduleClientReload(source, type, root, null);
            }
            if (type == ScriptType.SERVER) {
                // 清空上一轮 stage 的村民交易，防止 reload 重复累积（与 ServerEventListener
                // 资源 reload 路径同约定：每次完整脚本 reload 前 beginReload）
                com.tkisor.nekojs.villager.VillagerTradeManager.beginReload();
            }
            reloadResult = root.reload(type);
            // SERVER 脚本 reload 后重新应用配方脚本（NeoForge 配方热重载）
            boolean recipeBroadcast = false;
            if (type == ScriptType.SERVER) {
                try {
                    recipeBroadcast = applyRecipeScripts(source);
                    // SERVER domain plans are committed with the generation before platform follow-up.
                    MinecraftServer server = source.getServer();
                    if (com.tkisor.nekojs.villager.VillagerTradeManager.pendingCount() > 0) {
                        com.tkisor.nekojs.villager.VillagerTradeManager.apply(server);
                    }
                    var packs = com.tkisor.nekojs.core.pack.ScriptPackRegistry.get().enabledPacks();
                    if (com.tkisor.nekojs.resource.ScriptPackDataManager.hasDataPacks(packs)
                            && com.tkisor.nekojs.resource.ScriptPackDataManager.activateForServer(server, packs)) {
                        com.tkisor.nekojs.resource.ScriptPackDataManager.reloadServerResources(server);
                    }
                } catch (Exception postReloadFailure) {
                    return reportPostReloadFailure(source, root, reloadResult,
                            "recipe/pack processing", postReloadFailure);
                }
            }
            // Recipe reload has its own broadcast; report the committed generation as well.
            if (recipeBroadcast && source.getEntity() instanceof ServerPlayer) {
                refreshOpenErrorDashboard(source, root.errors());
                source.sendSystemMessage(Component.literal(
                        RuntimeCommandResultFormatter.reloadResult(reloadResult, root.isActiveFailed(type))));
                return 1;
            }
            return reportReloadResult(source, root, reloadResult);
        } catch (com.tkisor.nekojs.core.lifecycle.NekoReloadException e) {
            // 候选 generation 失败（工单 06）：active 保留，失败结果携带
            // generation/phase/source location/owner/domain 结构化字段（无修复指引）
            NekoJS.LOGGER.error("Reloading {} scripts failed", type.name, e);
            source.sendFailure(Component.literal(RuntimeCommandResultFormatter.reloadFailure(
                    e.report(), root.isActiveFailed(type))));
            return 0;
        } catch (Exception e) {
            NekoJS.LOGGER.error("Reloading {} scripts failed fatally", type.name, e);
            source.sendFailure(Component.literal("Reloading NekoJS " + type.name + " scripts failed fatally."));
            return 0;
        }
    }

    private static int runTests(CommandSourceStack source, NekoRuntimeRoot root) {
        if (!canReloadHere(source, ScriptType.TEST)) {
            return 0;
        }
        if (root == null) {
            source.sendFailure(Component.literal("NekoJS runtime is not available."));
            return 0;
        }
        try {
            NekoRuntimeRoot.TestRunResult result = root.runTests();
            if (!result.isConfigured() || !result.isCompleted()) {
                source.sendFailure(Component.literal(RuntimeCommandResultFormatter.testResult(result)));
                return 0;
            }
            sendReloadResult(source, root, RuntimeCommandResultFormatter.testResult(result));
            return 1;
        } catch (com.tkisor.nekojs.core.lifecycle.NekoReloadException e) {
            NekoJS.LOGGER.error("Running test scripts failed", e);
            source.sendFailure(Component.literal(RuntimeCommandResultFormatter.reloadFailure(
                    e.report(), root.isActiveFailed(ScriptType.TEST))));
            return 0;
        } catch (Exception e) {
            NekoJS.LOGGER.error("Running test scripts failed fatally", e);
            source.sendFailure(Component.literal("Running NekoJS test scripts failed fatally."));
            return 0;
        }
    }

    private static int scheduleClientReload(CommandSourceStack source, ScriptType type,
                                            NekoRuntimeRoot root, String filePath) {
        source.sendSystemMessage(Component.literal(filePath == null
                ? "NekoJS CLIENT reload scheduled on the client thread."
                : "NekoJS CLIENT script " + filePath + " reload scheduled on the client thread."));
        com.tkisor.nekojs.client.ClientReloadExecutor.execute(() -> {
            try {
                NekoRuntimeRoot.ReloadResult result = filePath == null
                        ? root.reload(type)
                        : root.reloadFile(type, java.nio.file.Path.of(filePath));
                source.getServer().execute(() -> reportReloadResult(source, root, result));
            } catch (com.tkisor.nekojs.core.lifecycle.NekoReloadException e) {
                NekoJS.LOGGER.error("Reloading {} scripts failed", type.name, e);
                source.getServer().execute(() -> source.sendFailure(Component.literal(
                        RuntimeCommandResultFormatter.reloadFailure(e.report(), root.isActiveFailed(type)))));
            } catch (Exception e) {
                NekoJS.LOGGER.error("Reloading {} scripts failed fatally", type.name, e);
                source.getServer().execute(() -> source.sendFailure(Component.literal(
                        "Reloading NekoJS " + type.name + " scripts failed fatally.")));
            }
        });
        return 1;
    }

    private static int reportPostReloadFailure(CommandSourceStack source, NekoRuntimeRoot root,
                                               NekoRuntimeRoot.ReloadResult result,
                                               String stage, Exception failure) {
        reportReloadResult(source, root, result);
        source.sendFailure(Component.literal(
                RuntimeCommandResultFormatter.postReloadFailure(result, stage, failure)));
        return 0;
    }

    private static int reportReloadResult(CommandSourceStack source, NekoRuntimeRoot root,
                                          NekoRuntimeRoot.ReloadResult result) {
        String message = RuntimeCommandResultFormatter.reloadResult(
                result, root.isActiveFailed(result.type()));
        if (!result.success()) {
            source.sendFailure(Component.literal(message));
            return 0;
        }
        sendReloadResult(source, root, message);
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

    private static int reloadFile(CommandSourceStack source, ScriptType type, String filePath, NekoRuntimeRoot root) {
        if (!canReloadHere(source, type)) {
            return 0;
        }
        if (root == null) {
            source.sendFailure(Component.literal("NekoJS runtime is not available."));
            return 0;
        }
        if (type == ScriptType.CLIENT && com.tkisor.nekojs.client.ClientReloadExecutor.isClientDist()) {
            return scheduleClientReload(source, type, root, filePath);
        }
        source.sendSystemMessage(Component.literal("Reloading NekoJS " + type.name + " script " + filePath + "..."));
        try {
            return reportReloadResult(source, root, root.reloadFile(type, java.nio.file.Path.of(filePath)));
        } catch (Exception e) {
            NekoJS.LOGGER.error("Reloading {} script file {} failed fatally", type.name, filePath, e);
            source.sendFailure(Component.literal("Reloading NekoJS " + type.name + " script file failed fatally."));
            return 0;
        }
    }

    private static boolean canReloadHere(CommandSourceStack source, ScriptType type) {
        if (source.getServer() == null) {
            source.sendFailure(Component.literal("NekoJS lifecycle commands require a server command source."));
            return false;
        }
        if (type == ScriptType.STARTUP) {
            source.sendFailure(Component.literal(
                    "STARTUP scripts cannot be reloaded from a runtime command; restart the game/loader."));
            return false;
        }
        if (type == ScriptType.CLIENT && !Platform.isClient()) {
            source.sendFailure(Component.literal("Client script reload is only available in an integrated client runtime."));
            return false;
        }
        return true;
    }

    private static List<ErrorSummaryDTO> errorSnapshot(NekoRuntimeRoot.ErrorSnapshot snapshot) {
        // Ticket 30: keep the legacy packet shape and project each frozen diagnostic record once.
        return snapshot.errors().stream()
                .map(err -> err.diagnostic().toErrorSummary(
                        err.getOccurrenceCount(),
                        err.getDisplayPath(),
                        err.getErrorMessage(),
                        err.getFullDetailText()
                )).toList();
    }

    private static void refreshOpenErrorDashboard(CommandSourceStack source, NekoRuntimeRoot.ErrorSnapshot snapshot) {
        if (source.getEntity() instanceof ServerPlayer player) {
            PacketDistributor.sendToPlayer(player, new ShowErrorListPacket(errorSnapshot(snapshot), false));
        }
    }

    private static void sendReloadResult(CommandSourceStack source, NekoRuntimeRoot root, String successMessage) {
        NekoRuntimeRoot.ErrorSnapshot errors = root.errors();
        refreshOpenErrorDashboard(source, errors);
        int count = errors.count();
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
                .executes(context -> runProbe(context.getSource(), ProbeBackendSelector.defaultTypescript()));
        probe.then(Commands.literal("all")
                .executes(context -> runProbe(context.getSource(), ProbeBackendSelector.all())));
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
                    return runProbe(source, ProbeBackendSelector.defaultTypescript());
                }));
        probe.then(Commands.literal("enable")
                .executes(context -> enableProbe(context.getSource())));
        probe.then(Commands.literal("disable")
                .executes(context -> disableProbe(context.getSource())));
        probe.then(Commands.argument("language", StringArgumentType.word())
                .suggests((context, builder) -> suggestProbeLanguages(builder))
                .executes(context -> runProbe(context.getSource(),
                        ProbeBackendSelector.forLanguage(StringArgumentType.getString(context, "language"))))
                .then(Commands.argument("name", StringArgumentType.word())
                        .suggests((context, builder) -> suggestProbeBackendNames(
                                StringArgumentType.getString(context, "language"), builder))
                        .executes(context -> runProbe(context.getSource(),
                                ProbeBackendSelector.named(
                                        StringArgumentType.getString(context, "language"),
                                        StringArgumentType.getString(context, "name"))))));
        return probe;
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
        boolean allOk = true;

        try {
            var snapshot = NekoScriptCatalog.snapshot(NekoRuntimeAccess.get());
            List<ProbeBackend.GenerateResult> results = ProbeCoordinator.run(snapshot, backends);

            int totalFiles = 0;
            long maxMs = 0;
            for (ProbeBackend.GenerateResult r : results) {
                if (r.success()) {
                    totalFiles += r.filesGenerated();
                    maxMs = Math.max(maxMs, r.durationMs());
                    for (String w : r.warnings()) {
                        source.sendSystemMessage(Component.literal("  warning: " + w));
                    }
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
                // 输出目录提示：用户最常问「文件去哪了」；相对游戏目录显示
                String dirs = results.stream()
                        .map(ProbeBackend.GenerateResult::outputDir)
                        .filter(java.util.Objects::nonNull)
                        .map(d -> {
                            try {
                                return NekoJSPaths.get().gameDir().relativize(d).toString();
                            } catch (IllegalArgumentException e) {
                                return d.toString();
                            }
                        })
                        .distinct()
                        .collect(Collectors.joining(", "));
                if (!dirs.isEmpty()) {
                    source.sendSystemMessage(Component.literal("  Output: " + dirs));
                }
            }
        } catch (Exception e) {
            NekoJS.LOGGER.error("Probe generation failed", e);
            source.sendFailure(Component.literal("Probe generation failed: " + e.getMessage()));
            return 0;
        }
        return allOk ? 1 : 0;
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
        for (String lang : ProbeBackendSelector.languageSuggestions()) {
            builder.suggest(lang);
        }
        return builder.buildFuture();
    }

    private static CompletableFuture<Suggestions> suggestProbeBackendNames(String languageId, SuggestionsBuilder builder) {
        if (languageId != null && !languageId.isBlank()) {
            for (ProbeBackend b : ProbeBackendSelector.nameSuggestions(languageId)) {
                builder.suggest(b.name());
            }
        }
        return builder.buildFuture();
    }
}
//?}
//?}
