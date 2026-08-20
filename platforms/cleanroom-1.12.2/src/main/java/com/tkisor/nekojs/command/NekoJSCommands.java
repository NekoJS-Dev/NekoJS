package com.tkisor.nekojs.command;

import com.tkisor.nekojs.NekoJS;
import com.tkisor.nekojs.NekoJSMod;
import com.tkisor.nekojs.api.ScriptType;
import com.tkisor.nekojs.bindings.event.ServerEvents;
import com.tkisor.nekojs.core.plugin.PluginGenerationHooks;
import com.tkisor.nekojs.listener.RegistryEventListener;
import com.tkisor.nekojs.api.catalog.NekoScriptCatalog;
import com.tkisor.nekojs.api.plugin.NekoRuntimeAccess;
import com.tkisor.nekojs.core.fs.NekoJSPaths;
import com.tkisor.nekojs.core.lifecycle.NekoRuntimeRoot;
import com.tkisor.nekojs.platform.Platform;
import com.tkisor.nekojs.probe.ProbeBackend;
import com.tkisor.nekojs.probe.ProbeBackendRegistry;
import com.tkisor.nekojs.probe.ProbeBackendSelector;
import com.tkisor.nekojs.probe.ProbeCoordinator;
import com.tkisor.nekojs.probe.ProbeGenerator;
import com.tkisor.nekojs.script.ScriptManager;
import com.tkisor.nekojs.wrapper.DataGeneratorJS;
import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.WrongUsageException;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.text.TextComponentString;
import net.minecraftforge.fml.common.event.FMLServerStartingEvent;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class NekoJSCommands extends CommandBase {

    private static final NekoJSCommands INSTANCE = new NekoJSCommands();

    public static void registerCommands(FMLServerStartingEvent event) {
        event.registerServerCommand(INSTANCE);
    }

    @Override
    @NotNull
    public String getName() {
        return "nekojs";
    }

    @Override
    @NotNull
    public String getUsage(@NotNull ICommandSender sender) {
        return "/nekojs <reload|test|error|probe|packs|trust>";
    }

    @Override
    @NotNull
    public List<String> getAliases() {
        return Collections.emptyList();
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 2;
    }

    @Override
    public void execute(@NotNull MinecraftServer server, @NotNull ICommandSender sender, @NotNull String[] args) throws CommandException {
        if (args.length == 0) {
            throw new WrongUsageException(getUsage(sender));
        }

        String subCommand = args[0];

        switch (subCommand) {
            case "reload":
                handleReload(server, sender, args);
                break;
            case "test":
                handleTest(server, sender);
                break;
            case "error":
                handleError(server, sender);
                break;
            case "probe":
                handleProbe(server, sender, args);
                break;
            case "packs":
                handlePacks(sender, args);
                break;
            case "trust":
                handleTrust(sender, args);
                break;
            default:
                throw new WrongUsageException(getUsage(sender));
        }
    }

    /**
     * /nekojs trust &lt;address&gt;：把服务器脚本包来源加入本进程信任存储（多人脚本分发：
     * 首连未信任会被断开并提示本命令，信任后重连收包）。仅在客户端进程生效（单人即本进程）。
     */
    private void handleTrust(@NotNull ICommandSender sender, @NotNull String[] args) throws CommandException {
        if (args.length < 2) {
            throw new WrongUsageException("/nekojs trust <address>");
        }
        if (!com.tkisor.nekojs.platform.Platform.isClient()) {
            notifyCommandListener(sender, this, 0,
                    "Run /nekojs trust on a client process (e.g. in a singleplayer world).");
            return;
        }
        String address = args[1];
        var store = com.tkisor.nekojs.core.pack.sync.PackSyncTrustStore.get();
        store.trustServer(address);
        notifyCommandListener(sender, this, 0,
                "Trusted " + address + " (bucket " + com.tkisor.nekojs.core.pack.sync.PackSyncTrustStore.bucketFor(address)
                        + "). Reconnect to receive its script packs.");
    }

    /**
     * /nekojs packs：列出全部脚本包；/nekojs packs enable|disable <id> 写包状态文件
     * （优先级高于 manifest），提示 reload 生效。与 NeoForge 平台同名命令语义一致。
     */
    private void handlePacks(@NotNull ICommandSender sender, @NotNull String[] args) throws CommandException {
        var registry = com.tkisor.nekojs.core.pack.ScriptPackRegistry.get();
        registry.refreshGlobalPacks();
        java.util.List<com.tkisor.nekojs.core.pack.ScriptPack> all = new java.util.ArrayList<>();
        all.addAll(registry.globalPacks());
        all.addAll(registry.worldPacks());

        if (args.length >= 3 && (args[1].equals("enable") || args[1].equals("disable"))) {
            String id = args[2];
            var match = all.stream().filter(p -> p.id().equals(id)).findFirst();
            if (match.isEmpty()) {
                throw new WrongUsageException("No script pack with id '" + id + "'. Use /nekojs packs to list.");
            }
            boolean enable = args[1].equals("enable");
            com.tkisor.nekojs.core.pack.ScriptPackState.save(match.get().root(), enable);
            notifyCommandListener(sender, this, 0,
                    "Script pack " + match.get().scope() + ":" + id + " " + (enable ? "enabled" : "disabled")
                            + ". Run /nekojs reload (server|client) to apply.");
            return;
        }

        if (all.isEmpty()) {
            notifyCommandListener(sender, this, 0,
                    "No script packs found (looked in nekojs/packs/ and <world>/nekojs_packs/).");
            return;
        }
        notifyCommandListener(sender, this, 0, "Script packs (" + all.size() + "):");
        for (var pack : all) {
            notifyCommandListener(sender, this, 0, "  [" + (pack.enabled() ? "x" : " ") + "] "
                    + pack.scope() + ":" + pack.id() + " v" + pack.version()
                    + (pack.name().equals(pack.id()) ? "" : " (" + pack.name() + ")")
                    + " - " + pack.root());
        }
    }

    private void handleReload(@NotNull MinecraftServer server, @NotNull ICommandSender sender, @NotNull String[] args) throws CommandException {
        ScriptType type = ScriptType.SERVER;
        if (args.length >= 2) {
            try {
                type = ScriptType.valueOf(args[1].toUpperCase());
            } catch (IllegalArgumentException e) {
                throw new WrongUsageException("Unknown script type: " + args[1] + ". Use: startup, server, client, test");
            }
        }

        if (!canReloadHere(sender, type)) {
            return;
        }

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
                // 1.12.2 recipes are buildtime (registry frozen after LoadComplete), so a
                // plain script reload alone won't update them. Re-apply recipe scripts:
                // unfreeze → drop old nekojs recipes → re-post recipe event → refreeze.
                if (type == ScriptType.SERVER) {
                    RegistryEventListener.reloadRecipes();
                    generateData(server);
                }
            }
            sender.sendMessage(new TextComponentString("NekoJS " + type.name + " scripts reloaded."));
        } catch (Throwable e) {
            NekoJS.LOGGER.error("Reloading {} scripts failed", type.name, e);
            sender.sendMessage(new TextComponentString("Reloading NekoJS " + type.name + " scripts failed: " + e.getMessage()));
        }
    }

    /**
     * 与 26-shared/1.21.1 的 canReloadHere 等价的守卫：
     * 专用服务器（无客户端运行时）上禁止 reload client，避免 walk 进 root.reload(CLIENT)。
     * Platform.isClient() 由 ForgePlatform 实现（FMLCommonHandler.instance().getSide() == Side.CLIENT）。
     */
    private boolean canReloadHere(@NotNull ICommandSender sender, ScriptType type) {
        if (type == ScriptType.CLIENT && !Platform.isClient()) {
            sender.sendMessage(new TextComponentString("Client script reload is only available in an integrated client runtime."));
            return false;
        }
        return true;
    }

    /**
     * 数据生成：插件与脚本把 datapack JSON 写入 {@code <worldDir>/data}
     * （loot tables / advancements / functions），随后 {@code server.reload()}
     * 使内容生效（vanilla /reload 等价物，同步且玩家安全）。
     */
    private static void generateData(MinecraftServer server) {
        try {
            Path dataDir = server.getWorld(0).getSaveHandler().getWorldDirectory().toPath().resolve("data");
            DataGeneratorJS generator = new DataGeneratorJS(dataDir, "after_mods");
            PluginGenerationHooks.fireGenerateData(generator);
            ServerEvents.GENERATE_DATA.post(generator, "after_mods");
            server.reload();
        } catch (Exception e) {
            ScriptType.SERVER.logger().error("generateData event failed", e);
        }
    }

    private void handleTest(@NotNull MinecraftServer server, @NotNull ICommandSender sender) {
        sender.sendMessage(new TextComponentString("Running NekoJS test scripts..."));

        try {
            NekoRuntimeRoot root = NekoJSMod.RUNTIME_ROOT;
            ScriptManager testSm = root.scriptManagerOrNull(ScriptType.TEST);
            if (testSm == null) {
                testSm = root.createScriptManager(ScriptType.TEST);
            }
            testSm.runTestScripts();
            sender.sendMessage(new TextComponentString("NekoJS test scripts completed."));
        } catch (Throwable e) {
            NekoJS.LOGGER.error("Running test scripts failed", e);
            sender.sendMessage(new TextComponentString("Running NekoJS test scripts failed: " + e.getMessage()));
        }
    }

    private void handleError(@NotNull MinecraftServer server, @NotNull ICommandSender sender) {
        int count = NekoJSMod.RUNTIME_ROOT.errors().count();
        if (count > 0) {
            sender.sendMessage(new TextComponentString("There are " + count + " NekoJS script error(s)."));
        } else {
            sender.sendMessage(new TextComponentString("No NekoJS script errors."));
        }
    }

    private void handleProbe(@NotNull MinecraftServer server, @NotNull ICommandSender sender, @NotNull String[] args) {
        String langArg = args.length >= 2 ? args[1] : null;
        List<ProbeBackend> backends;

        if (langArg == null || langArg.isEmpty()) {
            backends = ProbeBackendSelector.defaultTypescript();
        } else switch (langArg) {
            case "all":
                backends = ProbeBackendSelector.all();
                break;
            case "list":
                listProbeBackends(sender);
                return;
            case "reload":
                reloadProbeConfig(sender);
                return;
            case "enable":
                enableProbe(sender);
                return;
            case "disable":
                disableProbe(sender);
                return;
            default:
                String name = args.length >= 3 ? args[2] : null;
                backends = (name == null || name.isEmpty())
                        ? ProbeBackendSelector.forLanguage(langArg)
                        : ProbeBackendSelector.named(langArg, name);
                break;
        }

        runProbe(sender, backends);
    }

    private void runProbe(@NotNull ICommandSender sender, List<ProbeBackend> backends) {
        if (backends.isEmpty()) {
            sender.sendMessage(new TextComponentString("No probe backend matched. Use /nekojs probe list."));
            return;
        }
        StringBuilder names = new StringBuilder();
        for (ProbeBackend b : backends) {
            if (names.length() > 0) names.append(", ");
            names.append(b.languageId()).append(':').append(b.name());
        }
        sender.sendMessage(new TextComponentString("Starting probe (" + names + ")..."));
        NekoJS.LOGGER.info("Probe generation started ({}), this may take a while...", names);

        // Run synchronously on the server command thread.
        // 后台线程跑 probe 时，backend 内部的线程池会对客户端渲染类 Class.forName，
        // 其 <clinit> 调用 OpenGL 会崩溃；故保持在命令线程上触发。
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
                    for (String w : r.warnings()) {
                        sender.sendMessage(new TextComponentString("  warning: " + w));
                    }
                } else {
                    allOk = false;
                    sender.sendMessage(new TextComponentString("  backend failed: " + r.message()));
                }
            }
            String msg = allOk
                    ? "Probe complete: " + totalFiles + " files in " + maxMs + "ms"
                    : "Probe completed with failures";
            NekoJS.LOGGER.info("Probe ({}) {}", names, msg);
            sender.sendMessage(new TextComponentString(msg));
        } catch (Throwable e) {
            NekoJS.LOGGER.error("Probe generation crashed", e);
            sender.sendMessage(new TextComponentString(
                    "Probe error: " + e.getClass().getSimpleName() + ": " + e.getMessage()));
        }
    }

    private void listProbeBackends(@NotNull ICommandSender sender) {
        var entries = ProbeBackendRegistry.get().registrars();
        if (entries.isEmpty()) {
            sender.sendMessage(new TextComponentString("No probe backends registered."));
        } else {
            sender.sendMessage(new TextComponentString("Registered probe backends:"));
            for (String e : entries) {
                sender.sendMessage(new TextComponentString("  - " + e));
            }
        }
    }

    private void reloadProbeConfig(@NotNull ICommandSender sender) {
        ProbeCoordinator.reloadConfig();
        sender.sendMessage(new TextComponentString("Probe config (probe.toml) reloaded."));
    }

    /** /nekojs probe enable：运行时启用 probe（开关持久化由 ProbeCoordinator.setEnabled 负责）。 */
    private void enableProbe(@NotNull ICommandSender sender) {
        ProbeCoordinator.setEnabled(true);
        sender.sendMessage(new TextComponentString("Probe enabled."));
    }

    /** /nekojs probe disable：运行时禁用 probe。 */
    private void disableProbe(@NotNull ICommandSender sender) {
        ProbeCoordinator.setEnabled(false);
        sender.sendMessage(new TextComponentString("Probe disabled."));
    }

    @Override
    @NotNull
    public List<String> getTabCompletions(@NotNull MinecraftServer server, @NotNull ICommandSender sender, @NotNull String[] args, @Nullable net.minecraft.util.math.BlockPos targetPos) {
        if (args.length == 1) {
            return getListOfStringsMatchingLastWord(args, "reload", "test", "error", "probe");
        }
        if (args.length == 2 && "reload".equals(args[0])) {
            return getListOfStringsMatchingLastWord(args,
                    Arrays.stream(ScriptType.values()).map(t -> t.name.toLowerCase()).toArray(String[]::new));
        }
        if (args.length == 2 && "probe".equals(args[0])) {
            List<String> sugg = new ArrayList<>();
            sugg.add("all");
            sugg.add("list");
            sugg.add("reload");
            sugg.add("enable");
            sugg.add("disable");
            sugg.addAll(ProbeBackendRegistry.get().languages());
            return getListOfStringsMatchingLastWord(args, sugg.toArray(new String[0]));
        }
        if (args.length == 3 && "probe".equals(args[0])) {
            List<String> names = new ArrayList<>();
            for (ProbeBackend b : ProbeBackendRegistry.get().backendsFor(args[1])) {
                names.add(b.name());
            }
            return getListOfStringsMatchingLastWord(args, names.toArray(new String[0]));
        }
        return Collections.emptyList();
    }

    @Override
    public boolean isUsernameIndex(@NotNull String[] args, int index) {
        return false;
    }
}
