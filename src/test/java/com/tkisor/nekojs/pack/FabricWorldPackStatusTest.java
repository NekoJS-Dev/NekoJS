package com.tkisor.nekojs.pack;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Source wiring checks; resource behavior and native lifecycle acceptance have separate coverage. */
class FabricWorldPackStatusTest {

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

    private static String codeLinesOnly(String source) {
        StringBuilder out = new StringBuilder(source.length());
        for (String line : source.split("\n", -1)) {
            String trimmed = line.trim();
            if (trimmed.startsWith("*") || trimmed.startsWith("/*") || trimmed.startsWith("//")) {
                continue;
            }
            out.append(line).append('\n');
        }
        return out.toString();
    }

    private static List<Path> javaFiles(Path root) {
        if (!Files.isDirectory(root)) return List.of();
        try (Stream<Path> walk = Files.walk(root)) {
            return walk.filter(p -> p.toString().endsWith(".java")).toList();
        } catch (IOException e) {
            throw new IllegalStateException("cannot walk " + root, e);
        }
    }

    @Test
    void fabricActivatesBeforeLoadingServerScriptsAndRevokesOnStop() {
        String lifecycle = codeLinesOnly(read(repoRoot().resolve(
                "src/fabric/java/com/tkisor/nekojs/fabric/event/FabricServerEventBindings.java")));
        int activation = lifecycle.indexOf("activateWorldPacks(");
        assertTrue(activation >= 0 && activation < lifecycle.indexOf("loadServerScripts.run()"));
        assertTrue(lifecycle.contains("deactivateWorldPacks()"));
        assertTrue(lifecycle.contains("root.clearWorldPackListeners("));
    }

    @Test
    void fabricServerTickRoutesTimersThroughTheRootIncludingIndependentPackages() {
        String lifecycle = codeLinesOnly(read(repoRoot().resolve(
                "src/fabric/java/com/tkisor/nekojs/fabric/event/FabricServerEventBindings.java")));
        int tickStart = lifecycle.indexOf("ServerTickEvents.END_SERVER_TICK.register(server -> {");
        int tickEnd = lifecycle.indexOf("\n        });", tickStart);
        assertTrue(tickStart >= 0 && tickEnd > tickStart, "server timer work belongs to the native end tick");
        String tick = lifecycle.substring(tickStart, tickEnd);
        int rootLookup = tick.indexOf("var root = rootSupplier.get();");
        int serverFlush = tick.indexOf("root.flushReadyNodeTimers(ScriptType.SERVER);");
        int guard = tick.indexOf("if (root != null && root.hasScriptType(ScriptType.SERVER)) {");
        assertTrue(rootLookup >= 0 && guard > rootLookup,
                "native ticks must skip a missing or closed runtime");
        assertTrue(serverFlush > guard && serverFlush < tick.indexOf("TICK_POST.post("),
                "the root must drain legacy and package timers before script tick listeners");
        assertFalse(tick.contains("ScriptType.TEST"), "TEST timers keep their explicit test-run drain");
        assertFalse(tick.contains("scriptManagerOf("), "a single manager would omit independent package timers");
    }

    @Test
    void worldDiscoveryIsLimitedToLifecycleAndRepositoryPreview() {
        List<Path> roots = List.of(
                repoRoot().resolve("src/main/java"),
                repoRoot().resolve("src/fabric/java"),
                repoRoot().resolve("versions/1.21.1/src/main/java"));
        List<String> callers = new ArrayList<>();
        for (Path root : roots) {
            for (Path file : javaFiles(root)) {
                if (codeLinesOnly(read(file)).contains("activateWorldPacks(")) {
                    callers.add(root.relativize(file).toString().replace('\\', '/'));
                }
            }
        }
        assertEquals(List.of(
                        "com/tkisor/nekojs/fabric/event/FabricServerEventBindings.java",
                        "com/tkisor/nekojs/fabric/resource/FabricDirectoryPackSource.java",
                        "com/tkisor/nekojs/listener/ServerEventListener.java",
                        "com/tkisor/nekojs/listener/ServerEventListener.java"),
                callers.stream().sorted().toList(),
                "preview discovery must not introduce another script activation route");
    }

    @Test
    void fabricPackListDescribesMountedSourcesWithoutClaimingWorldActivation() {
        String fabricCommands = codeLinesOnly(
                read(repoRoot().resolve("src/fabric/java/com/tkisor/nekojs/fabric/FabricNekoJSCommands.java")));
        assertTrue(fabricCommands.contains("Component.translatable(\"nekojs.command.packs.empty\")"));
        assertTrue(fabricCommands.contains("registry.allPacks()"));
        assertFalse(fabricCommands.contains("looked in nekojs/packs/ and <world>/nekojs_packs/"));
    }

    @Test
    void sharedGatherReadsActivatedSourcesWithoutScanningSaves() {
        String gather = codeLinesOnly(
                read(repoRoot().resolve("common/src/main/java/com/tkisor/nekojs/core/pack/sync/PackSyncServer.java")));
        assertTrue(gather.contains("ScriptPackRegistry.get().enabledPacks()"),
                "the shared gather must read the pack registry (GLOBAL → WORLD → SERVER_CACHE order)");
        assertFalse(gather.contains("WORLD_PACKS_DIR"),
                "the gather must not scan the world directory itself; WORLD packs enter only via activation");
    }
}
