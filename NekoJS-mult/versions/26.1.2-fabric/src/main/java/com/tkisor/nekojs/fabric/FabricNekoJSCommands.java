package com.tkisor.nekojs.fabric;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.tkisor.nekojs.NekoJS;
import com.tkisor.nekojs.api.ScriptType;
import com.tkisor.nekojs.api.catalog.NekoScriptCatalog;
import com.tkisor.nekojs.api.plugin.NekoRuntimeAccess;
import com.tkisor.nekojs.core.ScriptLocator;
import com.tkisor.nekojs.core.fs.NekoJSPaths;
import com.tkisor.nekojs.core.lifecycle.NekoRuntimeRoot;
import com.tkisor.nekojs.platform.Platform;
import com.tkisor.nekojs.probe.ProbeBackend;
import com.tkisor.nekojs.probe.ProbeBackendRegistry;
import com.tkisor.nekojs.probe.ProbeBackendSelector;
import com.tkisor.nekojs.probe.ProbeCoordinator;
import com.tkisor.nekojs.script.ScriptManager;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * fabric 侧的 {@code /nekojs} 指令树（挂载等价 NeoForge 的 {@code RegisterCommandsEvent}）。
 *
 * <p>共享树的 {@code NekoJSCommands} 还是整文件 neoforge 守卫（它的依赖面——错误面板网络包、
 * 工作区编辑器、Modification 重放、ClientReloadExecutor——大多未移植 fabric，见
 * {@code docs/fabric-port-status} 的缺口清单），在那些面落地前由本文件提供 fabric 当前能力
 * 子集。子命令与 NeoForge 版同名同语义；差异：
 * <ul>
 *   <li>{@code error} / {@code view_all_errors} 降级为文本输出（错误 UI 与网络面板未移植）</li>
 *   <li>{@code editor} 不注册（工作区编辑器 GUI 面未移植）</li>
 *   <li>SERVER reload 做配方热重载（RecipeManagerMixin 孪生），但不做 Modification/
 *       村民交易重放（对应机制未移植 fabric）</li>
 *   <li>CLIENT reload 同步执行（无 ClientReloadExecutor 的 Render 线程投递）</li>
 * </ul>
 * 依赖面齐后应与共享树版合并回单副本。
 */
public final class FabricNekoJSCommands {

    private FabricNekoJSCommands() {}

