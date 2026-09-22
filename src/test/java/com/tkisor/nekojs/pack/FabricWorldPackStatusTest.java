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

/**
 * Fabric WORLD status fixture for ticket 19 AC6 (pure file reads, runs on every node): pins
 * Fabric's current "WORLD packs are never activated" behavior and its external surface, as
 * the factual input for partial/unavailable capability evidence — no fabricated parity, and
 * no change to local pack paths or defaults (this test is read-only).
 *
 * <p>Current behavior chain (2026-09-22, mult@7768e02d):
 *
 * <ul>
 *   <li>Activation: the only WORLD pack activation entry is the NeoForge-side {@code
 *       ServerEventListener#onServerAboutToStart}; the fabric raw root (including the
 *       SERVER_STARTING/STOPPED wiring in {@code FabricServerEventBindings}) never touches
 *       {@code ScriptPackRegistry}'s world batch.</li>
 *   <li>Listing: the fabric {@code /nekojs packs} empty-result message claims it looked in
 *       {@code <world>/nekojs_packs/}, but that directory is never scanned on fabric
 *       (worldPacks is always empty) — a known message/behavior mismatch, kept and recorded
 *       per ticket 19's scope, not fixed here.</li>
 *   <li>Distribution: the gather reads only the registry (GLOBAL → WORLD → SERVER_CACHE);
 *       on fabric the WORLD batch is always empty, so a fabric server can only distribute
 *       GLOBAL packs.</li>
 * </ul>
 */
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

    /** All code lines of the fabric raw root (javadoc/comments stripped). */
    private static String fabricCodeLines() {
        StringBuilder out = new StringBuilder();
        for (Path file : javaFiles(repoRoot().resolve("src/fabric/java"))) {
            out.append(codeLinesOnly(read(file)));
        }
        return out.toString();
    }

    @Test
    void fabricNeverActivatesOrDeactivatesWorldPacks() {
        String fabric = fabricCodeLines();
        assertFalse(fabric.contains("activateWorldPacks("),
                "fabric must not activate WORLD packs (current behavior: activation is unavailable)");
        assertFalse(fabric.contains("deactivateWorldPacks("),
                "fabric must not deactivate WORLD packs (the batch never exists there)");
        assertFalse(fabric.contains("WORLD_PACKS_DIR"),
                "fabric must not scan the world pack directory directly");
    }

    @Test
    void worldPackActivationExistsOnlyInTheNeoforgeServerLifecycle() {
        // activateWorldPacks call sites in main sources may exist only in the two NeoForge
        // ServerEventListeners (shared tree + 1.21.1 twin) — no second activation route on fabric
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
                        "com/tkisor/nekojs/listener/ServerEventListener.java",
                        "com/tkisor/nekojs/listener/ServerEventListener.java"),
                callers.stream().sorted().toList(),
                "WORLD pack activation must stay a NeoForge-only lifecycle route");
    }

    @Test
    void fabricPackListMessageClaimsALookupThatNeverRunsThere() {
        String fabricCommands = codeLinesOnly(
                read(repoRoot().resolve("src/fabric/java/com/tkisor/nekojs/fabric/FabricNekoJSCommands.java")));
        assertTrue(fabricCommands.contains(
                        "No script packs found (looked in nekojs/packs/ and <world>/nekojs_packs/)."),
                "the fabric empty-list message must stay pinned as documented (it claims a world lookup "
                        + "that never runs on fabric; recorded as a known gap, not fixed here)");
        // List content comes only from globalPacks + worldPacks; on fabric the latter is always empty (previous case pins the reason)
        assertTrue(fabricCommands.contains("registry.worldPacks()"));
    }

    @Test
    void sharedGatherReadsOnlyTheRegistrySoFabricDistributionIsGlobalOnly() {
        String gather = codeLinesOnly(
                read(repoRoot().resolve("common/src/main/java/com/tkisor/nekojs/core/pack/sync/PackSyncServer.java")));
        assertTrue(gather.contains("ScriptPackRegistry.get().enabledPacks()"),
                "the shared gather must read the pack registry (GLOBAL → WORLD → SERVER_CACHE order)");
        assertFalse(gather.contains("WORLD_PACKS_DIR"),
                "the gather must not scan the world directory itself; WORLD packs enter only via activation");
    }
}
