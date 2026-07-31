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
import com.tkisor.nekojs.probe.ProbeRegistry;
import com.tkisor.nekojs.script.ScriptManager;
import com.tkisor.nekojs.wrapper.DataGeneratorJS;
import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.WrongUsageException;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.text.TextComponentString;
import net.minecraftforge.fml.common.event.FMLServerStartingEvent;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
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
    @Nonnull
    public String getName() {
        return "nekojs";
    }

    @Override
    @Nonnull
    public String getUsage(@Nonnull ICommandSender sender) {
        return "/nekojs <reload|test|error|probe>";
    }

    @Override
    @Nonnull
    public List<String> getAliases() {
        return Collections.emptyList();
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 2;
    }

    @Override
    public void execute(@Nonnull MinecraftServer server, @Nonnull ICommandSender sender, @Nonnull String[] args) throws CommandException {
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
                handleProbe(server, sender);
                break;
            default:
                throw new WrongUsageException(getUsage(sender));
        }
    }

    private void handleReload(@Nonnull MinecraftServer server, @Nonnull ICommandSender sender, @Nonnull String[] args) throws CommandException {
        ScriptType type = ScriptType.SERVER;
        if (args.length >= 2) {
            try {
                type = ScriptType.valueOf(args[1].toUpperCase());
            } catch (IllegalArgumentException e) {
                throw new WrongUsageException("Unknown script type: " + args[1] + ". Use: startup, server, client, test");
            }
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

    private void handleTest(@Nonnull MinecraftServer server, @Nonnull ICommandSender sender) {
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

    private void handleError(@Nonnull MinecraftServer server, @Nonnull ICommandSender sender) {
        int count = NekoJSMod.RUNTIME_ROOT.errors().count();
        if (count > 0) {
            sender.sendMessage(new TextComponentString("There are " + count + " NekoJS script error(s)."));
        } else {
            sender.sendMessage(new TextComponentString("No NekoJS script errors."));
        }
    }

    private void handleProbe(@Nonnull MinecraftServer server, @Nonnull ICommandSender sender) {
        var generator = ProbeRegistry.getGenerator();
        if (generator == null) {
            sender.sendMessage(new TextComponentString("No probe generator registered."));
            return;
        }

        final String generatorName = generator.name();
        sender.sendMessage(new TextComponentString("Starting probe generation (" + generatorName + ")..."));
        NekoJS.LOGGER.info("Probe generation started ({}), this may take a while...", generatorName);

        // Run synchronously on the server command thread.
        // Background threads crash when ProbeOrchestrator uses its internal thread pool
        // to Class.forName client rendering classes whose <clinit> calls OpenGL.
        try {
            var snapshot = NekoScriptCatalog.snapshot(NekoRuntimeAccess.get());
            var outputDir = NekoJSPaths.get().probeDir();
            var result = generator.generate(snapshot, outputDir);

            String msg;
            if (result.success()) {
                msg = "Probe complete: " + result.filesGenerated() + " files in " + result.durationMs() + "ms";
            } else {
                msg = "Probe failed: " + result.message();
            }
            NekoJS.LOGGER.info("Probe ({}) {}", generatorName, msg);
            sender.sendMessage(new TextComponentString(msg));
        } catch (Throwable e) {
            NekoJS.LOGGER.error("Probe generation crashed", e);
            sender.sendMessage(new TextComponentString(
                    "Probe error: " + e.getClass().getSimpleName() + ": " + e.getMessage()));
        }
    }

    @Override
    @Nonnull
    public List<String> getTabCompletions(@Nonnull MinecraftServer server, @Nonnull ICommandSender sender, @Nonnull String[] args, @Nullable net.minecraft.util.math.BlockPos targetPos) {
        if (args.length == 1) {
            return getListOfStringsMatchingLastWord(args, "reload", "test", "error", "probe");
        }
        if (args.length == 2 && "reload".equals(args[0])) {
            return getListOfStringsMatchingLastWord(args,
                    Arrays.stream(ScriptType.values()).map(t -> t.name.toLowerCase()).toArray(String[]::new));
        }
        return Collections.emptyList();
    }

    @Override
    public boolean isUsernameIndex(@Nonnull String[] args, int index) {
        return false;
    }
}
