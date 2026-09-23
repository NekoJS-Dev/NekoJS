package com.tkisor.nekojs.command;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RuntimeCommandLifecycleSourceTraceTest {

    private static Path repoRoot() {
        Path dir = Path.of("").toAbsolutePath();
        while (dir != null) {
            if (Files.isRegularFile(dir.resolve("stonecutter.gradle.kts"))
                    && Files.isDirectory(dir.resolve("src"))) {
                return dir;
            }
            dir = dir.getParent();
        }
        throw new IllegalStateException("cannot locate repo root from " + Path.of("").toAbsolutePath());
    }

    private static String read(Path path) {
        try {
            return Files.readString(path, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("cannot read " + path, e);
        }
    }

    @Test
    void loaderCommandTreesKeepGamemasterPermissionAndUseRootLifecycleEntries() {
        Path root = repoRoot();
        List<Path> commandFiles = List.of(
                root.resolve("src/main/java/com/tkisor/nekojs/command/NekoJSCommands.java"),
                root.resolve("versions/1.21.1/src/main/java/com/tkisor/nekojs/command/NekoJSCommands.java"),
                root.resolve("src/fabric/java/com/tkisor/nekojs/fabric/FabricNekoJSCommands.java"));

        String modernNeoForge = read(commandFiles.get(0));
        String legacyNeoForge = read(commandFiles.get(1));
        String fabric = read(commandFiles.get(2));
        assertTrue(modernNeoForge.contains("Commands.LEVEL_GAMEMASTERS.check(source.permissions())"));
        assertTrue(legacyNeoForge.contains("source.hasPermission(2)"));
        assertTrue(fabric.contains("Commands.LEVEL_GAMEMASTERS.check(source.permissions())"));
        assertTrue(modernNeoForge.contains("new ShowErrorListPacket"));
        assertTrue(fabric.contains("script error(s); use /nekojs view_all_errors"));
        assertFalse(fabric.contains("ShowErrorListPacket"), "Fabric must keep its text-only error surface");

        for (Path path : commandFiles) {
            String source = read(path);
            assertFalse(source.contains("root.scriptManager"), path + " must not bypass NekoRuntimeRoot");
            assertFalse(source.contains("createScriptManager(ScriptType.TEST)"),
                    path + " must not create TEST managers from a command");
            assertTrue(source.contains("root.reload(type)"), path + " must use the root reload result");
            assertTrue(source.contains("root.reloadFile(type"), path + " must use root single-file reload");
            assertTrue(source.contains("root.runTests()"), path + " must use root TEST results");
            assertTrue(source.contains("RuntimeCommandResultFormatter"),
                    path + " must share lifecycle result formatting");
        }
    }

    @Test
    void fabricKeepsLazyRootAndClientThreadBoundaries() {
        Path root = repoRoot();
        String commands = read(root.resolve("src/fabric/java/com/tkisor/nekojs/fabric/FabricNekoJSCommands.java"));
        String entry = read(root.resolve("src/fabric/java/com/tkisor/nekojs/fabric/NekoJSFabricMod.java"));
        String clientExecutor = read(root.resolve("src/fabric/java/com/tkisor/nekojs/fabric/FabricClientReloadExecutor.java"));

        assertTrue(commands.contains("registerCallback(Supplier<NekoRuntimeRoot> rootProvider)"));
        assertTrue(commands.contains("register(dispatcher, rootProvider)"));
        int callbackStart = commands.indexOf("public static void registerCallback");
        int registerStart = commands.indexOf("private static void register(");
        assertFalse(commands.substring(callbackStart, registerStart).contains("rootProvider.get()"),
                "callback registration must preserve the lazy root provider");
        assertTrue(commands.contains("rootProvider.get()"), "leaf executors resolve the current root");
        assertFalse(commands.contains("NekoJSFabricMod.runtimeRootOrNull()"));
        assertTrue(entry.contains("FabricNekoJSCommands.registerCallback(NekoJSFabricMod::runtimeRootOrNull)"));
        assertFalse(commands.contains("net.minecraft.client.Minecraft"));
        assertTrue(commands.contains("FabricClientReloadExecutor.execute"));
        assertTrue(clientExecutor.contains("net.minecraft.client.Minecraft.getInstance().execute(task)"));

        String runtimeRoot = read(root.resolve("common/src/main/java/com/tkisor/nekojs/core/lifecycle/NekoRuntimeRoot.java"));
        assertTrue(runtimeRoot.contains("if (manager.isActiveFailed())"),
                "single-file reload must not recover an isolated active runtime");
        assertTrue(runtimeRoot.contains("ReloadResult.failure(type, manager.generationId(), ReloadPhase.PREPARATION"),
                "isolated single-file reload must return a stable lifecycle failure result");
        for (Path path : List.of(root.resolve("src/main/java/com/tkisor/nekojs/command/NekoJSCommands.java"),
                root.resolve("versions/1.21.1/src/main/java/com/tkisor/nekojs/command/NekoJSCommands.java"),
                root.resolve("src/fabric/java/com/tkisor/nekojs/fabric/FabricNekoJSCommands.java"))) {
            assertTrue(read(path).contains("source.getServer() == null"),
                    path + " must reject lifecycle commands without a server command source");
        }
    }

    @Test
    void startupCommandsAreRejectedAndPostCommitFailuresKeepCommitEvidence() {
        Path root = repoRoot();
        for (Path path : List.of(root.resolve("src/main/java/com/tkisor/nekojs/command/NekoJSCommands.java"),
                root.resolve("versions/1.21.1/src/main/java/com/tkisor/nekojs/command/NekoJSCommands.java"),
                root.resolve("src/fabric/java/com/tkisor/nekojs/fabric/FabricNekoJSCommands.java"))) {
            String source = read(path);
            assertTrue(source.contains("if (type == ScriptType.STARTUP)"),
                    path + " must refuse STARTUP reload outside its initialization owner");
            assertTrue(source.contains("restart the game/loader"),
                    path + " must tell the operator why STARTUP reload was rejected");
            int reloadCommit = source.indexOf("root.reload(type)");
            int postFailureCatch = source.indexOf("catch (Exception postReloadFailure)");
            assertTrue(reloadCommit >= 0 && postFailureCatch > reloadCommit,
                    path + " must catch post-commit exceptions after root reload returns");
            int postFailureReporter = source.indexOf("private static int reportPostReloadFailure");
            int committedResult = source.indexOf("reportReloadResult(source, root, result)", postFailureReporter);
            assertTrue(source.contains("reportPostReloadFailure(source, root"),
                    path + " must route post-commit exceptions to a separate result");
            assertTrue(postFailureReporter >= 0 && committedResult > postFailureReporter,
                    path + " must report the committed reload result before its follow-up failure");
            assertTrue(source.contains("STARTUP scripts cannot be reloaded from a runtime command"),
                    path + " must explicitly refuse command-thread STARTUP reload");
        }

        String formatter = read(root.resolve(
                "common/src/main/java/com/tkisor/nekojs/core/lifecycle/RuntimeCommandResultFormatter.java"));
        assertTrue(formatter.contains("public static String postReloadFailure"));
        assertTrue(formatter.contains("reload committed (generation="));
    }
}
