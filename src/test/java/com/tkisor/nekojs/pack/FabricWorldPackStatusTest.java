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
 * 票 19 AC6 的 Fabric WORLD 现状 fixture（纯文件读，全部节点同跑）：固定 Fabric 当前
 * 「WORLD 包从不激活」的行为与它的外部表现，作为 partial/unavailable 能力证据的事实输入——
 * 不伪造 parity，也不改本地包路径/默认启用（本测试只读）。
 *
 * <p>当前现象链（2026-09-22，mult@7768e02d）：
 *
 * <ul>
 *   <li>激活：WORLD 包唯一激活入口是 NeoForge 侧 {@code ServerEventListener#
 *       onServerAboutToStart}；fabric raw root（含 {@code FabricServerEventBindings} 的
 *       SERVER_STARTING/STOPPED 接线）不触碰 {@code ScriptPackRegistry} 的 world 批。</li>
 *   <li>列表：fabric {@code /nekojs packs} 的空结果文案声称查找 {@code <world>/nekojs_packs/}，
 *       但该目录在 fabric 从不被扫描（worldPacks 恒空）——已知文案与行为不一致，按票 19
 *       口径保留现状并记录，不在此修。</li>
 *   <li>分发：gather 只读 registry（GLOBAL → WORLD → SERVER_CACHE）；fabric 上 WORLD 批
 *       恒空，因此 fabric 服务器只能分发 GLOBAL 包。</li>
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

    /** fabric raw root 全部代码行（javadoc/注释剔除后）。 */
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
        // main 源里 activateWorldPacks 的调用点只允许出现在两个 NeoForge ServerEventListener
        //（共享树 + 1.21.1 孪生）——fabric 侧不存在第二条激活路线
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
        // 列表内容只来自 globalPacks + worldPacks；fabric 上后者恒空（上一用例钉住原因）
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