    public static void registerCallback() {
        // 回调在 server 启动时触发，届时 NekoJSFabricMod.RUNTIME_ROOT 已建好
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                register(dispatcher));
    }

    private static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
                Commands.literal("nekojs")
                        .requires(source -> Commands.LEVEL_GAMEMASTERS.check(source.permissions()))

                        .then(reloadCommand())
                        .then(testCommand())
                        .then(errorCommand())
                        .then(viewAllErrorsCommand())
                        .then(packsCommand())
                        .then(registryCommand())
                        .then(handCommand())
                        .then(inventoryCommand())
                        .then(trustCommand())
                        .then(probeCommand())
        );
    }

    private static NekoRuntimeRoot root() {
        return NekoJSFabricMod.RUNTIME_ROOT;
    }

    // ------------------------------------------------------------------
    //  reload / test
    // ------------------------------------------------------------------

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
        try {
            NekoRuntimeRoot root = root();
            if (type == ScriptType.TEST) {
                ScriptManager testSm = root.scriptManagerOrNull(ScriptType.TEST);
                if (testSm == null) {
                    testSm = root.createScriptManager(ScriptType.TEST);
                }
                testSm.runTestScripts();
            } else {
                root.reload(type);
                if (type == ScriptType.SERVER) {
                    applyRecipeScripts(source);
                }
            }
            sendReloadResult(source, "NekoJS " + type.name + " scripts reloaded.");
        } catch (Exception e) {
            NekoJS.LOGGER.error("Reloading {} scripts failed fatally", type.name, e);
            source.sendFailure(Component.literal("Reloading NekoJS " + type.name + " scripts failed fatally."));
        }
        return 1;
    }

    /**
     * SERVER 脚本 reload 后重新应用配方脚本（与 NeoForge 版 {@code NekoJSCommands#applyRecipeScripts}
     * 同语义）：RecipeManagerMixin 孪生的 {@code nekojs$applyScripts()} 从永久缓存重建工作集并
     * 重跑配方脚本；其内部会向全体 gamemaster 广播 ✔/⚠。
     */
    private static void applyRecipeScripts(CommandSourceStack source) {
        net.minecraft.server.MinecraftServer server = source.getServer();
        if (server == null) return;
        if (server.getRecipeManager() instanceof com.tkisor.nekojs.api.recipe.IRecipeManagerExtension ext) {
            ext.nekojs$applyScripts();
        }
    }

    private static int reloadFile(CommandSourceStack source, ScriptType type, String filePath) {
        if (!canReloadHere(source, type)) {
            return 0;
        }
        source.sendSystemMessage(Component.literal("Reloading NekoJS " + type.name + " script " + filePath + "..."));
        try {
            int affectedEntries = root().scriptManagerOf(type).reloadScriptFile(filePath).size();
            if (type == ScriptType.TEST) {
                ScriptManager testSm = root().scriptManagerOrNull(ScriptType.TEST);
                if (testSm != null) {
                    testSm.flushReadyNodeTimers();
                }
            }
            sendReloadResult(source, "NekoJS " + type.name + " script " + filePath + " reloaded ("
                    + affectedEntries + " affected entr" + (affectedEntries == 1 ? "y" : "ies") + ").");
        } catch (Exception e) {
            NekoJS.LOGGER.error("Reloading {} script file {} failed fatally", type.name, filePath, e);
            source.sendFailure(Component.literal("Reloading NekoJS " + type.name + " script file " + filePath + " failed: " + e.getMessage()));
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

    private static LiteralArgumentBuilder<CommandSourceStack> testCommand() {
        return Commands.literal("test")
                .executes(context -> {
                    CommandSourceStack source = context.getSource();
                    source.sendSystemMessage(Component.literal("Running NekoJS test scripts..."));
                    try {
                        NekoRuntimeRoot root = root();
                        ScriptManager testSm = root.scriptManagerOrNull(ScriptType.TEST);
                        if (testSm == null) {
                            testSm = root.createScriptManager(ScriptType.TEST);
                        }
                        testSm.runTestScripts();
                        sendReloadResult(source, "NekoJS test scripts completed.");
                    } catch (Exception e) {
                        NekoJS.LOGGER.error("Running test scripts failed fatally", e);
                        source.sendFailure(Component.literal("Running test scripts failed fatally."));
                    }
                    return 1;
                });
    }

    // ------------------------------------------------------------------
    //  error / view_all_errors（文本降级：错误 UI 与网络面板未移植）
    // ------------------------------------------------------------------

    private static LiteralArgumentBuilder<CommandSourceStack> errorCommand() {
        return Commands.literal("error")
                .executes(context -> {
                    CommandSourceStack source = context.getSource();
                    int count = root().errors().count();
                    if (count > 0) {
                        source.sendFailure(Component.literal(count + " script error(s); use /nekojs view_all_errors to list."));
                    } else {
                        source.sendSuccess(() -> Component.translatable("nekojs.command.error.healthy"), false);
                    }
                    return 1;
                });
    }

    private static LiteralArgumentBuilder<CommandSourceStack> viewAllErrorsCommand() {
        return Commands.literal("view_all_errors")
                .executes(context -> {
                    CommandSourceStack source = context.getSource();
                    var errors = root().errors().errors();
                    if (errors.isEmpty()) {
                        source.sendSuccess(() -> Component.translatable("nekojs.command.error.none"), false);
                        return 1;
                    }
                    for (var err : errors) {
                        source.sendSystemMessage(Component.literal("[" + err.getErrorId() + "] "
                                + err.getDisplayPath() + ":" + err.getLineNumber() + " - "
                                + err.getErrorMessage() + " (x" + err.getOccurrenceCount() + ")"));
                    }
                    return 1;
                });
    }

    private static void sendReloadResult(CommandSourceStack source, String successMessage) {
        int count = root().errors().count();
        if (count > 0) {
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
    //  trust / packs
    // ------------------------------------------------------------------

    private static LiteralArgumentBuilder<CommandSourceStack> trustCommand() {
        return Commands.literal("trust")
                .then(Commands.argument("address", StringArgumentType.string())
                        .executes(context -> trustServer(context.getSource(), StringArgumentType.getString(context, "address"))));
    }

    private static int trustServer(CommandSourceStack source, String address) {
        if (!Platform.isClient()) {
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

    // ------------------------------------------------------------------
    //  registry / hand / inventory
    // ------------------------------------------------------------------

    /** /nekojs registry：动态注册健康快照。fabric 上动态注册面未移植，快照恒为空。 */
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

    private static String compactComponents(ItemStack stack) {
        String text = stack.getComponentsPatch().toString().replace('\n', ' ');
        return text.length() > 300 ? text.substring(0, 300) + "..." : text;
    }

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

    // ------------------------------------------------------------------
    //  probe
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
            source.sendFailure(Component.literal("No probe backend selected."));
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

    private static int enableProbe(CommandSourceStack source) {
        ProbeCoordinator.setEnabled(true);
        source.sendSuccess(() -> Component.literal("Probe enabled."), false);
        return 1;
    }

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
